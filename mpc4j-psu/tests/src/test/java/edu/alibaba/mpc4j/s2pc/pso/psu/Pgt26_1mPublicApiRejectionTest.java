package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.factory.ProtocolRegistry;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Public factory/config APIs must reject one-sided PGT26_1M (internal only).
 */
public class Pgt26_1mPublicApiRejectionTest {
    @Test
    public void fromProtocolIdRejectsPgt26_1m() {
        IllegalArgumentException ex = Assert.assertThrows(
            IllegalArgumentException.class,
            () -> PsuType.fromProtocolId("PGT26_1M")
        );
        Assert.assertTrue(ex.getMessage().contains("internal one-sided"));
        Assert.assertTrue(ex.getMessage().contains("no public"));
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuType.fromProtocolId("PGT26-1M"));
    }

    @Test
    public void protocolRegistryRejectsPgt26_1m() {
        IllegalArgumentException ex = Assert.assertThrows(
            IllegalArgumentException.class,
            () -> ProtocolRegistry.findDescriptor("PGT26_1M")
        );
        Assert.assertTrue(ex.getMessage().contains("internal one-sided"));
    }

    @Test
    public void createConfigRejectsPgt26_1m() {
        Properties p = new Properties();
        p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "PGT26_1M");
        IllegalArgumentException ex = Assert.assertThrows(
            IllegalArgumentException.class,
            () -> PsuConfigUtils.createConfig(p)
        );
        Assert.assertTrue(ex.getMessage().contains("internal one-sided"));
    }

    @Test
    public void publicTwoSidedAliasStillResolves() {
        Assert.assertEquals(PsuType.EUROCRYPT_PuGaoTri26, PsuType.fromProtocolId("PGT26_2M"));
        Assert.assertEquals(PsuType.EUROCRYPT_PuGaoTri26, PsuType.fromProtocolId("EUROCRYPT:PuGaoTri26"));
        Assert.assertTrue(ProtocolRegistry.findDescriptor("PGT26_2M").isPresent());
    }
}
