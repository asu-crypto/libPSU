package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;
import org.junit.Assume;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Ours microbenchmark comparing exact vs fingerprint vs async/parallel modes.
 * Always runs n=32; larger sizes require {@code -DrunSmallEcLargeBench=true}.
 */
public class SmallEcElligatorPsuMicrobenchTest extends AbstractTwoPartyMemoryRpcPto {
    private static final String RUN_LARGE_BENCH = "runSmallEcLargeBench";
    private static final int ELEMENT_BYTE_LENGTH = SmallEcConstants.ITEM_BYTE_LENGTH;

    public SmallEcElligatorPsuMicrobenchTest() {
        super("SMALL_EC_ELLIGATOR_PSU_MICROBENCH");
    }

    @Test
    public void microbenchN32() throws Exception {
        runAllModes(32);
    }

    @Test
    public void microbenchN1024() throws Exception {
        runAllModes(1024);
    }

    @Test
    public void microbenchN2p18() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean(RUN_LARGE_BENCH));
        runAllModes(1 << 18);
    }

    @Test
    public void microbenchN2p20() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean(RUN_LARGE_BENCH));
        runAllModes(1 << 20);
    }

    private void runAllModes(int n) throws Exception {
        System.out.printf("%n=== Ours microbench n=%d ===%n", n);
        benchCase(1, n, exact(false, false));
        benchCase(2, n, exact(true, false));
        benchCase(3, n, exact(true, true));
        benchCase(4, n, fingerprint64(true, false));
        benchCase(5, n, fingerprintAuto(true, true));
    }

    private void benchCase(int caseId, int n, SmallEcElligatorPsuConfig config) throws Exception {
        int intersection = n / 2;
        Set<ByteBuffer>[] sets = structuredSets(n, intersection);
        Set<ByteBuffer> serverSet = sets[0];
        Set<ByteBuffer> clientSet = sets[1];

        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int tid = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(tid);
        client.setTaskId(tid);

        firstRpc.reset();
        secondRpc.reset();

        PsuServerThread st = new PsuServerThread(server, serverSet, n, ELEMENT_BYTE_LENGTH);
        PsuClientThread ct = new PsuClientThread(client, clientSet, n, ELEMENT_BYTE_LENGTH);
        long t0 = System.nanoTime();
        st.start();
        ct.start();
        st.join();
        ct.join();
        st.rethrowIfFailed();
        ct.rethrowIfFailed();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        long serverSend = firstRpc.getSendByteLength();
        long clientSend = secondRpc.getSendByteLength();
        PsuClientOutput out = ct.getClientOutput();
        int expectedUnion = n + n - intersection;

        System.out.printf(
            "case %d mode=%s asyncW=%s parallelEc=%s | time=%dms | serverSend=%dB clientSend=%dB "
                + "| union=%d (expect %d) | psiCa=%d (expect %d)%n",
            caseId,
            config.getWCompareMode(),
            config.isAsyncPrecomputeW(),
            config.isParallelEc(),
            elapsedMs,
            serverSend,
            clientSend,
            out.getUnion().size(),
            expectedUnion,
            out.getPsiCa(),
            intersection
        );

        server.destroy();
        client.destroy();
    }

    private static SmallEcElligatorPsuConfig exact(boolean asyncW, boolean parallelEc) {
        return new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT)
            .setAsyncPrecomputeW(asyncW)
            .setParallelEc(parallelEc)
            .build();
    }

    private static SmallEcElligatorPsuConfig fingerprint64(boolean asyncW, boolean parallelEc) {
        return new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
            .setFingerprintBitLength(64)
            .setAsyncPrecomputeW(asyncW)
            .setParallelEc(parallelEc)
            .build();
    }

    private static SmallEcElligatorPsuConfig fingerprintAuto(boolean asyncW, boolean parallelEc) {
        return new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
            .setFingerprintBitLength(0)
            .setStatisticalSecurityBits(40)
            .setAsyncPrecomputeW(asyncW)
            .setParallelEc(parallelEc)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static Set<ByteBuffer>[] structuredSets(int n, int intersection) {
        Set<ByteBuffer> serverSet = new HashSet<>(n);
        Set<ByteBuffer> clientSet = new HashSet<>(n);
        for (int i = 0; i < intersection; i++) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, i);
            byte[] v = bb.array();
            serverSet.add(ByteBuffer.wrap(v.clone()));
            clientSet.add(ByteBuffer.wrap(v.clone()));
        }
        int s = intersection;
        while (serverSet.size() < n) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES * 2, 1);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, s++);
            serverSet.add(bb);
        }
        int c = intersection;
        while (clientSet.size() < n) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES * 2, 2);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, c++);
            clientSet.add(bb);
        }
        return new Set[] {serverSet, clientSet};
    }
}
