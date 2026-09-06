package edu.alibaba.libpsu.factory;

import edu.alibaba.libpsu.api.ProtocolMetadataRegistry;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Smoke: SoK metadata covers every fair-bench PSU name.
 */
public class LibPsuMetadataSmokeTest {
    private static final String[] BENCH_PSU_NAMES = {
        "AC_KRTW19", "PKC_GMRSS21", "USENIX_JSZDG22", "USENIX_JSZDG22_SFS", "PKC_CheZhaZha24",
        "USENIX_ConYuWeiminDon23_SKE", "USENIX_ConYuWeiminDon23_PKE", "USENIX_YanShiHonDaw24", "C_KisSon05", "ACNS_Frikken07", "JOC_HazNis12",
        "BA12", "ACISP_DavCid17", "EUROCRYPT_PisTri26", "EUROCRYPT_PuGaoTri26",
        "Ours",
    };

    @Test
    public void metadataCoversBenchmarkNames() {
        for (String name : BENCH_PSU_NAMES) {
            if ("C_KisSon05".equals(name) || "JOC_HazNis12".equals(name) || "BA12".equals(name)) {
                continue;
            }
            if ("TCL23".equals(name)) {
                continue;
            }
            Assert.assertTrue("missing metadata: " + name, ProtocolMetadataRegistry.isKnownBenchmarkName(name));
        }
        Assert.assertTrue(ProtocolMetadataRegistry.isKnownBenchmarkName("C_KisSon05"));
        Assert.assertTrue(ProtocolMetadataRegistry.isKnownBenchmarkName("JOC_HazNis12"));
        Assert.assertTrue(ProtocolMetadataRegistry.isKnownBenchmarkName("BA12"));
        Assert.assertTrue(ProtocolMetadataRegistry.isKnownBenchmarkName("TCL23"));
    }

    @Test
    public void pgt26_2mConfigNameResolves() {
        Properties p = new Properties();
        p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "EUROCRYPT_PuGaoTri26");
        p.setProperty("silent_cot", "true");
        p.setProperty("compress_encode", "true");
        Assert.assertEquals(PsuType.EUROCRYPT_PuGaoTri26, ProtocolRegistry.resolvePsuType("EUROCRYPT_PuGaoTri26"));
    }
}
