package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfcPsuConfig;

/**
 * Deterministic small-set matrix for Jsz22Sfc (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Jsz22SfcSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Jsz22SfcSmallPsuMatrixTest() {
        super("Jsz22SfcSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Jsz22SfcPsuConfig.Builder(false).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
