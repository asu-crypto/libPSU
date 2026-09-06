package edu.alibaba.mpc4j.s2pc.pso.psu.czz24;

import edu.alibaba.mpc4j.common.rpc.RpcManager;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPto;
import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.structure.filter.FilterFactory.FilterType;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.MqRpmtClient;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.MqRpmtFactory;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.MqRpmtServer;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.czz24.Czz24CwOprfMqRpmtConfig;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deterministic Kunlun interop vectors for CZZ24 CW-OPRF mqRPMT and PSU.
 */
public class Czz24CwOprfInteropTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(Czz24CwOprfInteropTest.class);

    private RecordingRpc serverRpc;
    private RecordingRpc clientRpc;

    @Before
    public void connect() {
        RpcManager rpcManager = new MemoryRpcManager(2);
        serverRpc = new RecordingRpc(rpcManager.getRpc(0));
        clientRpc = new RecordingRpc(rpcManager.getRpc(1));
        serverRpc.connect();
        clientRpc.connect();
    }

    @After
    public void disconnect() {
        if (serverRpc != null) {
            serverRpc.disconnect();
        }
        if (clientRpc != null) {
            clientRpc.disconnect();
        }
    }

    @Test
    public void testPsuConfigReadsFilterType() {
        Properties properties = new Properties();
        properties.setProperty("psu_pto_name", "PKC_CheZhaZha24");
        properties.setProperty("filter_type", "NAIVE_RANDOM_BLOOM_FILTER");

        PsuConfig config = PsuConfigUtils.createConfig(properties);
        Assert.assertTrue(config instanceof Czz24CwOprfPsuConfig);
        Assert.assertEquals(
            FilterType.NAIVE_RANDOM_BLOOM_FILTER,
            ((Czz24CwOprfPsuConfig) config).getFilterType()
        );
    }

    @Test
    public void testDeterministicMqRpmtWireDump() throws Exception {
        Czz24CwOprfMqRpmtConfig config = new Czz24CwOprfMqRpmtConfig.Builder().build();
        WireRun first = runMqRpmt(config);
        WireRun second = runMqRpmt(config);

        Assert.assertEquals(first.wireSummary, second.wireSummary);
        Assert.assertEquals(4, first.containVector.length);
        Assert.assertEquals(2, countTrue(first.containVector));
        assertContainVector(first.serverVector, Czz24InteropSupport.clientSet(), first.containVector);

        LOGGER.info(
            "CZZ24 mqRPMT interop wire dump (serverSeed=0x{}, clientSeed=0x{}):\n{}",
            Long.toHexString(Czz24InteropSupport.SERVER_RNG_SEED),
            Long.toHexString(Czz24InteropSupport.CLIENT_RNG_SEED),
            first.wireSummary
        );
    }

    @Test
    public void testDeterministicPsuWireDumpAndUnion() throws Exception {
        Czz24CwOprfPsuConfig config = new Czz24CwOprfPsuConfig.Builder().build();
        WireRun first = runPsu(config);
        WireRun second = runPsu(config);

        Assert.assertEquals(first.wireSummary, second.wireSummary);
        Assert.assertEquals(Czz24InteropSupport.EXPECTED_INTERSECTION_SIZE, first.psica);
        Assert.assertEquals(Czz24InteropSupport.EXPECTED_UNION_SIZE, first.union.size());
        Assert.assertTrue(first.union.containsAll(Czz24InteropSupport.expectedUnion()));
        Assert.assertTrue(Czz24InteropSupport.expectedUnion().containsAll(first.union));

        LOGGER.info(
            "CZZ24 PSU interop wire dump (serverSeed=0x{}, clientSeed=0x{}):\n{}",
            Long.toHexString(Czz24InteropSupport.SERVER_RNG_SEED),
            Long.toHexString(Czz24InteropSupport.CLIENT_RNG_SEED),
            first.wireSummary
        );
    }

    @Test
    public void testDeterministicPsuWithBloomFilter() throws Exception {
        Czz24CwOprfPsuConfig config = new Czz24CwOprfPsuConfig.Builder()
            .setFilterType(FilterType.NAIVE_RANDOM_BLOOM_FILTER)
            .build();
        WireRun run = runPsu(config);

        Assert.assertEquals(Czz24InteropSupport.EXPECTED_INTERSECTION_SIZE, run.psica);
        Assert.assertEquals(Czz24InteropSupport.EXPECTED_UNION_SIZE, run.union.size());
        Assert.assertFalse(run.wireSummary.isEmpty());
        LOGGER.info("CZZ24 PSU Bloom-filter wire dump:\n{}", run.wireSummary);
    }

    private WireRun runMqRpmt(Czz24CwOprfMqRpmtConfig config) throws Exception {
        serverRpc.clearCaptures();
        clientRpc.clearCaptures();
        useJdkCrypto(config);

        MqRpmtServer server = MqRpmtFactory.createServer(serverRpc, clientRpc.ownParty(), config);
        MqRpmtClient client = MqRpmtFactory.createClient(clientRpc, serverRpc.ownParty(), config);
        configureParties(server, client);

        Set<ByteBuffer> serverSet = Czz24InteropSupport.serverSet();
        Set<ByteBuffer> clientSet = Czz24InteropSupport.clientSet();

        ByteBuffer[][] serverVector = new ByteBuffer[1][];
        boolean[][] containVector = new boolean[1][];

        Exception[] serverError = new Exception[1];
        Exception[] clientError = new Exception[1];
        Thread serverThread = new Thread(() -> {
            try {
                server.init(serverSet.size(), clientSet.size());
                serverVector[0] = server.mqRpmt(serverSet, clientSet.size());
            } catch (Exception e) {
                serverError[0] = e;
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                client.init(clientSet.size(), serverSet.size());
                containVector[0] = client.mqRpmt(clientSet, serverSet.size());
            } catch (Exception e) {
                clientError[0] = e;
            }
        });

        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();
        if (serverError[0] != null) {
            throw serverError[0];
        }
        if (clientError[0] != null) {
            throw clientError[0];
        }

        return new WireRun(
            mergeCaptures(),
            serverVector[0],
            containVector[0],
            -1,
            null
        );
    }

    private WireRun runPsu(Czz24CwOprfPsuConfig config) throws Exception {
        serverRpc.clearCaptures();
        clientRpc.clearCaptures();
        useJdkCrypto(config);

        PsuServer server = PsuFactory.createServer(serverRpc, clientRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(clientRpc, serverRpc.ownParty(), config);
        configureParties(server, client);

        Set<ByteBuffer> serverSet = Czz24InteropSupport.serverSet();
        Set<ByteBuffer> clientSet = Czz24InteropSupport.clientSet();
        int elementByteLength = Czz24InteropSupport.ELEMENT_BYTE_LENGTH;

        PsuClientOutput[] output = new PsuClientOutput[1];
        Exception[] serverError = new Exception[1];
        Exception[] clientError = new Exception[1];
        Thread serverThread = new Thread(() -> {
            try {
                server.init(serverSet.size(), clientSet.size());
                server.psu(serverSet, clientSet.size(), elementByteLength);
            } catch (Exception e) {
                serverError[0] = e;
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                client.init(clientSet.size(), serverSet.size());
                output[0] = client.psu(clientSet, serverSet.size(), elementByteLength);
            } catch (Exception e) {
                clientError[0] = e;
            }
        });

        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();
        if (serverError[0] != null) {
            throw serverError[0];
        }
        if (clientError[0] != null) {
            throw clientError[0];
        }
        Assert.assertNotNull(output[0]);

        return new WireRun(
            mergeCaptures(),
            null,
            null,
            output[0].getPsiCa(),
            output[0].getUnion()
        );
    }

    private static void useJdkCrypto(MultiPartyPtoConfig config) {
        config.setEnvType(EnvType.STANDARD_JDK);
    }

    private void configureParties(Object server, Object client) {
        SecureRandom serverRandom = Czz24InteropSupport.deterministicRandom(Czz24InteropSupport.SERVER_RNG_SEED);
        SecureRandom clientRandom = Czz24InteropSupport.deterministicRandom(Czz24InteropSupport.CLIENT_RNG_SEED);
        if (server instanceof MultiPartyPto serverPto) {
            serverPto.setTaskId(Czz24InteropSupport.FIXED_TASK_ID);
            serverPto.setSecureRandom(serverRandom);
            serverPto.setParallel(false);
        }
        if (client instanceof MultiPartyPto clientPto) {
            clientPto.setTaskId(Czz24InteropSupport.FIXED_TASK_ID);
            clientPto.setSecureRandom(clientRandom);
            clientPto.setParallel(false);
        }
    }

    private String mergeCaptures() {
        List<RecordingRpc.CapturedPacket> packets = new java.util.ArrayList<>();
        packets.addAll(serverRpc.getSentPackets());
        packets.addAll(clientRpc.getSentPackets());
        return Czz24InteropSupport.summarizeWireCaptures(packets);
    }

    private static int countTrue(boolean[] values) {
        int count = 0;
        for (boolean value : values) {
            if (value) {
                count++;
            }
        }
        return count;
    }

    private static void assertContainVector(
        ByteBuffer[] serverVector,
        Set<ByteBuffer> clientSet,
        boolean[] containVector
    ) {
        for (int i = 0; i < containVector.length; i++) {
            Assert.assertEquals(clientSet.contains(serverVector[i]), containVector[i]);
        }
    }

    private static final class WireRun {
        private final String wireSummary;
        private final ByteBuffer[] serverVector;
        private final boolean[] containVector;
        private final int psica;
        private final Set<ByteBuffer> union;

        private WireRun(
            String wireSummary,
            ByteBuffer[] serverVector,
            boolean[] containVector,
            int psica,
            Set<ByteBuffer> union
        ) {
            this.wireSummary = wireSummary;
            this.serverVector = serverVector;
            this.containVector = containVector;
            this.psica = psica;
            this.union = union;
        }
    }
}
