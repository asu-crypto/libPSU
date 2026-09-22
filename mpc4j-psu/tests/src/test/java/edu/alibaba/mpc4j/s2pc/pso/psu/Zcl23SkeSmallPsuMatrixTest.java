package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuConfig;

/**
 * Deterministic small-set matrix for Zcl23Ske (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class Zcl23SkeSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public Zcl23SkeSmallPsuMatrixTest() {
        super("Zcl23SkeSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new Zcl23SkePsuConfig.Builder(edu.alibaba.mpc4j.common.rpc.desc.SecurityModel.SEMI_HONEST, true).build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }
}
