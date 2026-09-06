package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * C_KisSon05 polynomial PSI tests.
 */
public class Ks05PsiTest extends AbstractTwoPartyMemoryRpcPto {
    public Ks05PsiTest() {
        super("KS05_PSI");
    }

    @Test
    public void testPartialIntersection() throws InterruptedException {
        runFixedCase(bytes(2L, 4L, 6L), bytes(1L, 4L, 7L), intersection(4L));
    }

    @Test
    public void testEmptyIntersection() throws InterruptedException {
        runFixedCase(bytes(2L, 4L, 6L), bytes(1L, 3L, 5L), intersection());
    }

    @Test
    public void testFullOverlap() throws InterruptedException {
        runFixedCase(bytes(1L, 2L, 3L), bytes(1L, 2L, 3L), intersection(1L, 2L, 3L));
    }

    @Test
    public void test2p5PartialIntersection() throws InterruptedException {
        runFixedCase(range(0, 32), range(16, 48), range(16, 32));
    }

    @Test
    public void testRejectsDefaultWarmupSize() {
        Ks05PsiConfig config = new Ks05PsiConfig.Builder().build();
        PsiServer server = PsiFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsiClient client = PsiFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        try {
            Assert.assertThrows(MpcAbortException.class, () -> server.init(1 << 10, 32));
            Assert.assertThrows(MpcAbortException.class, () -> client.init(32, 1 << 10));
        } finally {
            server.destroy();
            client.destroy();
        }
    }

    private static Set<ByteBuffer> bytes(long... values) {
        Set<ByteBuffer> set = new HashSet<>();
        for (long v : values) {
            set.add(ByteBuffer.wrap(longBytes(v)));
        }
        return set;
    }

    private static Set<ByteBuffer> range(long startInclusive, long endExclusive) {
        Set<ByteBuffer> set = new HashSet<>();
        for (long v = startInclusive; v < endExclusive; v++) {
            set.add(ByteBuffer.wrap(longBytes(v)));
        }
        return set;
    }

    private static Set<ByteBuffer> intersection(long... values) {
        return bytes(values);
    }

    private static byte[] longBytes(long v) {
        byte[] raw = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            raw[i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
        return raw;
    }

    private void runFixedCase(Set<ByteBuffer> clientSet, Set<ByteBuffer> serverSet, Set<ByteBuffer> expectIntersection)
        throws InterruptedException {
        Ks05PsiConfig config = new Ks05PsiConfig.Builder().build();
        PsiServer server = PsiFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsiClient client = PsiFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        PsiServerThread serverThread = new PsiServerThread(server, serverSet, clientSet.size(), Long.BYTES);
        PsiClientThread clientThread = new PsiClientThread(client, clientSet, serverSet.size(), Long.BYTES);
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();

        Set<ByteBuffer> actual = clientThread.getClientOutput().getIntersection();
        Assert.assertEquals(expectIntersection.size(), actual.size());
        for (ByteBuffer e : expectIntersection) {
            Assert.assertTrue("missing " + Arrays.toString(e.array()), containsElement(actual, e));
        }
        Set<ByteBuffer> golden = new HashSet<>(clientSet);
        golden.retainAll(serverSet);
        Assert.assertEquals(golden, actual);
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
