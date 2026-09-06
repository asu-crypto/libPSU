package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
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
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfSenderOutput;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Hao–Wan 2026 ssPMT-fast server (paper S): MP-OPRF sender, OKVS decode, ssPEQT.
 * <p>
 * Checks membership of each server element {@code x_i ∈ X} in the client set {@code Y}.
 * Supports unequal {@code |X|} and {@code |Y|}: OKVS / RS21 batch = {@code |Y|}, PEQT = {@code |X|}.
 * </p>
 */
public class HaoWan26SsPmtFastServer extends AbstractTwoPartyPto {
    private final Rs21MpOprfSender rs21MpOprfSender;
    private final PeqtParty peqtSender;
    private final Gf2kDokvsType gf2kDokvsType;
    private final int peqtBitLength;
    private int maxServerN;
    private int maxClientN;
    private Gf2k gf2k;

    public HaoWan26SsPmtFastServer(Rpc serverRpc, Party clientParty, HaoWan26SsPmtFastConfig config) {
        super(HaoWan26SsPmtFastPtoDesc.getInstance(), serverRpc, clientParty, config);
        Rs21MpOprfConfig rs21 = config.getRs21MpOprfConfig();
        rs21MpOprfSender = (Rs21MpOprfSender) OprfFactory.createOprfSender(serverRpc, clientParty, rs21);
        addSubPto(rs21MpOprfSender);
        peqtSender = PeqtFactory.createSender(serverRpc, clientParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        gf2kDokvsType = rs21.getOkvsType();
        gf2k = Gf2kFactory.createInstance(envType);
        peqtBitLength = gf2k.getL();
    }

    public void init(int maxServerN, int maxClientN) throws MpcAbortException {
        MathPreconditions.checkPositive("maxServerN", maxServerN);
        MathPreconditions.checkPositive("maxClientN", maxClientN);
        this.maxServerN = maxServerN;
        this.maxClientN = maxClientN;
        logPhaseInfo(PtoState.INIT_BEGIN);
        // RS21 batch is sized by the OPRF-receiver set Y.
        rs21MpOprfSender.init(maxClientN, maxClientN);
        peqtSender.init(peqtBitLength, maxServerN);
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
        rs21MpOprfSender.setParallel(parallel);
        peqtSender.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        rs21MpOprfSender.setTaskId(taskId);
        peqtSender.setTaskId(taskId);
    }

    /**
     * Runs ssPMT-fast on the server's (permuted) input set {@code X}.
     *
     * @param serverElements server set elements in execution order ({@code |X|}).
     * @param clientElementSize client set size {@code |Y|}.
     * @return secret-shared membership bits {@code [b_i]_0} of length {@code |X|}.
     */
    public SquareZ2Vector execute(byte[][] serverElements, int clientElementSize) throws MpcAbortException {
        checkInitialized();
        int serverN = serverElements.length;
        MathPreconditions.checkPositiveInRangeClosed("serverN", serverN, maxServerN);
        MathPreconditions.checkPositiveInRangeClosed("clientElementSize", clientElementSize, maxClientN);
        logPhaseInfo(PtoState.PTO_BEGIN);

        // T-shares are aligned with server queries (|X|), not |Y|.
        List<byte[]> tSharePayload = receiveOtherPartyPayload(PtoStep.CLIENT_SEND_T_SHARES.ordinal());
        MpcAbortPreconditions.checkArgument(tSharePayload.size() == serverN);
        byte[][] clientTShares = tSharePayload.toArray(new byte[0][]);

        // RS21 interactive batch must match the client's OPRF input size |Y|.
        Rs21MpOprfSenderOutput rs21Out = (Rs21MpOprfSenderOutput) rs21MpOprfSender.oprf(clientElementSize);

        byte[][] serverTShares = IntStream.range(0, serverN)
            .mapToObj(i -> {
                byte[] fx = rs21Out.getPrf(serverElements[i]);
                return gf2k.add(fx, clientTShares[i]);
            })
            .toArray(byte[][]::new);

        List<byte[]> okvsPayload = receiveOtherPartyPayload(PtoStep.CLIENT_SEND_OKVS.ordinal());
        int okvsKeyNum = Gf2kDokvsFactory.getHashKeyNum(gf2kDokvsType);
        int okvsM = Gf2kDokvsFactory.getM(envType, gf2kDokvsType, clientElementSize);
        MpcAbortPreconditions.checkArgument(okvsPayload.size() == okvsKeyNum + okvsM);
        byte[][] okvsKeys = IntStream.range(0, okvsKeyNum).mapToObj(okvsPayload::get).toArray(byte[][]::new);
        byte[][] storage = IntStream.range(okvsKeyNum, okvsPayload.size()).mapToObj(okvsPayload::get).toArray(byte[][]::new);
        Gf2kDokvs<ByteBuffer> dokvs = Gf2kDokvsFactory.createInstance(
            envType, gf2kDokvsType, clientElementSize, okvsKeys
        );
        dokvs.setParallelEncode(parallel);
        for (int i = 0; i < serverN; i++) {
            byte[] decoded = dokvs.decode(storage, ByteBuffer.wrap(serverElements[i]));
            gf2k.addi(serverTShares[i], decoded);
        }

        SquareZ2Vector membershipShare = peqtSender.peqt(peqtBitLength, serverTShares);
        logPhaseInfo(PtoState.PTO_END);
        return membershipShare;
    }

    /** Balanced convenience wrapper ({@code |X| = |Y|}). */
    public SquareZ2Vector execute(byte[][] serverElements) throws MpcAbortException {
        return execute(serverElements, serverElements.length);
    }
}
