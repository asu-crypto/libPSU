package edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.network.PermutationNetworkUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsOtdServer;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Hao–Wan 2026 balanced ePSU sender (paper S): permute X, ssPMT-fast, ssOTd.
 */
public class HaoWan2026PsuServer extends AbstractPsuServer {
    private final HaoWan26SsPmtFastServer ssPmtFastServer;
    private final HaoWan26SsOtdServer ssOtdServer;

    public HaoWan2026PsuServer(Rpc serverRpc, Party clientParty, HaoWan2026PsuConfig config) {
        super(HaoWan2026PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        ssPmtFastServer = new HaoWan26SsPmtFastServer(serverRpc, clientParty, config.getSsPmtFastConfig());
        addSubPto(ssPmtFastServer);
        ssOtdServer = new HaoWan26SsOtdServer(serverRpc, clientParty, config.getSsOtdConfig());
        addSubPto(ssOtdServer);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        ssPmtFastServer.setParallel(parallel);
        ssOtdServer.setParallel(parallel);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        ssPmtFastServer.init(maxServerElementSize, maxClientElementSize);
        ssOtdServer.init(maxServerElementSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        int[] permutation = PermutationNetworkUtils.randomPermutation(serverElementSize, secureRandom);
        byte[][] permutedElements = PermutationNetworkUtils.permutation(permutation, serverElementArrayList.stream()
            .map(ByteBuffer::array)
            .toArray(byte[][]::new));
        stopWatch.stop();
        long permuteTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 3, permuteTime, "permute server set");

        stopWatch.start();
        SquareZ2Vector membershipShare0 = ssPmtFastServer.execute(permutedElements, clientElementSize);
        stopWatch.stop();
        long ssPmtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 3, ssPmtTime, "ssPMT-fast");

        stopWatch.start();
        ssOtdServer.execute(permutedElements, membershipShare0, elementByteLength);
        stopWatch.stop();
        long ssOtdTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 3, ssOtdTime, "ssOTd");

        logPhaseInfo(PtoState.PTO_END);
    }
}
