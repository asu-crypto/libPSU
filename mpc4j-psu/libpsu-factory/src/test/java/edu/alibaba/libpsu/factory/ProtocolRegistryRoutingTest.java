package edu.alibaba.libpsu.factory;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.impl.memory.MemoryRpcManager;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.psu.balanced.PsuLibrary;
import edu.alibaba.mpc4j.psu.protocol.gmr21.Gmr21PsuPipelineClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * The unified factory keeps the legacy pipeline selection and output-model guards.
 */
@SuppressWarnings("deprecation")
public class ProtocolRegistryRoutingTest {
    private static final String PIPELINE_PROPERTY = "mpc4j.psu.usePluginPipeline";
    private String previousPipelineProperty;
    private Rpc serverRpc;
    private Rpc clientRpc;

    @Before
    public void setUp() {
        previousPipelineProperty = System.getProperty(PIPELINE_PROPERTY);
        MemoryRpcManager rpcManager = new MemoryRpcManager(2);
        serverRpc = rpcManager.getRpc(0);
        clientRpc = rpcManager.getRpc(1);
    }

    @After
    public void restorePipelineProperty() {
        if (previousPipelineProperty == null) {
            System.clearProperty(PIPELINE_PROPERTY);
        } else {
            System.setProperty(PIPELINE_PROPERTY, previousPipelineProperty);
        }
    }

    @Test
    public void testDefaultClientMatchesCompatibilityRuntime() {
        System.clearProperty(PIPELINE_PROPERTY);
        assertGmr21Routing(Gmr21PsuClient.class);
    }

    @Test
    public void testPluginClientMatchesCompatibilityRuntime() {
        System.setProperty(PIPELINE_PROPERTY, "true");
        assertGmr21Routing(Gmr21PsuPipelineClient.class);
    }

    @Test
    public void testPluginSelectionPreservesTwoSidedGuard() {
        System.setProperty(PIPELINE_PROPERTY, "true");
        PsuConfig config = new Pt26PsuConfig.Builder().build();
        try {
            ProtocolRegistry.createPsuClient(clientRpc, serverRpc.ownParty(), config);
            Assert.fail("Two-sided protocols must not use the one-sided client factory");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("TWO_SIDED"));
        }
        try {
            ProtocolRegistry.createPsuServer(serverRpc, clientRpc.ownParty(), config);
            Assert.fail("Two-sided protocols must not use the one-sided server factory");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("TWO_SIDED"));
        }
    }

    private void assertGmr21Routing(Class<? extends PsuClient> expectedClientType) {
        Gmr21PsuConfig config = new Gmr21PsuConfig.Builder(false).build();
        config.setEnvType(EnvType.STANDARD_JDK);
        PsuClient client = ProtocolRegistry.createPsuClient(clientRpc, serverRpc.ownParty(), config);
        PsuClient compatibilityClient = PsuLibrary.createClient(clientRpc, serverRpc.ownParty(), config);
        PsuServer server = ProtocolRegistry.createPsuServer(serverRpc, clientRpc.ownParty(), config);
        try {
            Assert.assertEquals(expectedClientType, client.getClass());
            Assert.assertEquals(compatibilityClient.getClass(), client.getClass());
            Assert.assertEquals(Gmr21PsuServer.class, server.getClass());
        } finally {
            client.destroy();
            compatibilityClient.destroy();
            server.destroy();
        }
    }
}
