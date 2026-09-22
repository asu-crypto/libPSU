package edu.alibaba.mpc4j.psu.contracts;

import edu.alibaba.libpsu.factory.ProtocolRegistry;
import edu.alibaba.libpsu.spi.ProtocolDescriptor;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiType;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuType;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

/** Integration checks for the metadata -> config -> runtime-type boundary. */
public class AllProtocolIntegrationTest {
    @Test
    public void testEveryBalancedProtocolBuildsThroughRegistry() {
        Map<PsuType, PsuConfig> configs = new EnumMap<>(PsuType.class);
        for (ProtocolDescriptor descriptor : ProtocolRegistry.allPsuDescriptors()) {
            Properties properties = new Properties();
            properties.setProperty("silent_cot", "true");
            properties.setProperty("rosn_type", "GMR21_NET");
            PsuConfig config = ProtocolRegistry.createPsuConfig(descriptor.getProtocolName(), properties);
            Assert.assertEquals(descriptor.getProtocolName(), config.getPtoType().protocolId());
            Assert.assertSame(config.getPtoType(), ProtocolRegistry.resolvePsuType(descriptor.getProtocolName()));
            Assert.assertNull("duplicate descriptor for " + config.getPtoType(), configs.put(config.getPtoType(), config));
        }
        Assert.assertEquals(PsuType.values().length, configs.size());
    }

    @Test
    public void testEveryUnbalancedAndLegacyPsiTypeHasAConfig() {
        UpsuConfig[] upsu = {new Tcl23UpsuConfig.Builder().build(), new Tbz25UpsuConfig.Builder(false).build()};
        PsiConfig[] psi = {new Ks05PsiConfig.Builder().build(), new Hn12PsiConfig.Builder().build()};
        Assert.assertEquals(UpsuType.values().length, upsu.length);
        Assert.assertEquals(PsiType.values().length, psi.length);
        for (UpsuType type : UpsuType.values()) {
            Assert.assertTrue(java.util.Arrays.stream(upsu).anyMatch(config -> config.getPtoType() == type));
        }
        for (PsiType type : PsiType.values()) {
            Assert.assertTrue(java.util.Arrays.stream(psi).anyMatch(config -> config.getPtoType() == type));
        }
    }
}
