package edu.alibaba.mpc4j.psu.protocol.tbz25;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuServer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * USENIX_BinYujConYanYu25 balanced ePSU correctness at small set size (2^5 smoke).
 */
public class Tbz25Psu2p5Test extends AbstractTwoPartyMemoryRpcPto {
    private static final int SIZE = 32;
    private static final int ELEMENT_BYTE_LENGTH = 16;

    public Tbz25Psu2p5Test() {
        super("TBZ25_PSU");
    }

    @Test(timeout = 120_000)
    public void testBalancedUnion() throws InterruptedException {
        Tbz25PsuConfig config = new Tbz25PsuConfig.Builder(false).build();
        Tbz25PsuServer server = new Tbz25PsuServer(firstRpc, secondRpc.ownParty(), config);
        Tbz25PsuClient client = new Tbz25PsuClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(SIZE, SIZE, ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = sets.get(1);

        AtomicReference<PsuClientOutput> clientOut = new AtomicReference<>();
        AtomicReference<Throwable> serverErr = new AtomicReference<>();
        AtomicReference<Throwable> clientErr = new AtomicReference<>();

        Thread serverThread = new Thread(() -> {
            try {
                server.init(serverSet.size(), clientSet.size());
                server.psu(serverSet, clientSet.size(), ELEMENT_BYTE_LENGTH);
            } catch (Throwable t) {
                serverErr.set(t);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                client.init(clientSet.size(), serverSet.size());
                clientOut.set(client.psu(clientSet, serverSet.size(), ELEMENT_BYTE_LENGTH));
            } catch (Throwable t) {
                clientErr.set(t);
            }
        });
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();

        if (serverErr.get() != null) {
            throw new AssertionError("server failed", serverErr.get());
        }
        if (clientErr.get() != null) {
            throw new AssertionError("client failed", clientErr.get());
        }

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Set<ByteBuffer> actualUnion = clientOut.get().getUnion();
        Assert.assertEquals(expectUnion.size(), actualUnion.size());
        Assert.assertTrue(actualUnion.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actualUnion));
    }
}
