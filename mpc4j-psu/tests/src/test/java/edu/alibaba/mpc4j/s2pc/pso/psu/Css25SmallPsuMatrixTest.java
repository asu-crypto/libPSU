package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuConfig;

/**
 * Deterministic small-set matrix for Css25 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Css25SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Css25SmallPsuMatrixTest() {
        super("Css25SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Css25PsuConfig.Builder(false).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
