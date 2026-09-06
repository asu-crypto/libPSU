package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import org.junit.Assert;
import org.junit.Test;

public class SmallEcElligatorPsuConfigTest {
    @Test
    public void defaultIsExactMode() {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder().build();
        Assert.assertEquals(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT, config.getWCompareMode());
        Assert.assertTrue(config.isAsyncPrecomputeW());
        Assert.assertFalse(config.isParallelEc());
        Assert.assertEquals(1024, config.getParallelThreshold());
    }

    @Test
    public void resolvedLambdaForFingerprintMode() {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder()
            .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
            .setFingerprintBitLength(0)
            .setStatisticalSecurityBits(40)
            .build();
        Assert.assertEquals(64, config.getResolvedFingerprintBitLength(32));
        Assert.assertEquals(96, config.getResolvedFingerprintBitLength(1 << 20));
    }

    @Test(expected = IllegalStateException.class)
    public void resolvedLambdaNotUsedInExactMode() {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder().build();
        config.getResolvedFingerprintBitLength(32);
    }
}
