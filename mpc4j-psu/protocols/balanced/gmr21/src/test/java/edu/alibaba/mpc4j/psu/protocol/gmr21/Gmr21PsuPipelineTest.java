package edu.alibaba.mpc4j.psu.protocol.gmr21;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuServer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parity: plugin pipeline client vs legacy {@link Gmr21PsuServer}.
 */
public class Gmr21PsuPipelineTest extends AbstractTwoPartyMemoryRpcPto {
    public Gmr21PsuPipelineTest() {
        super("GMR21_PIPELINE");
    }

    private static final int SIZE = 10;
    private static final int ELEMENT_BYTE_LENGTH = 16;

    @Test(timeout = 60_000)
    public void testLegacyClientBaseline() throws InterruptedException {
        runPair(new Gmr21PsuClient(secondRpc, firstRpc.ownParty(), config()));
    }

    @Test(timeout = 60_000)
    public void testPipelineMatchesLegacy() throws InterruptedException {
        runPair(new Gmr21PsuPipelineClient(secondRpc, firstRpc.ownParty(), config()));
    }

    private static Gmr21PsuConfig config() {
        return new Gmr21PsuConfig.Builder(false).build();
    }

    private void runPair(PsuClient client) throws InterruptedException {
        Gmr21PsuConfig cfg = config();
        Gmr21PsuServer server = new Gmr21PsuServer(firstRpc, secondRpc.ownParty(), cfg);
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
        Assert.assertTrue(actualUnion.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actualUnion));
    }
}
