package edu.alibaba.mpc4j.psu.protocol.haowan2026;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuServer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hao–Wan 2026 balanced ePSU correctness: balanced, empty, partial, and full intersection.
 */
public class HaoWan2026Psu2p5Test extends AbstractTwoPartyMemoryRpcPto {
    private static final int SIZE = 32;
    private static final int ELEMENT_BYTE_LENGTH = 16;
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    public HaoWan2026Psu2p5Test() {
        super("HAO_WAN2026_PSU");
    }

    @Test(timeout = 300_000)
    public void testBalancedUnion() throws InterruptedException {
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(SIZE, SIZE, ELEMENT_BYTE_LENGTH);
        runAndAssertUnion(sets.get(0), sets.get(1));
    }

    @Test(timeout = 300_000)
    public void testEmptyIntersection() throws InterruptedException {
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(SIZE, SIZE, ELEMENT_BYTE_LENGTH);
        // Force disjointness by regenerating client until empty intersection (usually already near-empty for random).
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = new HashSet<>();
        while (clientSet.size() < SIZE) {
            byte[] e = new byte[ELEMENT_BYTE_LENGTH];
            SECURE_RANDOM.nextBytes(e);
            ByteBuffer buf = ByteBuffer.wrap(e);
            if (!serverSet.contains(buf)) {
                clientSet.add(buf);
            }
        }
        PsuClientOutput out = runAndAssertUnion(serverSet, clientSet);
        Assert.assertEquals(0, out.getPsiCa());
    }

    @Test(timeout = 300_000)
    public void testFullIntersection() throws InterruptedException {
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(SIZE, SIZE, ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = new HashSet<>(serverSet);
        PsuClientOutput out = runAndAssertUnion(serverSet, clientSet);
        Assert.assertEquals(SIZE, out.getPsiCa());
        Assert.assertEquals(SIZE, out.getUnion().size());
    }

    @Test(timeout = 300_000)
    public void testUnequalSizes() throws InterruptedException {
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(SIZE, SIZE / 2, ELEMENT_BYTE_LENGTH);
        runAndAssertUnion(sets.get(0), sets.get(1));
    }

    private PsuClientOutput runAndAssertUnion(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet)
        throws InterruptedException {
        HaoWan2026PsuConfig config = new HaoWan2026PsuConfig.Builder(false).build();
        HaoWan2026PsuServer server = new HaoWan2026PsuServer(firstRpc, secondRpc.ownParty(), config);
        HaoWan2026PsuClient client = new HaoWan2026PsuClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

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
            TIMEOUT_MS,
            "HaoWan2026-2p5"
        );

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Set<ByteBuffer> actualUnion = clientOut.get().getUnion();
        Assert.assertEquals(expectUnion.size(), actualUnion.size());
        Assert.assertTrue(actualUnion.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actualUnion));
        return clientOut.get();
    }
}
