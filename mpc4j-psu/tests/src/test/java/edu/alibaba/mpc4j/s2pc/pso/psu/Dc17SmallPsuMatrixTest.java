package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig;

/**
 * Deterministic small-set matrix for Dc17 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Dc17SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Dc17SmallPsuMatrixTest() {
        super("Dc17SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Dc17PsuConfig.Builder().build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
