package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26IbltParams;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Correctness tests for EUROCRYPT_PisTri26 PSU (Piske &amp; Trieu, EUROCRYPT 2026).
 */
public class Pt26PsuTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(Pt26PsuTest.class);

    public Pt26PsuTest() {
        super("PT26_PSU");
    }

    @Test
    public void testTiny() throws InterruptedException {
        runCase(4, 4, Long.BYTES, false);
    }

    @Test
    public void testSmall() throws InterruptedException {
        runCase(10, 12, CommonConstants.BLOCK_BYTE_LENGTH, false);
    }

    @Test
    public void testTable1ExpansionSchedule() {
        Assert.assertEquals(4.5, Pt26IbltParams.chooseExpansion(1 << 14), 0.000001);
        Assert.assertEquals(3.5, Pt26IbltParams.chooseExpansion(1 << 16), 0.000001);
        Assert.assertEquals(2.0, Pt26IbltParams.chooseExpansion(1 << 18), 0.000001);
        Assert.assertEquals(1.5, Pt26IbltParams.chooseExpansion(1 << 20), 0.000001);
        Assert.assertEquals(1.5, Pt26IbltParams.chooseExpansion(1 << 22), 0.000001);
    }

    private void runCase(int serverSize, int clientSize, int elementByteLength, boolean parallel)
        throws InterruptedException {
        Pt26PsuConfig config = new Pt26PsuConfig.Builder().build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);

        LOGGER.info("EUROCRYPT_PisTri26 test server_size = {}, client_size = {}", serverSize, clientSize);
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = sets.get(1);
        PsuServerThread serverThread = new PsuServerThread(server, serverSet, clientSet.size(), elementByteLength);
        PsuClientThread clientThread = new PsuClientThread(client, clientSet, serverSet.size(), elementByteLength);
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();

        assertUnionAndPsiCa(serverSet, clientSet, clientThread.getClientOutput());
        printAndResetRpc(0);
        new Thread(server::destroy).start();
        new Thread(client::destroy).start();
    }

    private static void assertUnionAndPsiCa(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, PsuClientOutput out) {
        Set<ByteBuffer> expectIntersection = new HashSet<>(serverSet);
        expectIntersection.retainAll(clientSet);
        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Assert.assertEquals(expectIntersection.size(), out.getPsiCa());
        Assert.assertTrue(out.getUnion().containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(out.getUnion()));
    }
}
