package edu.alibaba.libpsu.factory;

import edu.alibaba.libpsu.spi.ProtocolDescriptor;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psu.ks05.Ks05PsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ProtocolRegistryTest {
    @Test
    public void testFindsDescriptorAndAliases() {
        ProtocolDescriptor descriptor = ProtocolRegistry.findDescriptor("PKC_GMRSS21").orElseThrow();
        Assert.assertEquals("PKC:GMRSS21", descriptor.getProtocolName());
        Assert.assertTrue(descriptor.supportsName("PKC:GMRSS21"));
        Assert.assertTrue(ProtocolRegistry.findDescriptor("GMR21").isPresent());
        Assert.assertEquals(
            "PKC:GMRSS21",
            ProtocolRegistry.findDescriptor("GMR21").orElseThrow().getProtocolName()
        );
    }

    @Test
    public void testDefaultDescriptorsExcludeExperimental() {
        Assert.assertTrue(ProtocolRegistry.findDescriptor("Ours").orElseThrow().getProtocolInfo()
            .isSmallSetOptimization());
        Assert.assertFalse(
            ProtocolRegistry.defaultPsuDescriptors().stream()
                .anyMatch(descriptor -> descriptor.supportsName("Ours"))
        );
    }

    @Test
    public void testDescriptorCreatesSimpleConfig() {
        PsuConfig config = ProtocolRegistry.createPsuConfig("AC_KRTW19", new Properties());
        Assert.assertEquals(PsuType.AC_KRTW19, config.getPtoType());
    }

    @Test
    public void testNamedConfigPreservesDefaultsWithoutMutatingCaller() {
        Properties defaults = new Properties();
        defaults.setProperty("ks05_max_set_size", "32");
        defaults.setProperty("psu_pto_name", "AC:KRTW19");
        Properties properties = new Properties(defaults);

        Ks05PsuConfig config = (Ks05PsuConfig) ProtocolRegistry.createPsuConfig("KS05", properties);
        Assert.assertEquals(PsuType.C_KisSon05, config.getPtoType());
        Assert.assertEquals(32, config.getMaxSetSize());
        Assert.assertTrue(properties.isEmpty());
        Assert.assertEquals("AC:KRTW19", properties.getProperty("psu_pto_name"));
        Assert.assertEquals("32", defaults.getProperty("ks05_max_set_size"));

        properties.setProperty("ks05_max_set_size", "64");
        config = (Ks05PsuConfig) ProtocolRegistry.createPsuConfig("C:KisSon05", properties);
        Assert.assertEquals(64, config.getMaxSetSize());
        Assert.assertEquals("64", properties.getProperty("ks05_max_set_size"));
        Assert.assertEquals("32", defaults.getProperty("ks05_max_set_size"));
    }
}
