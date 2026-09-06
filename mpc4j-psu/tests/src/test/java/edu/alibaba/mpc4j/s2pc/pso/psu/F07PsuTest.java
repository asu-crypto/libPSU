package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Paper-fidelity tests for ACNS_Frikken07 two-party HBC PSU subset.
 */
public class F07PsuTest extends AbstractTwoPartyMemoryRpcPto {
    public F07PsuTest() {
        super("F07_PSU");
    }

    @Test
    public void testReportVectorClient234Server147() throws InterruptedException {
        runFixedCase(
            bytes(2L, 4L, 6L),
            bytes(1L, 4L, 7L),
            union(bytes(1L, 2L, 4L, 6L, 7L))
        );
    }

    @Test
    public void testEmptyIntersection() throws InterruptedException {
        runFixedCase(
            bytes(2L, 4L, 6L),
            bytes(1L, 3L, 5L),
            union(bytes(1L, 2L, 3L, 4L, 5L, 6L))
        );
    }

    @Test
    public void testFullOverlap() throws InterruptedException {
        runFixedCase(
            bytes(1L, 2L, 3L),
            bytes(1L, 2L, 3L),
            union(bytes(1L, 2L, 3L))
        );
    }

    private static Set<ByteBuffer> bytes(long... values) {
        Set<ByteBuffer> set = new HashSet<>();
        for (long v : values) {
            set.add(ByteBuffer.wrap(longBytes(v)));
        }
        return set;
    }

    private static Set<ByteBuffer> union(Set<ByteBuffer> expected) {
        return expected;
    }

    private static byte[] longBytes(long v) {
        byte[] raw = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            raw[i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
        return raw;
    }

    private void runFixedCase(Set<ByteBuffer> clientSet, Set<ByteBuffer> serverSet, Set<ByteBuffer> expectUnion)
        throws InterruptedException {
        F07PsuConfig config = new F07PsuConfig.Builder().build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        PsuServerThread serverThread = new PsuServerThread(
            server, serverSet, clientSet.size(), Long.BYTES
        );
        PsuClientThread clientThread = new PsuClientThread(
            client, clientSet, serverSet.size(), Long.BYTES
        );
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();

        PsuClientOutput out = clientThread.getClientOutput();
        Set<ByteBuffer> actual = out.getUnion();
        Assert.assertEquals(expectUnion.size(), actual.size());
        for (ByteBuffer e : expectUnion) {
            Assert.assertTrue("missing " + Arrays.toString(e.array()), containsElement(actual, e));
        }
        Set<ByteBuffer> intersection = new HashSet<>(clientSet);
        intersection.retainAll(serverSet);
        Assert.assertEquals(intersection.size(), out.getPsiCa());
        printAndResetRpc(0);
        server.destroy();
        client.destroy();
    }

    private static boolean containsElement(Set<ByteBuffer> set, ByteBuffer needle) {
        for (ByteBuffer e : set) {
            if (Arrays.equals(e.array(), needle.array())) {
                return true;
            }
        }
        return false;
    }
}
