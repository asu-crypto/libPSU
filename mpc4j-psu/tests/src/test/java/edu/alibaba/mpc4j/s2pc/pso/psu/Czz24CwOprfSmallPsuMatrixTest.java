package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuConfig;

/**
 * Deterministic small-set matrix for Czz24CwOprf (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Czz24CwOprfSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Czz24CwOprfSmallPsuMatrixTest() {
        super("Czz24CwOprfSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Czz24CwOprfPsuConfig.Builder().build();
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
