package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Paper-fidelity tests for ACISP_DavCid17 EIBF PSU.
 */
public class Dc17PsuTest extends AbstractTwoPartyMemoryRpcPto {
    public Dc17PsuTest() {
        super("DC17_PSU");
    }

    @Test
    public void testUnevenPartialIntersectionPsiCa() throws InterruptedException {
        runFixedCase(
            bytes(2L, 4L, 6L),
            bytes(1L, 4L, 7L, 8L, 9L),
            union(bytes(1L, 2L, 4L, 6L, 7L, 8L, 9L))
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
        Dc17PsuConfig config = new Dc17PsuConfig.Builder().build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        PsuServerThread serverThread = new PsuServerThread(server, serverSet, clientSet.size(), Long.BYTES);
        PsuClientThread clientThread = new PsuClientThread(client, clientSet, serverSet.size(), Long.BYTES);
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
