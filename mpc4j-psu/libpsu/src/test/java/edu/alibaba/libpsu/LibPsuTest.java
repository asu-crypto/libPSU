package edu.alibaba.libpsu;

import edu.alibaba.libpsu.api.ProtocolFunctionality;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Operation;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23EccDdhPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiType;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuType;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class LibPsuTest {
    @Test
    public void testSingleEntryPointSeesEveryBalancedProtocol() {
        long metadataCount = LibPsu.protocols().stream()
            .filter(info -> info.getFunctionality() == ProtocolFunctionality.PSU)
            .count();
        Assert.assertEquals(PsuType.values().length, metadataCount);
        Assert.assertEquals(PsuType.values().length, LibPsu.psuProtocols().size());
    }

    @Test
    public void testSingleEntryPointCreatesConfig() {
        Assert.assertEquals(PsuType.AC_KRTW19, LibPsu.createPsuConfig("AC:KRTW19").getPtoType());
    }

    @Test
    public void testMetadataDistinguishesSharedProtocolNames() {
        String name = "USENIX:BinYujConYanYu25";
        Assert.assertEquals(ProtocolFunctionality.PSU,
            LibPsu.findProtocol(name, ProtocolFunctionality.PSU).orElseThrow().getFunctionality());
        Assert.assertEquals(ProtocolFunctionality.UPSU,
            LibPsu.findProtocol(name, ProtocolFunctionality.UPSU).orElseThrow().getFunctionality());
        Assert.assertEquals(UpsuType.values().length, LibPsu.protocols(ProtocolFunctionality.UPSU).size());
        Assert.assertFalse(LibPsu.findProtocol("unknown", ProtocolFunctionality.PSU).isPresent());
    }

    @Test
    public void testEveryPsiAndUpsuProtocolHasPublicConfigCreation() {
        for (PsiType type : PsiType.values()) {
            Assert.assertEquals(type, LibPsu.createPsiConfig(type.protocolId()).getPtoType());
        }
        for (UpsuType type : UpsuType.values()) {
            Assert.assertEquals(type, LibPsu.createUpsuConfig(type.protocolId()).getPtoType());
        }
    }

    @Test
    public void testNamedConfigPreservesDefaultsWithoutMutatingCaller() {
        Properties defaults = new Properties();
        defaults.setProperty("ks05_max_set_size", "32");
        Properties options = new Properties(defaults);
        options.setProperty("psi_pto_name", "JOC:HazNis12");
        Ks05PsiConfig config = (Ks05PsiConfig) LibPsu.createPsiConfig("C:KisSon05", options);
        Assert.assertEquals(32, config.getMaxSetSize());
        Assert.assertEquals("JOC:HazNis12", options.getProperty("psi_pto_name"));
        Assert.assertEquals(1, options.size());
    }

    @Test
    public void testUpsuSelectorRetainsVariantAndLegacyKey() {
        Tcl23UpsuConfig variant = (Tcl23UpsuConfig) LibPsu.createUpsuConfig("TCL23_ECC_DDH");
        Assert.assertTrue(variant.getPmPeqtConfig() instanceof Tcl23EccDdhPmPeqtConfig);
        Properties legacy = new Properties();
        legacy.setProperty("psu_pto_name", "CCS:TCLZ23");
        Assert.assertEquals(UpsuType.CCS_TCLZ23, LibPsu.createUpsuConfig(legacy).getPtoType());
        Assert.assertEquals(UpsuType.USENIX_BinYujConYanYu25,
            LibPsu.createUpsuConfig("USENIX:BinYujConYanYu25", legacy).getPtoType());
        Assert.assertFalse(legacy.containsKey("upsu_pto_name"));
    }

    @Test
    public void testPropertiesAndOfflineOnlineConfigEntryPoints() {
        Properties options = new Properties();
        options.setProperty("psu_pto_name", "PKC:GMRSS21");
        options.setProperty("rosn_type", "GMR21_NET");
        options.setProperty("silent_cot", "true");
        Assert.assertEquals(PsuType.PKC_GMRSS21, LibPsu.createPsuConfig(options).getPtoType());
        Assert.assertEquals(PsuType.PKC_GMRSS21, LibPsu.createOoPsuConfig(options).getPtoType());
    }

    @Test
    public void testBa12IsAvailableThroughPublicArtifact() {
        Assert.assertEquals(32, LibPsu.createBa12Config(new Properties(), 4).getEll());
        Assert.assertEquals(Ba12Operation.BA12_UNION, LibPsu.readBa12Operation(new Properties()));
        Assert.assertEquals("BA12", LibPsu.readBa12ProtocolName(new Properties()));
        Properties options = new Properties();
        options.setProperty("ba12_operation", "BA12_INTERSECTION");
        Assert.assertEquals(Ba12Operation.BA12_INTERSECTION, LibPsu.readBa12Operation(options));
        options.setProperty("ba12_operation", "ASIACCS:BlaAgu12_UNION");
        Assert.assertEquals(Ba12Operation.BA12_UNION, LibPsu.readBa12Operation(options));
    }

    @Test
    public void testPublicFactoryPreservesOutputDisclosureGuard() {
        PsuConfig twoSided = LibPsu.createDefaultTwoSidedConfig(SecurityModel.MALICIOUS);
        Assert.assertTrue(LibPsu.usesTwoSidedPublicFactory(twoSided));
        Assert.assertTrue(LibPsu.capabilitiesOf(twoSided).isTwoSided());
        Assert.assertThrows(IllegalArgumentException.class, () -> LibPsu.createServer(null, null, twoSided));
        Assert.assertThrows(IllegalArgumentException.class, () -> LibPsu.createClient(null, null, twoSided));
        PsuConfig oneSided = LibPsu.createPsuConfig("AC:KRTW19");
        Assert.assertFalse(LibPsu.usesTwoSidedPublicFactory(oneSided));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> LibPsu.createTwoSidedServer(null, null, oneSided));
        Assert.assertThrows(IllegalArgumentException.class,
            () -> LibPsu.createTwoSidedClient(null, null, oneSided));
    }
}
