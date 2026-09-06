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
    }
}
