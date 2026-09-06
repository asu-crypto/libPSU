package edu.alibaba.libpsu.api;

import org.junit.Assert;
import org.junit.Test;

public class ProtocolMetadataRegistryTest {
    @Test
    public void testMetadataHasReadinessAndSizePolicy() {
        ProtocolInfo pgt26 = ProtocolMetadataRegistry.findByName("EUROCRYPT_PuGaoTri26").orElseThrow();
        Assert.assertEquals(ProtocolReadiness.STABLE, pgt26.getReadiness());
        Assert.assertEquals(2, pgt26.getMinimumInputSetSize());
        Assert.assertTrue(pgt26.isDefaultEnabled());

        ProtocolInfo smallEc = ProtocolMetadataRegistry.findByName("Ours").orElseThrow();
        Assert.assertEquals(ProtocolReadiness.EXPERIMENTAL, smallEc.getReadiness());
        Assert.assertFalse(smallEc.isDefaultEnabled());
        Assert.assertEquals(OutputModel.LEAKAGE_BASELINE, smallEc.getOutputModel());
    }

    @Test
    public void tbz25BalancedAndUpsuBothDiscoverable() {
        Assert.assertTrue(ProtocolMetadataRegistry.findByName("USENIX:BinYujConYanYu25").isEmpty());
        Assert.assertEquals(2, ProtocolMetadataRegistry.findAllByName("USENIX:BinYujConYanYu25").size());

        ProtocolInfo balanced = ProtocolMetadataRegistry
            .findByNameAndFunctionality("USENIX:BinYujConYanYu25", ProtocolFunctionality.PSU)
            .orElseThrow();
        ProtocolInfo upsu = ProtocolMetadataRegistry
            .findByNameAndFunctionality("USENIX:BinYujConYanYu25", ProtocolFunctionality.UPSU)
            .orElseThrow();

        Assert.assertEquals(ProtocolFunctionality.PSU, balanced.getFunctionality());
        Assert.assertEquals(SetSizeMode.BALANCED, balanced.getSetSizeMode());
        Assert.assertEquals("mpc4j-psu-protocol-tbz25", balanced.getMavenModule());

        Assert.assertEquals(ProtocolFunctionality.UPSU, upsu.getFunctionality());
        Assert.assertEquals(SetSizeMode.UNBALANCED, upsu.getSetSizeMode());
        Assert.assertEquals("mpc4j-psu-protocol-tbz25-upsu", upsu.getMavenModule());
    }

    @Test
    public void hn12IsExperimentalSemiHonestTwoSided() {
        ProtocolInfo hn12 = ProtocolMetadataRegistry.findByName("JOC:HazNis12").orElseThrow();
        Assert.assertEquals(ProtocolReadiness.EXPERIMENTAL, hn12.getReadiness());
        Assert.assertEquals(LibPsuSecurityModel.SEMI_HONEST, hn12.getSecurityModel());
        Assert.assertEquals(OutputModel.TWO_SIDED, hn12.getOutputModel());
    }
}
