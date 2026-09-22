package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig;

/**
 * Deterministic small-set matrix for Jszg24Becrg (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Jszg24BecrgSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Jszg24BecrgSmallPsuMatrixTest() {
        super("Jszg24BecrgSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Jszg24BecrgPsuConfig.Builder(false).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
