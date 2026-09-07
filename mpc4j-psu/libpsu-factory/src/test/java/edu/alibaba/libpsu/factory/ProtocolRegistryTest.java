package edu.alibaba.libpsu.factory;

import edu.alibaba.libpsu.spi.ProtocolDescriptor;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
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
}
