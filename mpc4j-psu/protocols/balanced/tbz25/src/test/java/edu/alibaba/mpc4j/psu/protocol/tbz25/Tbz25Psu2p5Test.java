package edu.alibaba.mpc4j.psu.protocol.tbz25;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
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
        TwoPartyTestJoin.joinFailFast(
            serverThread, serverErr::get, server::destroy,
            clientThread, clientErr::get, client::destroy,
            "TBZ25-PSU"
        );

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Set<ByteBuffer> actualUnion = clientOut.get().getUnion();
        Assert.assertEquals(expectUnion.size(), actualUnion.size());
        Assert.assertTrue(actualUnion.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actualUnion));
        int expectPsiCa = serverSet.size() + clientSet.size() - expectUnion.size();
        Assert.assertEquals(expectPsiCa, clientOut.get().getPsiCa());
    }

    @Test(timeout = 120_000)
    public void testBalancedUnequalPartialDisjointFull() throws InterruptedException {
        runExact(10, 8, 4);
        runExact(8, 8, 0);
        runExact(8, 8, 8);
    }

    private void runExact(int serverSize, int clientSize, int intersectionSize) throws InterruptedException {
        Tbz25PsuConfig config = new Tbz25PsuConfig.Builder(false).build();
        Tbz25PsuServer server = new Tbz25PsuServer(firstRpc, secondRpc.ownParty(), config);
        Tbz25PsuClient client = new Tbz25PsuClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        Set<ByteBuffer> serverSet = new HashSet<>();
        Set<ByteBuffer> clientSet = new HashSet<>();
        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer shared = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            shared.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, i);
            byte[] v = shared.array();
            serverSet.add(ByteBuffer.wrap(v.clone()));
            clientSet.add(ByteBuffer.wrap(v.clone()));
        }
        int s = intersectionSize;
        while (serverSet.size() < serverSize) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES * 2, 1);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, s++);
            serverSet.add(bb);
        }
        int c = intersectionSize;
        while (clientSet.size() < clientSize) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES * 2, 2);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, c++);
            clientSet.add(bb);
        }

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
        TwoPartyTestJoin.joinFailFast(
            serverThread, serverErr::get, server::destroy,
            clientThread, clientErr::get, client::destroy,
            "TBZ25-PSU-exact"
        );
        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Assert.assertEquals(expectUnion, clientOut.get().getUnion());
        Assert.assertEquals(intersectionSize, clientOut.get().getPsiCa());
    }
}
