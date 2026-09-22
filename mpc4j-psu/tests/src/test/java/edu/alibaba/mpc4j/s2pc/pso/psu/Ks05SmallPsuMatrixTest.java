package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.ks05.Ks05PsuConfig;

/**
 * Deterministic small-set matrix for Ks05 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Ks05SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Ks05SmallPsuMatrixTest() {
        super("Ks05SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Ks05PsuConfig.Builder().setMaxSetSize(32).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
