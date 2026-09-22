package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuConfig;

/**
 * Deterministic small-set matrix for Krtw19 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Krtw19SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Krtw19SmallPsuMatrixTest() {
        super("Krtw19SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Krtw19PsuConfig.Builder().build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
