package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.PsuPaperFidelity;
import org.junit.Assert;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry coverage: every active {@link PsuType} must have a dedicated small-set matrix test class
 * (its own scenario list via {@link AbstractSmallPsuMatrixTest} or a richer protocol-specific suite).
 */
public class AllSupportedPsuSmallSetMatrixCoverageTest {

    private static final Map<PsuType, Class<?>> MATRIX_BY_TYPE = new EnumMap<>(PsuType.class);

    static {
        MATRIX_BY_TYPE.put(PsuType.AC_KRTW19, Krtw19SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.PKC_GMRSS21, Gmr21SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_JSZDG22, Jsz22SfcSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_JSZDG22_SFS, Jsz22SfsSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_ConYuWeiminDon23_PKE, Zcl23PkeSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_ConYuWeiminDon23_SKE, Zcl23SkeSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.PKC_CheZhaZha24, Czz24CwOprfSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.ASIACCS_CSSW25, Css25SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.EUROCRYPT_PisTri26, Pt26SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.ACISP_DavCid17, Dc17SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.ACNS_Frikken07, F07SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.C_KisSon05, Ks05SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.JOC_HazNis12, Hn12SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_BinYujConYanYu25, Tbz25SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_HaoWan26, HaoWan2026SmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.USENIX_YanShiHonDaw24, Jszg24BecrgSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.EUROCRYPT_PuGaoTri26, Pgt26_2mSmallPsuMatrixTest.class);
        MATRIX_BY_TYPE.put(PsuType.Ours, OursSmallPsuMatrixTest.class);
    }

    @Test
    public void everyActivePsuTypeHasDedicatedMatrixTest() {
        for (PsuType type : PsuType.values()) {
            if (PsuPaperFidelity.isRemovedProtocol(type) || PsuPaperFidelity.isReservedPaperExact(type)) {
                continue;
            }
            Class<?> matrix = MATRIX_BY_TYPE.get(type);
            Assert.assertNotNull("missing dedicated small-set matrix for " + type.protocolId(), matrix);
            Assert.assertTrue(
                matrix.getSimpleName() + " should be a *SmallPsuMatrixTest",
                matrix.getSimpleName().contains("SmallPsuMatrixTest")
            );
        }
        Assert.assertEquals(
            "matrix map must cover all active PsuType values",
            PsuType.values().length,
            MATRIX_BY_TYPE.size()
        );
    }

    @Test
    public void abstractScenarioListIsPresent() {
        // Sanity: shared harness exposes the WS15-required scenario methods.
        String[] required = {
            "equal_2_2_2",
            "disjoint_2_2_0",
            "withZeroElement",
            "withAllFfElement",
            "parallel_4_4_2",
            "twoExecutionsAfterOneInit",
        };
        for (String method : required) {
            try {
                AbstractSmallPsuMatrixTest.class.getMethod(method);
            } catch (NoSuchMethodException e) {
                Assert.fail("AbstractSmallPsuMatrixTest missing scenario: " + method);
            }
        }
    }
}
