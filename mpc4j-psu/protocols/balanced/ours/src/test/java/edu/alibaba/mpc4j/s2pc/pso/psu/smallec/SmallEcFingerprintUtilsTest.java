package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import org.junit.Assert;
import org.junit.Test;

public class SmallEcFingerprintUtilsTest {
    @Test
    public void autoFingerprintBitLength() {
        Assert.assertEquals(64, SmallEcElligatorPsuConfig.autoFingerprintBitLength(32, 40));
        Assert.assertEquals(96, SmallEcElligatorPsuConfig.autoFingerprintBitLength(1 << 20, 40));
        Assert.assertEquals(64, SmallEcFingerprintUtils.autoFingerprintBitLength(32, 40));
        Assert.assertEquals(96, SmallEcFingerprintUtils.autoFingerprintBitLength(1 << 20, 40));
    }

    @Test
    public void ceilLog2() {
        Assert.assertEquals(0, SmallEcElligatorPsuConfig.ceilLog2(1));
        Assert.assertEquals(5, SmallEcElligatorPsuConfig.ceilLog2(32));
        Assert.assertEquals(20, SmallEcElligatorPsuConfig.ceilLog2(1 << 20));
    }

    @Test
    public void fingerprintByteLength() {
        Assert.assertEquals(8, SmallEcFingerprintUtils.fingerprintByteLength(64));
        Assert.assertEquals(12, SmallEcFingerprintUtils.fingerprintByteLength(96));
        Assert.assertEquals(16, SmallEcFingerprintUtils.fingerprintByteLength(128));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidFingerprintBits() {
        SmallEcFingerprintUtils.fingerprintByteLength(80);
    }
}
