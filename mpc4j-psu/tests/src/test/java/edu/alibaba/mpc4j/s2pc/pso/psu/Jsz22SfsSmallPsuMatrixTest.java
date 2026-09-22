package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfsPsuConfig;

/**
 * Deterministic small-set matrix for Jsz22Sfs (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Jsz22SfsSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Jsz22SfsSmallPsuMatrixTest() {
        super("Jsz22SfsSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Jsz22SfsPsuConfig.Builder(false).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
