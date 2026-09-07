package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf;

import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Deterministic fixed-width scalar encoding for {@link Hn12IdealPrf}.
 */
public class Hn12IdealPrfTest {
    @Test
    public void encodeScalarZero() {
        byte[] enc = Hn12IdealPrf.encodeScalar(BigInteger.ZERO);
        Assert.assertEquals(32, enc.length);
        Assert.assertArrayEquals(new byte[32], enc);
    }

    @Test
    public void encodeScalarOne() {
        byte[] enc = Hn12IdealPrf.encodeScalar(BigInteger.ONE);
        Assert.assertEquals(32, enc.length);
        byte[] expect = new byte[32];
        expect[31] = 1;
        Assert.assertArrayEquals(expect, enc);
    }

    @Test
    public void encodeScalar31BytesRightAligned() {
        byte[] raw31 = new byte[31];
        Arrays.fill(raw31, (byte) 0xAB);
        BigInteger s = new BigInteger(1, raw31);
        byte[] enc = Hn12IdealPrf.encodeScalar(s);
        Assert.assertEquals(32, enc.length);
        Assert.assertEquals(0, enc[0]);
        for (int i = 0; i < 31; i++) {
            Assert.assertEquals((byte) 0xAB, enc[i + 1]);
        }
    }

    @Test
    public void encodeScalarFull32Bytes() {
        byte[] raw32 = new byte[32];
        Arrays.fill(raw32, (byte) 0x7F);
        BigInteger s = new BigInteger(1, raw32);
        byte[] enc = Hn12IdealPrf.encodeScalar(s);
        Assert.assertEquals(32, enc.length);
        Assert.assertArrayEquals(raw32, enc);
    }

    @Test
    public void encodeScalarStripsLeadingSignByte() {
        // Value whose MSB is set → BigInteger.toByteArray() prefixes 0x00.
        byte[] magnitude = new byte[32];
        Arrays.fill(magnitude, (byte) 0xFF);
        BigInteger s = new BigInteger(1, magnitude);
        byte[] twos = s.toByteArray();
        Assert.assertTrue("expected leading sign byte", twos.length == 33 && twos[0] == 0);
        byte[] enc = Hn12IdealPrf.encodeScalar(s);
        Assert.assertEquals(32, enc.length);
        Assert.assertArrayEquals(magnitude, enc);
    }

    @Test
    public void encodeScalarRejectsNegativeAndOversized() {
        Assert.assertThrows(IllegalArgumentException.class, () -> Hn12IdealPrf.encodeScalar(BigInteger.valueOf(-1)));
        Assert.assertThrows(
            IllegalArgumentException.class,
            () -> Hn12IdealPrf.encodeScalar(BigInteger.ONE.shiftLeft(256))
        );
    }
}
