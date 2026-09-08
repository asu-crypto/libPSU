package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvs;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvsFactory;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvsFactory.Gf2eDokvsType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfReceiver;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26AltModExpand.ExpandProfile;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Hao–Wan 2026 ssPMT-fast server (paper S): F32 SOW receiver (query holder), OKVS decode, ssPEQT.
 * <p>
 * Checks membership of each server element {@code x_i ∈ X} in the client set {@code Y}.
 * SOW batch = {@code |X|}; OKVS keyed by {@code |Y|}. Does not learn full {@code F(x)} (only a share).
 * </p>
 */
public class HaoWan26SsPmtFastServer extends AbstractTwoPartyPto {
    private final F32SowOprfReceiver f32SowReceiver;
    private final PeqtParty peqtSender;
    private final Gf2eDokvsType gf2eDokvsType;
    private final ExpandProfile expandProfile;
    private final F32WprfPublicParamsType publicParamsType;
    private final boolean fullOutputLength;
    private int maxServerN;
    private int maxClientN;
    private int maxEllBits;

    public HaoWan26SsPmtFastServer(Rpc serverRpc, Party clientParty, HaoWan26SsPmtFastConfig config) {
        super(HaoWan26SsPmtFastPtoDesc.getInstance(), serverRpc, clientParty, config);
        f32SowReceiver = F32SowOprfFactory.createReceiver(serverRpc, clientParty, config.getF32SowOprfConfig());
        addSubPto(f32SowReceiver);
        peqtSender = PeqtFactory.createSender(serverRpc, clientParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        gf2eDokvsType = config.getGf2eDokvsType();
        expandProfile = config.getExpandProfile();
        publicParamsType = config.getPublicParamsType();
        fullOutputLength = config.isFullOutputLength();
    }

    public void init(int maxServerN, int maxClientN) throws MpcAbortException {
        MathPreconditions.checkPositive("maxServerN", maxServerN);
        MathPreconditions.checkPositive("maxClientN", maxClientN);
        this.maxServerN = maxServerN;
        this.maxClientN = maxClientN;
        maxEllBits = fullOutputLength
            ? F32Wprf.getOutputByteLength() * Byte.SIZE
            : HaoWan26Truncate.ellBits(maxServerN);
        logPhaseInfo(PtoState.INIT_BEGIN);
        f32SowReceiver.init(maxServerN);
        peqtSender.init(maxEllBits, maxServerN);
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
        f32SowReceiver.setParallel(parallel);
        peqtSender.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        f32SowReceiver.setTaskId(taskId);
        peqtSender.setTaskId(taskId);
    }

    /**
     * Runs ssPMT-fast on the server's (permuted) input set {@code X}.
     *
     * @param serverElements    server set elements in execution order ({@code |X|}).
     * @param clientElementSize client set size {@code |Y|}.
     * @return secret-shared membership bits {@code [b_i]_0} of length {@code |X|}.
     */
    public SquareZ2Vector execute(byte[][] serverElements, int clientElementSize) throws MpcAbortException {
        checkInitialized();
        int serverN = serverElements.length;
        MathPreconditions.checkPositiveInRangeClosed("serverN", serverN, maxServerN);
        MathPreconditions.checkPositiveInRangeClosed("clientElementSize", clientElementSize, maxClientN);
        int ellBits = fullOutputLength
            ? F32Wprf.getOutputByteLength() * Byte.SIZE
            : HaoWan26Truncate.ellBits(serverN);
        logPhaseInfo(PtoState.PTO_BEGIN);

        // Paper S = F32 SOW receiver: [t_i]_0 for expanded queries.
        byte[][] expandedX = IntStream.range(0, serverN)
            .mapToObj(i -> HaoWan26AltModExpand.expand(serverElements[i], expandProfile))
            .toArray(byte[][]::new);
        byte[][] serverTShares = f32SowReceiver.oprf(expandedX);

        List<byte[]> okvsPayload = receiveOtherPartyPayload(PtoStep.CLIENT_SEND_OKVS.ordinal());
        int okvsKeyNum = Gf2eDokvsFactory.getHashKeyNum(gf2eDokvsType);
        int okvsM = Gf2eDokvsFactory.getM(envType, gf2eDokvsType, clientElementSize);
        MpcAbortPreconditions.checkArgument(okvsPayload.size() == okvsKeyNum + okvsM);
        byte[][] okvsKeys = IntStream.range(0, okvsKeyNum).mapToObj(okvsPayload::get).toArray(byte[][]::new);
        byte[][] storage = IntStream.range(okvsKeyNum, okvsPayload.size()).mapToObj(okvsPayload::get).toArray(byte[][]::new);
        Gf2eDokvs<ByteBuffer> dokvs = Gf2eDokvsFactory.createInstance(
            envType, gf2eDokvsType, clientElementSize, ellBits, okvsKeys
        );
        dokvs.setParallelEncode(parallel);

        byte[][] peqtInputs = new byte[serverN][];
        for (int i = 0; i < serverN; i++) {
            byte[] decoded = dokvs.decode(storage, ByteBuffer.wrap(serverElements[i]));
            byte[] share0 = HaoWan26Truncate.truncateJavaMsbShare(
                serverTShares[i], ellBits, publicParamsType
            );
            peqtInputs[i] = BytesUtils.xor(share0, decoded);
        }

        SquareZ2Vector membershipShare = peqtSender.peqt(ellBits, peqtInputs);
        logPhaseInfo(PtoState.PTO_END);
        return membershipShare;
    }

    /** Balanced convenience wrapper ({@code |X| = |Y|}). */
    public SquareZ2Vector execute(byte[][] serverElements) throws MpcAbortException {
        return execute(serverElements, serverElements.length);
    }
}
