package edu.alibaba.libpsu.factory;

import edu.alibaba.libpsu.api.OutputModel;
import edu.alibaba.libpsu.api.PsuProtocolCapabilities;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.PsuPaperFidelity;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Smoke: every active {@link PsuType} resolves from config and instantiates the correct party API.
 */
public class LibPsuBalancedFactorySmokeTest extends AbstractTwoPartyMemoryRpcPto {

    public LibPsuBalancedFactorySmokeTest() {
        super("LibPsuBalancedFactorySmokeTest");
    }

    @Test
    public void allActivePsuTypesResolveAndInstantiate() {
        for (PsuType type : PsuType.values()) {
            if (PsuPaperFidelity.isReservedPaperExact(type) || PsuPaperFidelity.isRemovedProtocol(type)) {
                continue;
            }
            String name = type.name();
            Assert.assertTrue("metadata: " + name, ProtocolRegistry.isKnownBenchmarkProtocol(name));
            Properties p = minimalProperties(name);
            PsuConfig config = PsuConfigUtils.createConfig(p);
            Assert.assertEquals(type.protocolId(), config.getPtoType().protocolId());
            Assert.assertEquals(type, config.getPtoType());

            PsuProtocolCapabilities caps = ProtocolRegistry.capabilitiesOf(config);
            Rpc serverRpc = firstRpc;
            Rpc clientRpc = secondRpc;
            Party clientParty = secondRpc.ownParty();
            Party serverParty = firstRpc.ownParty();

            if (ProtocolRegistry.usesTwoSidedPublicFactory(config)) {
                Assert.assertTrue(caps.isTwoSided());
                PsuTwoSidedServer server = ProtocolRegistry.createTwoSidedPsuServer(serverRpc, clientParty, config);
                PsuTwoSidedClient client = ProtocolRegistry.createTwoSidedPsuClient(clientRpc, serverParty, config);
                Assert.assertNotNull(server);
                Assert.assertNotNull(client);
                server.destroy();
                client.destroy();
            } else {
                Assert.assertTrue(
                    "one-sided public API for " + caps.protocolId() + " / " + caps.outputModel(),
                    OutputModel.isOneSidedPublicApi(caps.outputModel())
                );
                PsuServer server = ProtocolRegistry.createPsuServer(serverRpc, clientParty, config);
                PsuClient client = ProtocolRegistry.createPsuClient(clientRpc, serverParty, config);
                Assert.assertNotNull(server);
                Assert.assertNotNull(client);
                server.destroy();
                client.destroy();
            }
        }
    }

    @Test
    public void pt26MetadataAndFactoryAreTwoSided() {
        PsuConfig config = PsuConfigUtils.createConfig(minimalProperties(PsuType.EUROCRYPT_PisTri26.name()));
        PsuProtocolCapabilities caps = ProtocolRegistry.capabilitiesOf(config);
        Assert.assertEquals(OutputModel.TWO_SIDED, caps.outputModel());
        Assert.assertTrue(ProtocolRegistry.usesTwoSidedPublicFactory(config));
        PsuTwoSidedServer server = ProtocolRegistry.createTwoSidedPsuServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = ProtocolRegistry.createTwoSidedPsuClient(secondRpc, firstRpc.ownParty(), config);
        server.destroy();
        client.destroy();
    }

    @Test
    public void pgt26MetadataAndFactoryAreMaliciousTwoSided() {
        PsuConfig config = PsuConfigUtils.createConfig(minimalProperties(PsuType.EUROCRYPT_PuGaoTri26.name()));
        PsuProtocolCapabilities caps = ProtocolRegistry.capabilitiesOf(config);
        Assert.assertEquals(OutputModel.MALICIOUS_TWO_SIDED, caps.outputModel());
        Assert.assertTrue(ProtocolRegistry.usesTwoSidedPublicFactory(config));
        PsuTwoSidedServer server = ProtocolRegistry.createTwoSidedPsuServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = ProtocolRegistry.createTwoSidedPsuClient(secondRpc, firstRpc.ownParty(), config);
        server.destroy();
        client.destroy();
    }

    @Test
    public void wrongOutputModelFactoryIsRejected() {
        PsuConfig pt26 = PsuConfigUtils.createConfig(minimalProperties(PsuType.EUROCRYPT_PisTri26.name()));
        try {
            ProtocolRegistry.createPsuServer(firstRpc, secondRpc.ownParty(), pt26);
            Assert.fail("expected rejection of one-sided factory for PT26");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("TWO_SIDED"));
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("createTwoSided"));
        }

        PsuConfig gmr21 = PsuConfigUtils.createConfig(minimalProperties(PsuType.PKC_GMRSS21.name()));
        try {
            ProtocolRegistry.createTwoSidedPsuServer(firstRpc, secondRpc.ownParty(), gmr21);
            Assert.fail("expected rejection of two-sided factory for GMR21");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("ONE_SIDED") || ex.getMessage().contains("createPsuServer"));
        }
    }

    @Test
    public void defaultTwoSidedMaliciousConfigIsPgt26() {
        PsuConfig config = ProtocolRegistry.createDefaultTwoSidedConfig(SecurityModel.MALICIOUS);
        Assert.assertEquals(PsuType.EUROCRYPT_PuGaoTri26, config.getPtoType());
        Assert.assertEquals(OutputModel.MALICIOUS_TWO_SIDED, ProtocolRegistry.capabilitiesOf(config).outputModel());
    }

    private static Properties minimalProperties(String psuPtoName) {
        Properties p = new Properties();
        p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, psuPtoName);
        p.setProperty("silent_cot", "true");
        p.setProperty("compress_encode", "true");
        if (PsuType.PKC_GMRSS21.name().equals(psuPtoName)
            || PsuType.USENIX_JSZDG22.name().equals(psuPtoName)
            || PsuType.USENIX_JSZDG22_SFS.name().equals(psuPtoName)) {
            p.setProperty(PsuConfigUtils.ROSN_TYPE, "LLL24_FLAT_NET");
        }
        if (PsuType.ASIACCS_CSSW25.name().equals(psuPtoName)) {
            p.setProperty(PsuConfigUtils.ROSN_TYPE, "LLL24_FLAT_NET");
        }
        if (PsuType.Ours.name().equals(psuPtoName)) {
            p.setProperty("small_ec_item_bit_length", "128");
        }
        if (PsuType.USENIX_YanShiHonDaw24.name().equals(psuPtoName)) {
            p.setProperty("jszg24_item_bit_length", "128");
        }
        return p;
    }
}
