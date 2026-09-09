package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PsuBlackIpConfigUtilsTest {
    @Test
    public void encodesCanonicalEndpoints() {
        ByteBuffer zero = PsuBlackIpConfigUtils.encodeIpv4("0.0.0.0");
        ByteBuffer ones = PsuBlackIpConfigUtils.encodeIpv4("255.255.255.255");
        ByteBuffer ordinary = PsuBlackIpConfigUtils.encodeIpv4("1.2.3.4");
        Assert.assertEquals(16, zero.remaining());
        Assert.assertEquals(16, ones.remaining());
        Assert.assertFalse(Arrays.equals(zero.array(), ones.array()));
        Assert.assertFalse(Arrays.equals(zero.array(), ordinary.array()));
        Assert.assertArrayEquals(
            Arrays.copyOf(PsuBlackIpConfigUtils.IPV4_DOMAIN_PREFIX, 12),
            Arrays.copyOf(ordinary.array(), 12)
        );
        Assert.assertEquals(1, ordinary.array()[12] & 0xFF);
        Assert.assertEquals(2, ordinary.array()[13] & 0xFF);
        Assert.assertEquals(3, ordinary.array()[14] & 0xFF);
        Assert.assertEquals(4, ordinary.array()[15] & 0xFF);
    }

    @Test
    public void rejectsInvalidOctets() {
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuBlackIpConfigUtils.encodeIpv4("256.0.0.1"));
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuBlackIpConfigUtils.encodeIpv4("-1.0.0.1"));
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuBlackIpConfigUtils.encodeIpv4("1.2.3"));
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuBlackIpConfigUtils.encodeIpv4("1.2.3.4.5"));
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuBlackIpConfigUtils.encodeIpv4("1..3.4"));
        Assert.assertThrows(IllegalArgumentException.class, () -> PsuBlackIpConfigUtils.encodeIpv4("a.b.c.d"));
    }

    @Test
    public void trimsWhitespaceAndAcceptsLeadingZeros() {
        ByteBuffer a = PsuBlackIpConfigUtils.encodeIpv4("  010.002.003.004  ");
        ByteBuffer b = PsuBlackIpConfigUtils.encodeIpv4("10.2.3.4");
        Assert.assertArrayEquals(a.array(), b.array());
    }
}
