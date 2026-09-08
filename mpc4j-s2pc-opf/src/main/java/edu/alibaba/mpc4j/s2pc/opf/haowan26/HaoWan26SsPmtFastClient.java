package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvs;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvsFactory;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvsFactory.Gf2eDokvsType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfSender;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26AltModExpand.ExpandProfile;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Hao–Wan 2026 ssPMT-fast client (paper R): F32 SOW sender (key holder), OKVS encode, ssPEQT.
 * <p>
 * Encodes {@code Y} into an OKVS of truncated PRF values and runs ssPEQT against server queries of size {@code |X|}.
 * SOW batch size equals {@code |X|} (server queries). Does not send T-shares.
 * </p>
 */
public class HaoWan26SsPmtFastClient extends AbstractTwoPartyPto {
    private final F32SowOprfSender f32SowSender;
    private final PeqtParty peqtReceiver;
    private final Gf2eDokvsType gf2eDokvsType;
    private final ExpandProfile expandProfile;
    private final F32WprfPublicParamsType publicParamsType;
    private final boolean fullOutputLength;
    private int maxServerN;
    private int maxClientN;
    private int maxEllBits;

    public HaoWan26SsPmtFastClient(Rpc clientRpc, Party serverParty, HaoWan26SsPmtFastConfig config) {
        super(HaoWan26SsPmtFastPtoDesc.getInstance(), clientRpc, serverParty, config);
        f32SowSender = F32SowOprfFactory.createSender(clientRpc, serverParty, config.getF32SowOprfConfig());
        addSubPto(f32SowSender);
        peqtReceiver = PeqtFactory.createReceiver(clientRpc, serverParty, config.getPeqtConfig());
        addSubPto(peqtReceiver);
        gf2eDokvsType = config.getGf2eDokvsType();
        expandProfile = config.getExpandProfile();
        publicParamsType = config.getPublicParamsType();
        fullOutputLength = config.isFullOutputLength();
    }

    public void init(int maxClientN, int maxServerN) throws MpcAbortException {
        MathPreconditions.checkPositive("maxClientN", maxClientN);
        MathPreconditions.checkPositive("maxServerN", maxServerN);
        this.maxClientN = maxClientN;
        this.maxServerN = maxServerN;
        maxEllBits = fullOutputLength
            ? F32Wprf.getOutputByteLength() * Byte.SIZE
            : HaoWan26Truncate.ellBits(maxServerN);
        logPhaseInfo(PtoState.INIT_BEGIN);
        f32SowSender.init(maxServerN);
        peqtReceiver.init(maxEllBits, maxServerN);
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
        f32SowSender.setParallel(parallel);
        peqtReceiver.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        f32SowSender.setTaskId(taskId);
        peqtReceiver.setTaskId(taskId);
    }

    /**
     * Runs ssPMT-fast on the client's input set {@code Y}.
     *
     * @param clientElements    client set elements ({@code |Y|}).
     * @param serverElementSize server set size {@code |X|} (SOW / PEQT length).
     * @return secret-shared membership bits {@code [b_i]_1} of length {@code |X|}.
     */
    public SquareZ2Vector execute(byte[][] clientElements, int serverElementSize) throws MpcAbortException {
        checkInitialized();
        int clientN = clientElements.length;
        MathPreconditions.checkPositiveInRangeClosed("clientN", clientN, maxClientN);
        MathPreconditions.checkPositiveInRangeClosed("serverElementSize", serverElementSize, maxServerN);
        int ellBits = fullOutputLength
            ? F32Wprf.getOutputByteLength() * Byte.SIZE
            : HaoWan26Truncate.ellBits(serverElementSize);
        logPhaseInfo(PtoState.PTO_BEGIN);

        // Paper R = F32 SOW sender: obtain [t_i]_1 for each server query (batch = |X|).
        byte[][] clientTShares = f32SowSender.oprf(serverElementSize);

        Map<ByteBuffer, byte[]> kvMap = new HashMap<>(clientN);
        for (int i = 0; i < clientN; i++) {
            byte[] fy = f32SowSender.prf(HaoWan26AltModExpand.expand(clientElements[i], expandProfile));
            kvMap.put(
                ByteBuffer.wrap(clientElements[i]),
                HaoWan26Truncate.truncateJavaMsbShare(fy, ellBits, publicParamsType)
            );
        }
        byte[][] okvsKeys = BlockUtils.randomBlocks(Gf2eDokvsFactory.getHashKeyNum(gf2eDokvsType), secureRandom);
        Gf2eDokvs<ByteBuffer> dokvs = Gf2eDokvsFactory.createInstance(
            envType, gf2eDokvsType, clientN, ellBits, okvsKeys
        );
        dokvs.setParallelEncode(parallel);
        byte[][] encoded = dokvs.encode(kvMap, true);
        List<byte[]> okvsPayload = new LinkedList<>();
        for (byte[] okvsKey : okvsKeys) {
            okvsPayload.add(okvsKey);
        }
        okvsPayload.addAll(Arrays.asList(encoded));
        sendOtherPartyPayload(PtoStep.CLIENT_SEND_OKVS.ordinal(), okvsPayload);

        byte[][] peqtInputs = IntStream.range(0, serverElementSize)
            .mapToObj(i -> HaoWan26Truncate.truncateJavaMsbShare(clientTShares[i], ellBits, publicParamsType))
            .toArray(byte[][]::new);
        SquareZ2Vector membershipShare = peqtReceiver.peqt(ellBits, peqtInputs);
        logPhaseInfo(PtoState.PTO_END);
        return membershipShare;
    }

    /** Balanced convenience wrapper ({@code |X| = |Y|}). */
    public SquareZ2Vector execute(byte[][] clientElements) throws MpcAbortException {
        return execute(clientElements, clientElements.length);
    }
}
