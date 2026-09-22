package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;

/**
 * Deterministic small-set matrix for Ours (shared AbstractSmallPsuMatrixTest scenario list).
 */
public class OursSmallPsuMatrixTest extends AbstractSmallPsuMatrixTest {
    public OursSmallPsuMatrixTest() {
        super("OursSmallPsuMatrixTest");
    }

    @Override
    protected PsuConfig createConfig() {
        return new SmallEcElligatorPsuConfig.Builder().build();
    }

    @Override
    protected boolean isTwoSided() {
        return false;
    }

    @Override
    protected int elementByteLength() {
        return SmallEcConstants.ITEM_BYTE_LENGTH;
    }

    @Override
    protected boolean supportsAsymmetricSizes() {
        return false;
    }

    @Override
    protected boolean supportsDeterministicDomainEncoding() {
        // Elligator map rejects some DeterministicPsuSets domain encodings.
        return false;
    }

    @Override
    protected boolean supportsAllFfElement() {
        return false;
    }
}
