package edu.alibaba.libpsu.factory;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psi.PsiConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiFactory;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Smoke: factory can instantiate parties for representative protocol types.
 */
public class LibPsuFactorySmokeTest extends AbstractTwoPartyMemoryRpcPto {
    public LibPsuFactorySmokeTest() {
        super("LibPsuFactorySmokeTest");
    }

    @Test
    public void createSmallEcElligatorPsuParties() throws Exception {
        Properties p = new Properties();
        p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "Ours");
        p.setProperty("small_ec_item_bit_length", "128");
        p.setProperty("silent_cot", "true");
        p.setProperty("compress_encode", "true");
        PsuConfig config = ProtocolRegistry.createPsuConfig(p);
        Assert.assertEquals(PsuType.Ours, config.getPtoType());
        Assert.assertTrue(config instanceof SmallEcElligatorPsuConfig);

        PsuServer psuServer = ProtocolRegistry.createPsuServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient psuClient = ProtocolRegistry.createPsuClient(secondRpc, firstRpc.ownParty(), config);
        Assert.assertNotNull(psuServer);
        Assert.assertNotNull(psuClient);
    }

    @Test
    public void createKs05PsiParties() {
        Properties p = new Properties();
        p.setProperty(PsiConfigUtils.PSI_PTO_NAME_KEY, "C:KisSon05");
        p.setProperty("silent_cot", "true");
        var config = PsiConfigUtils.createConfig(p);
        PsiServer server = PsiFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsiClient client = PsiFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        Assert.assertNotNull(server);
        Assert.assertNotNull(client);
    }

    @Test
    public void createKs05AndHn12PsuParties() {
        Properties ks = new Properties();
        ks.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "C:KisSon05");
        ks.setProperty("ks05_max_set_size", "32");
        PsuConfig ksConfig = ProtocolRegistry.createPsuConfig(ks);
        Assert.assertEquals(PsuType.C_KisSon05, ksConfig.getPtoType());
        Assert.assertNotNull(ProtocolRegistry.createPsuServer(firstRpc, secondRpc.ownParty(), ksConfig));
        Assert.assertNotNull(ProtocolRegistry.createPsuClient(secondRpc, firstRpc.ownParty(), ksConfig));

        Properties hn = new Properties();
        hn.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "JOC:HazNis12");
        PsuConfig hnConfig = ProtocolRegistry.createPsuConfig(hn);
        Assert.assertEquals(PsuType.JOC_HazNis12, hnConfig.getPtoType());
        Assert.assertNotNull(ProtocolRegistry.createPsuServer(firstRpc, secondRpc.ownParty(), hnConfig));
        Assert.assertNotNull(ProtocolRegistry.createPsuClient(secondRpc, firstRpc.ownParty(), hnConfig));
    }
}
