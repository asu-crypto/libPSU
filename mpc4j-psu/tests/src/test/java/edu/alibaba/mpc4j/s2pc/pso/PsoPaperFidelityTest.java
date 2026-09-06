package edu.alibaba.mpc4j.s2pc.pso;

import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiFactory.CcpsiType;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Paper-fidelity labelling tests. Distinguishes runnable proxies from unsupported paper-exact modes.
 */
public class PsoPaperFidelityTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int CSS25_PACKING_TEST_SIZE = 1 << 20;

    public PsoPaperFidelityTest() {
        super("PSO_PAPER_FIDELITY");
    }

    @Test
    public void gmr21ResolvesToOriginalType() {
        Assert.assertEquals(PsuType.PKC_GMRSS21, PsuPaperFidelity.resolvePsuType("PKC_GMRSS21"));
        Assert.assertFalse(PsuPaperFidelity.isProxyOrInspiredName("PKC_GMRSS21"));
    }

    @Test
    public void css25ResolvesToRunnableComparisonType() {
        Assert.assertEquals(PsuType.ASIACCS_CSSW25, PsuPaperFidelity.resolvePsuType("ASIACCS_CSSW25"));
        Assert.assertTrue(PsuPaperFidelity.isProxyOrInspired(PsuType.ASIACCS_CSSW25));
        Assert.assertTrue(PsuPaperFidelity.isProxyOrInspiredName("ASIACCS_CSSW25"));
    }

    @Test
    public void gmr21ConfigReportsOriginalType() {
        Assert.assertEquals(PsuType.PKC_GMRSS21, new Gmr21PsuConfig.Builder(false).build().getPtoType());
    }

    @Test
    public void css25DefaultIsPsty19RunnableProxy() {
        Css25PsuConfig config = new Css25PsuConfig.Builder(true).build();
        Assert.assertEquals(Css25PsuConfig.Css25Mode.PROXY, config.getMode());
        Assert.assertFalse(config.isPaperComparisonProxy());
        Assert.assertFalse(config.isPaperExact());
        Assert.assertEquals(CcpsiType.PSTY19, config.getCcpsiConfig().getPtoType());
    }

    @Test
    public void css25PaperExactIsUnsupportedAtConstruction() {
        Css25PsuConfig config = new Css25PsuConfig.Builder(true).setPaperExact().build();
        Assert.assertTrue(config.isPaperExact());
        Assert.assertTrue(PsuPaperFidelity.isReservedPaperExact(config));
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config)
        );
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config)
        );
    }

    @Test
    public void css25ConfigParsingCreatesRunnableProxyConfig() {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.ASIACCS_CSSW25.name());
        properties.setProperty("silent_cot", "true");
        Css25PsuConfig config = (Css25PsuConfig) PsuConfigUtils.createConfig(properties);
        Assert.assertEquals(PsuType.ASIACCS_CSSW25, config.getPtoType());
        Assert.assertEquals(Css25PsuConfig.Css25Mode.PROXY, config.getMode());
        Assert.assertFalse(config.isPaperComparisonProxy());
        Assert.assertFalse(config.isPaperExact());
        Assert.assertEquals(CcpsiType.PSTY19, config.getCcpsiConfig().getPtoType());
        assertCss25PaperCuckooPacking(config);
    }

    @Test
    public void css25ExplicitPaperComparisonConfigParsingCreatesRs21Config() {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.ASIACCS_CSSW25.name());
        properties.setProperty("silent_cot", "true");
        properties.setProperty(PsuConfigUtils.CSS25_PAPER_COMPARISON_KEY, "true");
        Css25PsuConfig config = (Css25PsuConfig) PsuConfigUtils.createConfig(properties);
        Assert.assertEquals(PsuType.ASIACCS_CSSW25, config.getPtoType());
        Assert.assertEquals(Css25PsuConfig.Css25Mode.PAPER_COMPARISON_PROXY, config.getMode());
        Assert.assertTrue(config.isPaperComparisonProxy());
        Assert.assertFalse(config.isPaperExact());
        Assert.assertEquals(CcpsiType.RS21, config.getCcpsiConfig().getPtoType());
        assertCss25PaperCuckooPacking(config);
    }

    @Test
    public void css25DeprecatedRs21AblationFlagStillCreatesComparisonConfig() {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.ASIACCS_CSSW25.name());
        properties.setProperty("silent_cot", "true");
        properties.setProperty(PsuConfigUtils.CSS25_RS21_PROXY_ABLATION_KEY, "true");
        Css25PsuConfig config = (Css25PsuConfig) PsuConfigUtils.createConfig(properties);
        Assert.assertTrue(config.isPaperComparisonProxy());
        Assert.assertEquals(CcpsiType.RS21, config.getCcpsiConfig().getPtoType());
        assertCss25PaperCuckooPacking(config);
    }

    @Test
    public void css25CcpsiOverridePreservesPaperCuckooPacking() {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.ASIACCS_CSSW25.name());
        properties.setProperty("silent_cot", "true");
        properties.setProperty("ccpsi_pto_name", CcpsiType.RS21.name());
        Css25PsuConfig config = (Css25PsuConfig) PsuConfigUtils.createConfig(properties);
        Assert.assertEquals(CcpsiType.RS21, config.getCcpsiConfig().getPtoType());
        assertCss25PaperCuckooPacking(config);
    }

    @Test
    public void css25PaperComparisonRejectsCcpsiOverride() {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.ASIACCS_CSSW25.name());
        properties.setProperty("silent_cot", "true");
        properties.setProperty(PsuConfigUtils.CSS25_PAPER_COMPARISON_KEY, "true");
        properties.setProperty("ccpsi_pto_name", CcpsiType.PSTY19.name());
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuConfigUtils.createConfig(properties));
    }

    @Test
    public void css25ExplicitPaperExactConfigParsingStillMarkedExact() {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.ASIACCS_CSSW25.name());
        properties.setProperty("silent_cot", "true");
        properties.setProperty(PsuConfigUtils.CSS25_PAPER_EXACT_KEY, "true");
        Css25PsuConfig config = (Css25PsuConfig) PsuConfigUtils.createConfig(properties);
        Assert.assertTrue(config.isPaperExact());
        Assert.assertTrue(PsuPaperFidelity.isReservedPaperExact(config));
    }

    @Test
    public void css25PaperComparisonConfigReportsCss25Type() {
        Assert.assertEquals(
            PsuType.ASIACCS_CSSW25,
            new Css25PsuConfig.Builder(true)
                .setPaperComparisonProxy()
                .build()
                .getPtoType()
        );
    }

    @Test
    public void pgt26TwoSidedIsPubliclySupported() {
        Assert.assertEquals(
            PsuType.EUROCRYPT_PuGaoTri26,
            PsuPaperFidelity.resolvePsuType("EUROCRYPT:PuGaoTri26")
        );
        Assert.assertFalse(PsuPaperFidelity.isRemovedProtocol(PsuType.EUROCRYPT_PuGaoTri26));
    }

    @Test
    public void unknownProtocolNamesRejected() {
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuPaperFidelity.resolvePsuType("NOT_A_REAL_PSU"));
    }

    private static void assertCss25PaperCuckooPacking(Css25PsuConfig config) {
        Assert.assertEquals(
            (int) Math.ceil(1.4 * CSS25_PACKING_TEST_SIZE),
            config.getCcpsiConfig().getOutputBitNum(CSS25_PACKING_TEST_SIZE, CSS25_PACKING_TEST_SIZE)
        );
    }
}
