package edu.alibaba.libpsu.factory;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.PsuPaperFidelity;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Smoke: every active {@link PsuType} resolves from config and instantiates server/client.
 */
public class LibPsuBalancedFactorySmokeTest extends AbstractTwoPartyMemoryRpcPto {

    public LibPsuBalancedFactorySmokeTest() {
        super("LibPsuBalancedFactorySmokeTest");
    }

    @Test
    public void allActivePsuTypesResolveAndInstantiate() {
        for (PsuType type : PsuType.values()) {
            if (PsuPaperFidelity.isReservedPaperExact(type)
                || PsuPaperFidelity.isRemovedProtocol(type)
                || type == PsuType.EUROCRYPT_PuGaoTri26) {
                continue;
            }
            String name = type.name();
            Assert.assertTrue("metadata: " + name, ProtocolRegistry.isKnownBenchmarkProtocol(name));
            Properties p = minimalProperties(name);
            PsuConfig config = PsuConfigUtils.createConfig(p);
            Assert.assertEquals(type.protocolId(), config.getPtoType().protocolId());
            Assert.assertEquals(type, config.getPtoType());
            Rpc serverRpc = firstRpc;
            Rpc clientRpc = secondRpc;
            Party clientParty = secondRpc.ownParty();
            Party serverParty = firstRpc.ownParty();
            PsuServer server = ProtocolRegistry.createPsuServer(serverRpc, clientParty, config);
            PsuClient client = ProtocolRegistry.createPsuClient(clientRpc, serverParty, config);
            Assert.assertNotNull(server);
            Assert.assertNotNull(client);
            server.destroy();
            client.destroy();
        }
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
