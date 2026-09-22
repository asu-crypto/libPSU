package edu.alibaba.libpsu.api;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Unit-level invariants shared by every public libPSU protocol. */
public class AllProtocolMetadataUnitTest {
    private static final Set<String> EXPECTED_KEYS = Set.of(
        "AC:KRTW19|PSU", "PKC:GMRSS21|PSU", "USENIX:JSZDG22|PSU",
        "USENIX:JSZDG22_SFS|PSU", "USENIX:ConYuWeiminDon23_PKE|PSU",
        "USENIX:ConYuWeiminDon23_SKE|PSU", "PKC:CheZhaZha24|PSU",
        "ASIACCS:CSSW25|PSU", "EUROCRYPT:PisTri26|PSU", "ACISP:DavCid17|PSU",
        "ACNS:Frikken07|PSU", "C:KisSon05|PSU", "JOC:HazNis12|PSU",
        "USENIX:BinYujConYanYu25|PSU", "USENIX:HaoWan26|PSU",
        "USENIX:YanShiHonDaw24|PSU", "EUROCRYPT:PuGaoTri26|PSU", "Ours|PSU",
        "CCS:TCLZ23|UPSU", "USENIX:BinYujConYanYu25|UPSU",
        "ASIACCS:BlaAgu12|BA12"
    );

    @Test
    public void testEveryPublicProtocolIsRegisteredExactlyOnce() {
        Set<String> actual = ProtocolMetadataRegistry.allEntries().stream()
            .map(info -> info.getProtocolName() + "|" + info.getFunctionality())
            .collect(Collectors.toSet());
        Assert.assertEquals("Update the all-protocol test matrix when adding a protocol", EXPECTED_KEYS, actual);
        Assert.assertEquals(actual.size(), ProtocolMetadataRegistry.allEntries().size());
    }

    @Test
    public void testEveryProtocolHasCompleteOperationalMetadata() {
        Set<String> modules = new HashSet<>();
        for (ProtocolInfo info : ProtocolMetadataRegistry.allEntries()) {
            Assert.assertFalse(info.getProtocolName(), info.getCitationKey().isBlank());
            Assert.assertTrue(info.getProtocolName(), info.getYear() >= 2000);
            Assert.assertTrue(info.getProtocolName(), info.getMinimumInputSetSize() >= 1);
            Assert.assertNotNull(info.getProtocolName(), info.getOutputModel());
            Assert.assertNotNull(info.getProtocolName(), info.getSecurityModel());
            Assert.assertNotNull(info.getProtocolName(), info.getSetSizeMode());
            Assert.assertNotNull(info.getProtocolName(), info.getPrimitiveKind());
            Assert.assertNotNull(info.getProtocolName(), info.getReadiness());
            Assert.assertFalse(info.getProtocolName(), info.getMavenModule().isBlank());
            Assert.assertFalse(info.getProtocolName(), info.getConfigPropertyName().isBlank());
            modules.add(info.getMavenModule());
        }
        Assert.assertFalse(modules.isEmpty());
    }
}
