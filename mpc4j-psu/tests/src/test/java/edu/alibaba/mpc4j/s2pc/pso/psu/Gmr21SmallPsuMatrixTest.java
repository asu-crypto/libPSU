package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;

/**
 * Deterministic small-set matrix for Gmr21 (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Gmr21SmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Gmr21SmallPsuMatrixTest() {
        super("Gmr21SmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Gmr21PsuConfig.Builder(false).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }

    @Override
    protected boolean supportsAllFfElement() {
        // AbstractMqRpmt still rejects all-FF as ⊥.
        return false;
    }
}
