package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class SmallEcElligatorPsuBenchConfigFactoryTest {
    @Test
    public void case6EnablesFingerprint64Async() {
        Properties p = new Properties();
        p.setProperty("append_string", "small_ec_bench_2p5_case6_fp64_async_no_parallel");
        p.setProperty("small_ec_log_stats", "true");
        SmallEcElligatorPsuConfig config = SmallEcElligatorPsuBenchConfigFactory.createForProperties(p);
        Assert.assertNotNull(config);
        Assert.assertEquals(
            SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC, config.getWCompareMode()
        );
        Assert.assertEquals(64, config.getFingerprintBitLength());
        Assert.assertTrue(config.isAsyncPrecomputeW());
        Assert.assertFalse(config.isParallelEc());
    }

    @Test
    public void case1DisablesAsync() {
        Properties p = new Properties();
        p.setProperty("append_string", "small_ec_bench_2p5_case1_exact_no_async_no_parallel");
        SmallEcElligatorPsuConfig config = SmallEcElligatorPsuBenchConfigFactory.createForProperties(p);
        Assert.assertNotNull(config);
        Assert.assertEquals(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT, config.getWCompareMode());
        Assert.assertFalse(config.isAsyncPrecomputeW());
        Assert.assertFalse(config.isParallelEc());
    }

    @Test
    public void case8AutoFingerprint() {
        Properties p = new Properties();
        p.setProperty("append_string", "small_ec_bench_2p5_case8_fp_auto_async_parallel");
        SmallEcElligatorPsuConfig config = SmallEcElligatorPsuBenchConfigFactory.createForProperties(p);
        Assert.assertNotNull(config);
        Assert.assertEquals(
            SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC, config.getWCompareMode()
        );
        Assert.assertEquals(0, config.getFingerprintBitLength());
        Assert.assertEquals(64, config.getResolvedFingerprintBitLength(32));
    }
}
