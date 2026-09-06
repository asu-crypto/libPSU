package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfcPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfsPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23PkePsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuConfig;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * PSU协议测试。
 *
 * @author Weiran Liu
 * @date 2022/02/15
 */
@RunWith(Parameterized.class)
public class PsuTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsuTest.class);
    /**
     * 默认数量
     */
    private static final int DEFAULT_SIZE = 99;
    /**
     * 默认元素字节长度
     */
    private static final int DEFAULT_ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * 较小元素字节长度
     */
    private static final int SMALL_ELEMENT_BYTE_LENGTH = Long.BYTES;
    /**
     * 较大元素字节长度
     */
    private static final int LARGE_ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH * 2;
    /**
     * 较大数量
     */
    private static final int LARGE_SIZE = 1 << 12;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> configurations() {
        Collection<Object[]> configurations = new ArrayList<>();

        // PKC_CheZhaZha24
        configurations.add(new Object[]{
            PsuType.PKC_CheZhaZha24.name(), new Czz24CwOprfPsuConfig.Builder().build(),
        });
        // USENIX_JSZDG22_SFS (direct)
        configurations.add(new Object[]{
            PsuType.USENIX_JSZDG22_SFS.name() + " (direct)", new Jsz22SfsPsuConfig.Builder(false).build(),
        });
        // USENIX_JSZDG22_SFS (silent)
        configurations.add(new Object[]{
            PsuType.USENIX_JSZDG22_SFS.name() + " (silent)", new Jsz22SfsPsuConfig.Builder(true).build(),
        });
        // USENIX_JSZDG22 (direct)
        configurations.add(new Object[]{
            PsuType.USENIX_JSZDG22.name() + " (direct)", new Jsz22SfcPsuConfig.Builder(false).build(),
        });
        // USENIX_JSZDG22 (silent)
        configurations.add(new Object[]{
            PsuType.USENIX_JSZDG22.name() + " (silent)", new Jsz22SfcPsuConfig.Builder(true).build(),
        });
        // USENIX_ConYuWeiminDon23_PKE
        configurations.add(new Object[]{
            PsuType.USENIX_ConYuWeiminDon23_PKE.name(), new Zcl23PkePsuConfig.Builder().build(),
        });
        // USENIX_ConYuWeiminDon23_SKE
        configurations.add(new Object[]{
            PsuType.USENIX_ConYuWeiminDon23_SKE.name() + "(" + SecurityModel.IDEAL + ")",
            new Zcl23SkePsuConfig.Builder(SecurityModel.IDEAL, true).build(),
        });
        configurations.add(new Object[]{
            PsuType.USENIX_ConYuWeiminDon23_SKE.name() + "(" + SecurityModel.SEMI_HONEST + ", silent)",
            new Zcl23SkePsuConfig.Builder(SecurityModel.SEMI_HONEST, true).build(),
        });
        // PKC_GMRSS21 (direct)
        configurations.add(new Object[]{
            PsuType.PKC_GMRSS21.name() + " (direct)", new Gmr21PsuConfig.Builder(false).build(),
        });
        // PKC_GMRSS21 (silent)
        configurations.add(new Object[]{
            PsuType.PKC_GMRSS21.name() + " (silent)", new Gmr21PsuConfig.Builder(true).build(),
        });
        // AC_KRTW19
        configurations.add(new Object[]{
            PsuType.AC_KRTW19.name(), new Krtw19PsuConfig.Builder().build(),
        });
        // ACISP_DavCid17
        configurations.add(new Object[]{
            PsuType.ACISP_DavCid17.name(), new Dc17PsuConfig.Builder().build(),
        });
        // ACNS_Frikken07
        configurations.add(new Object[]{
            PsuType.ACNS_Frikken07.name(), new F07PsuConfig.Builder().build(),
        });

        return configurations;
    }

    /**
     * config
     */
    private final PsuConfig config;

    public PsuTest(String name, PsuConfig config) {
        super(name);
        this.config = config;
    }

    @Test
    public void test2() {
        testPto(2, 2, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void test10() {
        testPto(10, 10, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testLargeServerSize() {
        testPto(DEFAULT_SIZE, 10, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testLargeClientSize() {
        testPto(10, DEFAULT_SIZE, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testSmallElementByteLength() {
        testPto(DEFAULT_SIZE, DEFAULT_SIZE, SMALL_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testLargeElementByteLength() {
        testPto(DEFAULT_SIZE, DEFAULT_SIZE, LARGE_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testDefault() {
        testPto(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testParallelDefault() {
        testPto(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_ELEMENT_BYTE_LENGTH, true);
    }

    @Test
    public void testLarge() {
        testPto(LARGE_SIZE, LARGE_SIZE, LARGE_ELEMENT_BYTE_LENGTH, false);
    }

    @Test
    public void testParallelLarge() {
        testPto(LARGE_SIZE, LARGE_SIZE, LARGE_ELEMENT_BYTE_LENGTH, true);
    }

    private void testPto(int serverSize, int clientSize, int elementByteLength, boolean parallel) {
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);
        try {
            LOGGER.info("-----test {}, server_size = {}, client_size = {}-----",
                server.getPtoDesc().getPtoName(), serverSize, clientSize
            );
            // generate sets
            ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
            Set<ByteBuffer> serverSet = sets.get(0);
            Set<ByteBuffer> clientSet = sets.get(1);
            PsuServerThread serverThread = new PsuServerThread(server, serverSet, clientSet.size(), elementByteLength);
            PsuClientThread clientThread = new PsuClientThread(client, clientSet, serverSet.size(), elementByteLength);
            StopWatch stopWatch = new StopWatch();
            // start
            stopWatch.start();
            serverThread.start();
            clientThread.start();
            // stop
            serverThread.join();
            clientThread.join();
            serverThread.rethrowIfFailed();
            clientThread.rethrowIfFailed();
            stopWatch.stop();
            long time = stopWatch.getTime(TimeUnit.MILLISECONDS);
            stopWatch.reset();
            // verify
            assertOutput(serverSet, clientSet, clientThread.getClientOutput());
            printAndResetRpc(time);
            // destroy
            new Thread(server::destroy).start();
            new Thread(client::destroy).start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void assertOutput(Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, PsuClientOutput clientOutput) {
        // compute intersection and union
        Set<ByteBuffer> expectIntersectionSet = new HashSet<>(serverSet);
        expectIntersectionSet.retainAll(clientSet);
        int expectPsiCa = expectIntersectionSet.size();
        Set<ByteBuffer> expectUnionSet = new HashSet<>(serverSet);
        expectUnionSet.addAll(clientSet);
        Assert.assertEquals(expectPsiCa, clientOutput.getPsiCa());
        Set<ByteBuffer> actualUnionSet = clientOutput.getUnion();
        Assert.assertTrue(actualUnionSet.containsAll(expectUnionSet));
        Assert.assertTrue(expectUnionSet.containsAll(actualUnionSet));
    }
}
