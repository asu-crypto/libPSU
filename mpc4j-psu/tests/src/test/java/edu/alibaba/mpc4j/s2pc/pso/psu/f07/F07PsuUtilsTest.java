package edu.alibaba.mpc4j.s2pc.pso.psu.f07;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Tests for ACNS_Frikken07 protocol arithmetic helpers.
 */
public class F07PsuUtilsTest {
    @Test
    public void testElementBytesUsesRemainingSlice() {
        byte[] backing = new byte[]{9, 1, 2, 3, 4, 8};
        ByteBuffer sliced = ByteBuffer.wrap(backing);
        sliced.position(1);
        sliced.limit(5);

        byte[] elementBytes = F07PsuUtils.elementBytes(sliced);

        Assert.assertArrayEquals(new byte[]{1, 2, 3, 4}, elementBytes);
        Assert.assertEquals(new BigInteger(1, elementBytes), F07PsuUtils.elementToInteger(elementBytes));
    }

    @Test
    public void testSampleInvertibleScalar() {
        BigInteger modulus = BigInteger.valueOf(15);
        Random random = new Random(0L);
        for (int i = 0; i < 32; i++) {
            BigInteger r = F07PsuUtils.sampleInvertibleScalar(modulus, random);
            Assert.assertTrue(r.signum() > 0);
            Assert.assertEquals(BigInteger.ONE, r.gcd(modulus));
        }
    }

    @Test
    public void testRecoverServerElement() {
        BigInteger modulus = BigInteger.valueOf(101);
        BigInteger s = BigInteger.valueOf(37);
        BigInteger y = BigInteger.valueOf(73);
        BigInteger x = s.multiply(y).mod(modulus);

        Assert.assertEquals(s, F07PsuUtils.recoverServerElement(x, y, modulus));
        Assert.assertNull(F07PsuUtils.recoverServerElement(BigInteger.ZERO, BigInteger.ZERO, modulus));
    }
}
