package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuConfig;

/**
 * Deterministic small-set matrix for F07 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class F07SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public F07SmallPsuMatrixTest() {
        super("F07SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new F07PsuConfig.Builder().build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
