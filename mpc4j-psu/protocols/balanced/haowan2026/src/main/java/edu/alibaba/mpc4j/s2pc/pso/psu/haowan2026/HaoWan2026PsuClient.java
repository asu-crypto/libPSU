package edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsOtdClient;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Hao–Wan 2026 balanced ePSU receiver (paper R): ssPMT-fast, ssOTd, union output.
 */
public class HaoWan2026PsuClient extends AbstractPsuClient {
    private final HaoWan26SsPmtFastClient ssPmtFastClient;
    private final HaoWan26SsOtdClient ssOtdClient;

    public HaoWan2026PsuClient(Rpc clientRpc, Party serverParty, HaoWan2026PsuConfig config) {
        super(HaoWan2026PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        ssPmtFastClient = new HaoWan26SsPmtFastClient(clientRpc, serverParty, config.getSsPmtFastConfig());
        addSubPto(ssPmtFastClient);
        ssOtdClient = new HaoWan26SsOtdClient(clientRpc, serverParty, config.getSsOtdConfig());
        addSubPto(ssOtdClient);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        ssPmtFastClient.setParallel(parallel);
        ssOtdClient.setParallel(parallel);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        ssPmtFastClient.init(maxClientElementSize, maxServerElementSize);
        ssOtdClient.init(maxServerElementSize);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        byte[][] clientElements = clientElementArrayList.stream()
            .map(ByteBuffer::array)
            .toArray(byte[][]::new);

        stopWatch.start();
        SquareZ2Vector membershipShare1 = ssPmtFastClient.execute(clientElements, serverElementSize);
        stopWatch.stop();
        long ssPmtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, ssPmtTime, "ssPMT-fast");

        stopWatch.start();
        ByteBuffer[] transferred = ssOtdClient.execute(membershipShare1, elementByteLength);
        Set<ByteBuffer> union = new HashSet<>(clientElementSet);
        int psica = 0;
        for (ByteBuffer element : transferred) {
            if (element != null) {
                union.add(element);
            } else {
                psica++;
            }
        }
        stopWatch.stop();
        long ssOtdTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, ssOtdTime, "ssOTd + union");

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }
}
