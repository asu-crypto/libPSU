package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvs;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvsFactory;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvsFactory.Gf2kDokvsType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.galoisfield.gf2k.Gf2k;
import edu.alibaba.mpc4j.common.tool.galoisfield.gf2k.Gf2kFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Hao–Wan 2026 ssPMT-fast client (paper R): MP-OPRF receiver, OKVS encode, ssPEQT.
 * <p>
 * Encodes {@code Y} into an OKVS and runs ssPEQT against server queries of size {@code |X|}.
 * Supports unequal {@code |X|} and {@code |Y|}.
 * </p>
 */
public class HaoWan26SsPmtFastClient extends AbstractTwoPartyPto {
    private final MpOprfReceiver rs21MpOprfReceiver;
    private final PeqtParty peqtReceiver;
    private final Gf2kDokvsType gf2kDokvsType;
    private final int peqtBitLength;
    private int maxServerN;
    private int maxClientN;
    private Gf2k gf2k;

    public HaoWan26SsPmtFastClient(Rpc clientRpc, Party serverParty, HaoWan26SsPmtFastConfig config) {
        super(HaoWan26SsPmtFastPtoDesc.getInstance(), clientRpc, serverParty, config);
        Rs21MpOprfConfig rs21 = config.getRs21MpOprfConfig();
        rs21MpOprfReceiver = (MpOprfReceiver) OprfFactory.createOprfReceiver(clientRpc, serverParty, rs21);
        addSubPto(rs21MpOprfReceiver);
        peqtReceiver = PeqtFactory.createReceiver(clientRpc, serverParty, config.getPeqtConfig());
        addSubPto(peqtReceiver);
        gf2kDokvsType = rs21.getOkvsType();
        gf2k = Gf2kFactory.createInstance(envType);
        peqtBitLength = gf2k.getL();
    }

    public void init(int maxClientN, int maxServerN) throws MpcAbortException {
        MathPreconditions.checkPositive("maxClientN", maxClientN);
        MathPreconditions.checkPositive("maxServerN", maxServerN);
        this.maxClientN = maxClientN;
        this.maxServerN = maxServerN;
        logPhaseInfo(PtoState.INIT_BEGIN);
        rs21MpOprfReceiver.init(maxClientN, maxClientN);
        peqtReceiver.init(peqtBitLength, maxServerN);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    /** Balanced convenience wrapper. */
    public void init(int maxN) throws MpcAbortException {
        init(maxN, maxN);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        rs21MpOprfReceiver.setParallel(parallel);
        peqtReceiver.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        rs21MpOprfReceiver.setTaskId(taskId);
        peqtReceiver.setTaskId(taskId);
    }

    /**
     * Runs ssPMT-fast on the client's input set {@code Y}.
     *
     * @param clientElements client set elements ({@code |Y|}).
     * @param serverElementSize server set size {@code |X|} (PEQT / T-share length).
     * @return secret-shared membership bits {@code [b_i]_1} of length {@code |X|}.
     */
    public SquareZ2Vector execute(byte[][] clientElements, int serverElementSize) throws MpcAbortException {
        checkInitialized();
        int clientN = clientElements.length;
        MathPreconditions.checkPositiveInRangeClosed("clientN", clientN, maxClientN);
        MathPreconditions.checkPositiveInRangeClosed("serverElementSize", serverElementSize, maxServerN);
        logPhaseInfo(PtoState.PTO_BEGIN);

        // One random T-share per server query position.
        byte[][] clientTShares = IntStream.range(0, serverElementSize)
            .mapToObj(i -> gf2k.createRandom(secureRandom))
            .toArray(byte[][]::new);
        sendOtherPartyPayload(PtoStep.CLIENT_SEND_T_SHARES.ordinal(), Arrays.asList(clientTShares));

        MpOprfReceiverOutput rs21Out = rs21MpOprfReceiver.oprf(clientElements);

        Map<ByteBuffer, byte[]> kvMap = new HashMap<>(clientN);
        for (int i = 0; i < clientN; i++) {
            kvMap.put(ByteBuffer.wrap(clientElements[i]), rs21Out.getPrf(i));
        }
        byte[][] okvsKeys = BlockUtils.randomBlocks(Gf2kDokvsFactory.getHashKeyNum(gf2kDokvsType), secureRandom);
        Gf2kDokvs<ByteBuffer> dokvs = Gf2kDokvsFactory.createInstance(envType, gf2kDokvsType, clientN, okvsKeys);
        dokvs.setParallelEncode(parallel);
        byte[][] encoded = dokvs.encode(kvMap, true);
        List<byte[]> okvsPayload = new LinkedList<>();
        for (byte[] okvsKey : okvsKeys) {
            okvsPayload.add(okvsKey);
        }
        okvsPayload.addAll(Arrays.asList(encoded));
        sendOtherPartyPayload(PtoStep.CLIENT_SEND_OKVS.ordinal(), okvsPayload);

        SquareZ2Vector membershipShare = peqtReceiver.peqt(peqtBitLength, clientTShares);
        logPhaseInfo(PtoState.PTO_END);
        return membershipShare;
    }

    /** Balanced convenience wrapper ({@code |X| = |Y|}). */
    public SquareZ2Vector execute(byte[][] clientElements) throws MpcAbortException {
        return execute(clientElements, clientElements.length);
    }
}
