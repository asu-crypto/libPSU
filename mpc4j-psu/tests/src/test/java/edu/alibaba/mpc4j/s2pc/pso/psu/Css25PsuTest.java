package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuConfig;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Small-set correctness tests for ASIACCS_CSSW25 PSU (Ccpsi + OPRF + ROSN ShTr + COT).
 *
 * @author mpc4j
 * @date 2026/05/14
 */
public class Css25PsuTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(Css25PsuTest.class);
    private static Boolean nativeToolAvailable;

    public Css25PsuTest() {
        super("CSS25_PSU");
    }

    private static Css25PsuConfig testConfig(boolean silent) {
        return new Css25PsuConfig.Builder(silent).build();
    }

    @Test
    public void testTinyDirect() throws InterruptedException {
        runCase(testConfig(false), 4, 4, Long.BYTES, false);
    }

    @Test
    public void testSmallSilent() throws InterruptedException {
        runCase(testConfig(true), 12, 10, CommonConstants.BLOCK_BYTE_LENGTH, false);
    }

    @Test
    public void testPaperComparisonConstruction() {
        Css25PsuConfig config = new Css25PsuConfig.Builder(true).setPaperComparisonProxy().build();
        Assert.assertTrue(config.isPaperComparisonProxy());
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        Assert.assertNotNull(server);
        Assert.assertNotNull(client);
        server.destroy();
        client.destroy();
    }

    @Test
    public void testPaperExactConstructionRejected() {
        Css25PsuConfig config = new Css25PsuConfig.Builder(true).setPaperExact().build();
        Assert.assertEquals(PsuType.ASIACCS_CSSW25, config.getPtoType());
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config)
        );
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config)
        );
    }

    @Test
    public void testOriginalServerElementsReturned() throws InterruptedException {
        int elementByteLength = CommonConstants.BLOCK_BYTE_LENGTH;
        Set<ByteBuffer> serverSet = fixedSet(elementByteLength, 1L, 4L, 7L, 9L);
        Set<ByteBuffer> clientSet = fixedSet(elementByteLength, 2L, 4L, 6L, 8L);
        runCase(testConfig(false), serverSet, clientSet, elementByteLength, false);
    }

    @Test
    public void testSmallParallel() throws InterruptedException {
        runCase(testConfig(false), 16, 14, Long.BYTES, true);
    }

    private void runCase(Css25PsuConfig config, int serverSize, int clientSize, int elementByteLength, boolean parallel)
        throws InterruptedException {
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = sets.get(1);
        runCase(config, serverSet, clientSet, elementByteLength, parallel);
    }

    private void runCase(
        Css25PsuConfig config, Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, int elementByteLength,
        boolean parallel
    ) throws InterruptedException {
        assumeNativeToolAvailable();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);

        LOGGER.info("ASIACCS_CSSW25 test server_size = {}, client_size = {}", serverSet.size(), clientSet.size());
        PsuServerThread serverThread = new PsuServerThread(server, serverSet, clientSet.size(), elementByteLength);
        PsuClientThread clientThread = new PsuClientThread(client, clientSet, serverSet.size(), elementByteLength);
        serverThread.start();
        clientThread.start();
        TwoPartyTestJoin.joinFailFast(
            serverThread, serverThread::getFailure, server::destroy,
            clientThread, clientThread::getFailure, client::destroy,
            "ASIACCS_CSSW25"
        );

        assertUnionAndPsiCa(serverSet, clientSet, clientThread.getClientOutput());
        printAndResetRpc(0);
    }

    private static void assumeNativeToolAvailable() {
        if (nativeToolAvailable == null) {
            try {
                System.loadLibrary(CommonConstants.MPC4J_NATIVE_TOOL_NAME);
                nativeToolAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                nativeToolAvailable = false;
            }
        }
        Assume.assumeTrue("ASIACCS_CSSW25 runtime tests require mpc4j-native-tool", nativeToolAvailable);
    }

    private static Set<ByteBuffer> fixedSet(int elementByteLength, long... values) {
        Set<ByteBuffer> set = new HashSet<>();
        for (long value : values) {
            byte[] element = new byte[elementByteLength];
            ByteBuffer.wrap(element).putLong(elementByteLength - Long.BYTES, value);
            set.add(ByteBuffer.wrap(element));
        }
        return set;
    }

    private static void assertUnionAndPsiCa(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, PsuClientOutput out) {
        Set<ByteBuffer> expectIntersection = new HashSet<>(serverSet);
        expectIntersection.retainAll(clientSet);
        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Assert.assertEquals(expectIntersection.size(), out.getPsiCa());
        Set<ByteBuffer> actual = out.getUnion();
        Assert.assertTrue(actual.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actual));
    }
}
