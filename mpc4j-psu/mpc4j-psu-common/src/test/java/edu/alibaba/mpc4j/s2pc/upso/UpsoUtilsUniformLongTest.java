package edu.alibaba.mpc4j.s2pc.upso;

import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * Deterministic range tests for {@link UpsoUtils#uniformLong}.
 */
public class UpsoUtilsUniformLongTest {
    @Test
    public void rejectsNonPositiveBound() {
        SecureRandom rnd = new SecureRandom();
        Assert.assertThrows(IllegalArgumentException.class, () -> UpsoUtils.uniformLong(rnd, 0));
        Assert.assertThrows(IllegalArgumentException.class, () -> UpsoUtils.uniformLong(rnd, -1));
    }

    @Test
    public void samplesInRange() {
        SecureRandom rnd = new SecureRandom();
        rnd.setSeed(0x5EED5EED5EED5EEDL);
        long[] bounds = {1L, 2L, 3L, 7L, 257L, (1L << 20) + 3L};
        for (long bound : bounds) {
            for (int i = 0; i < 256; i++) {
                long v = UpsoUtils.uniformLong(rnd, bound);
                Assert.assertTrue("v=" + v + " bound=" + bound, v >= 0L && v < bound);
            }
        }
    }
}
