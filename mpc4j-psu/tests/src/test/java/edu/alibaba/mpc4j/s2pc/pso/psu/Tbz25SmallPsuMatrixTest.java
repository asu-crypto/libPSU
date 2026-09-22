package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig;

/**
 * Deterministic small-set matrix for Tbz25 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Tbz25SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Tbz25SmallPsuMatrixTest() {
        super("Tbz25SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Tbz25PsuConfig.Builder(false).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
