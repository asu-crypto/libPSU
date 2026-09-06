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
        "AC:KRTW19", "PKC:GMRSS21", "USENIX:JSZDG22", "USENIX:JSZDG22_SFS", "PKC:CheZhaZha24",
        "USENIX:ConYuWeiminDon23_SKE", "USENIX:ConYuWeiminDon23_PKE", "USENIX:YanShiHonDaw24",
        "C:KisSon05", "ACNS:Frikken07", "JOC:HazNis12",
        "ACISP:DavCid17", "EUROCRYPT:PisTri26", "EUROCRYPT:PuGaoTri26",
        "Ours",
    };

    @Test
    public void metadataCoversBenchmarkNames() {
        for (String name : BENCH_PSU_NAMES) {
            Assert.assertTrue("missing metadata: " + name, ProtocolMetadataRegistry.isKnownBenchmarkName(name));
            Assert.assertTrue("missing PSU descriptor: " + name, ProtocolRegistry.findDescriptor(name).isPresent());
        }
        Assert.assertTrue(ProtocolMetadataRegistry.isKnownBenchmarkName("BA12"));
        Assert.assertTrue(ProtocolMetadataRegistry.isKnownBenchmarkName("TCL23"));
    }

    @Test
    public void pgt26_2mConfigNameResolves() {
        Properties p = new Properties();
        p.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, "EUROCRYPT:PuGaoTri26");
        p.setProperty("silent_cot", "true");
        p.setProperty("compress_encode", "true");
        Assert.assertEquals(PsuType.EUROCRYPT_PuGaoTri26, ProtocolRegistry.resolvePsuType("EUROCRYPT:PuGaoTri26"));
    }
}
