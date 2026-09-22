package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuConfig;

/**
 * Deterministic small-set matrix for Hn12 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Hn12SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Hn12SmallPsuMatrixTest() {
        super("Hn12SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Hn12PsuConfig.Builder().build();
    }

    @Override
    protected boolean isTwoSided() {
        return true;
    }
}
