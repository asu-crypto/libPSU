package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Ours optimization modes (exact default + opt-in fingerprint).
 */
public class SmallEcElligatorPsuOptimizationTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int N = 1 << 5;

    public SmallEcElligatorPsuOptimizationTest() {
        super("SMALL_EC_ELLIGATOR_PSU_OPT");
    }

    @Test
    public void exactModeDefaultPartialOverlap() throws Exception {
        runProtocol(defaultConfig(), N, N, N / 2);
    }

    @Test
    public void exactModeAsyncDisabled() throws Exception {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder()
            .setAsyncPrecomputeW(false)
            .build();
        runProtocol(config, N, N, N / 2);
    }

    @Test
    public void fingerprintModePartialOverlap() throws Exception {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
            .setFingerprintBitLength(64)
            .build();
        runProtocol(config, N, N, N / 2);
    }

    @Test
    public void fingerprintModeAutoLambda() throws Exception {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
            .setFingerprintBitLength(0)
            .setStatisticalSecurityBits(40)
            .build();
        Assert.assertEquals(64, config.getResolvedFingerprintBitLength(N));
        runProtocol(config, N, N, N / 2);
    }

    @Test
    public void fingerprintModeFullIntersection() throws Exception {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
            .setFingerprintBitLength(64)
            .build();
        runProtocol(config, N, N, N);
    }

    private static SmallEcElligatorPsuConfig defaultConfig() {
        return new SmallEcElligatorPsuConfig.Builder().build();
    }

    private void runProtocol(SmallEcElligatorPsuConfig config, int serverSize, int clientSize, int intersectionSize)
        throws Exception {
        int elementByteLength = SmallEcConstants.ITEM_BYTE_LENGTH;
        ArrayList<Set<ByteBuffer>> sets = generateBytesSets(serverSize, clientSize, intersectionSize, elementByteLength);

        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int tid = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(tid);
        client.setTaskId(tid);

        PsuServerThread st = new PsuServerThread(server, sets.get(0), clientSize, elementByteLength);
        PsuClientThread ct = new PsuClientThread(client, sets.get(1), serverSize, elementByteLength);
        st.start();
        ct.start();
        st.join();
        ct.join();

        int n = sets.get(0).size();
        int expectedUnionSize = n + clientSize - intersectionSize;
        Set<ByteBuffer> expectUnion = new HashSet<>(sets.get(0));
        expectUnion.addAll(sets.get(1));

        PsuClientOutput out = ct.getClientOutput();
        Assert.assertEquals(intersectionSize, out.getPsiCa());
        Assert.assertEquals(expectedUnionSize, out.getUnion().size());
        assertUnionEqual(expectUnion, out.getUnion());

        server.destroy();
        client.destroy();
    }

    private static void assertUnionEqual(Set<ByteBuffer> expected, Set<ByteBuffer> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        for (ByteBuffer e : expected) {
            Assert.assertTrue(unionContains(actual, e));
        }
    }

    private static boolean unionContains(Set<ByteBuffer> union, ByteBuffer needle) {
        byte[] target = byteArray(needle);
        for (ByteBuffer b : union) {
            if (java.util.Arrays.equals(target, byteArray(b))) {
                return true;
            }
        }
        return false;
    }

    private static byte[] byteArray(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] item = new byte[dup.remaining()];
        dup.get(item);
        return item;
    }

    private static ArrayList<Set<ByteBuffer>> generateBytesSets(
        int serverSize, int clientSize, int intersectionSize, int elementByteLength
    ) {
        Assert.assertTrue(intersectionSize <= Math.min(serverSize, clientSize));
        Set<ByteBuffer> serverSet = new HashSet<>(serverSize);
        Set<ByteBuffer> clientSet = new HashSet<>(clientSize);
        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES, i);
            byte[] v = bb.array();
            serverSet.add(ByteBuffer.wrap(v.clone()));
            clientSet.add(ByteBuffer.wrap(v.clone()));
        }
        int s = intersectionSize;
        while (serverSet.size() < serverSize) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES * 2, 1);
            bb.putInt(elementByteLength - Integer.BYTES, s++);
            serverSet.add(bb);
        }
        int c = intersectionSize;
        while (clientSet.size() < clientSize) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES * 2, 2);
            bb.putInt(elementByteLength - Integer.BYTES, c++);
            clientSet.add(bb);
        }
        ArrayList<Set<ByteBuffer>> out = new ArrayList<>(2);
        out.add(serverSet);
        out.add(clientSet);
        return out;
    }
}
