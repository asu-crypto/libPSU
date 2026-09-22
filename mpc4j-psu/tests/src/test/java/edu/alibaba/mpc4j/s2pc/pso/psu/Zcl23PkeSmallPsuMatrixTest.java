package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23PkePsuConfig;

/**
 * Deterministic small-set matrix for Zcl23Pke (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Zcl23PkeSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Zcl23PkeSmallPsuMatrixTest() {
        super("Zcl23PkeSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Zcl23PkePsuConfig.Builder().build();
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
