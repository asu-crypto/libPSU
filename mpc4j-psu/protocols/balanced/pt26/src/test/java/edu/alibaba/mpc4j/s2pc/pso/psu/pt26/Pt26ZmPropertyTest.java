package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * Property tests for power-of-two {@code Z_M} and tagged BOT wire encoding.
 */
public class Pt26ZmPropertyTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void encodeDecodeRoundTrip() {
        Pt26IbltParams params = params(8);
        for (int i = 0; i < 64; i++) {
            byte[] raw = new byte[8];
            RANDOM.nextBytes(raw);
            ByteBuffer el = ByteBuffer.wrap(raw);
            byte[] enc = Pt26Zm.encodeElement(el, params);
            Assert.assertEquals(el, Pt26Zm.decodeElement(enc, 8, params));
            Assert.assertTrue(Pt26Zm.isValidEncoding(enc, el, params));
        }
    }

    @Test
    public void addSubtractInverse() {
        Pt26IbltParams params = params(8);
        for (int i = 0; i < 64; i++) {
            byte[] a = randomZm(params);
            byte[] b = randomZm(params);
            byte[] sum = Pt26Zm.add(a, b, params);
            Assert.assertArrayEquals(a, Pt26Zm.subtract(sum, b, params));
            Assert.assertArrayEquals(a, Pt26Zm.add(a, Pt26Zm.zero(params), params));
        }
    }

    @Test
    public void wraparoundAtPowerOfTwoBoundary() {
        Pt26IbltParams params = params(8);
        int L = params.getZmByteLength();
        BigInteger M = params.modulus();
        BigInteger B = BigInteger.ONE.shiftLeft(8);
        // Low-byte boundary inside Z_M: (B-1)+1 = B (not truncated to 0).
        byte[] almostB = Pt26Zm.toFixedLength(B.subtract(BigInteger.ONE), L, M);
        byte[] one = Pt26Zm.toFixedLength(BigInteger.ONE, L, M);
        byte[] bVal = Pt26Zm.toFixedLength(B, L, M);
        Assert.assertArrayEquals(bVal, Pt26Zm.add(almostB, one, params));
        // Full ring wrap: (M-1)+1 = 0.
        byte[] almostM = Pt26Zm.toFixedLength(M.subtract(BigInteger.ONE), L, M);
        Assert.assertArrayEquals(Pt26Zm.zero(params), Pt26Zm.add(almostM, one, params));
        Assert.assertArrayEquals(almostM, Pt26Zm.subtract(Pt26Zm.zero(params), one, params));
    }

    @Test
    public void taggedBotAndValue() {
        Pt26IbltParams params = params(8);
        byte[] bot = Pt26Zm.encodeWireBot(params);
        Assert.assertTrue(Pt26Zm.decodeWire(bot, params).isEmpty());
        byte[] zm = randomZm(params);
        byte[] wire = Pt26Zm.encodeWireValue(zm, params);
        Optional<byte[]> got = Pt26Zm.decodeWire(wire, params);
        Assert.assertTrue(got.isPresent());
        Assert.assertArrayEquals(zm, got.get());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectWrongWireLength() {
        Pt26Zm.decodeWire(new byte[3], 8);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectUnknownTag() {
        byte[] wire = new byte[9];
        wire[0] = 2;
        Pt26Zm.decodeWire(wire, 8);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectNoncanonicalZmLength() {
        Pt26IbltParams params = params(8);
        Pt26Zm.fromFixedLength(new byte[params.getZmByteLength() + 1], params);
    }

    @Test
    public void singletonEncodingsInjective() {
        Pt26IbltParams params = params(8);
        byte[] a = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(1)), params);
        byte[] b = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(2)), params);
        Assert.assertFalse(java.util.Arrays.equals(a, b));
        Assert.assertEquals(0, a[0]); // leading zero from zmByteLength = elementByteLength + 1
    }

    @Test
    public void expansionScheduleUsesTotalTau() {
        Assert.assertEquals(4.5, Pt26IbltParams.chooseExpansionForTau((1 << 16) - 1), 1e-9);
        Assert.assertEquals(3.5, Pt26IbltParams.chooseExpansionForTau(1 << 16), 1e-9);
        Assert.assertEquals(3.5, Pt26IbltParams.chooseExpansionForTau((1 << 18) - 1), 1e-9);
        Assert.assertEquals(2.0, Pt26IbltParams.chooseExpansionForTau(1 << 18), 1e-9);
        Assert.assertEquals(2.0, Pt26IbltParams.chooseExpansionForTau((1 << 20) - 1), 1e-9);
        Assert.assertEquals(1.5, Pt26IbltParams.chooseExpansionForTau(1 << 20), 1e-9);
        // Asymmetric: n0=1, n1=2^16-2 ⇒ τ = 2^16-1 < 2^16 ⇒ e=4.5
        Assert.assertEquals(4.5, Pt26IbltParams.chooseExpansionForTau(1 + ((1 << 16) - 2)), 1e-9);
        Assert.assertEquals(3.5, Pt26IbltParams.chooseExpansionForTau((1 << 16) + (1 << 16)), 1e-9);
    }

    private static Pt26IbltParams params(int elementLen) {
        byte[][] keys = new byte[Pt26IbltParams.DEFAULT_K][];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = BlockUtils.randomBlock(RANDOM);
        }
        return Pt26IbltParams.createDefault(16, 16, elementLen, keys);
    }

    private static byte[] randomZm(Pt26IbltParams params) {
        byte[] out = new byte[params.getZmByteLength()];
        RANDOM.nextBytes(out);
        out[0] = 0; // keep value < 2^(8*(L-1)) subset; still valid in Z_M
        return out;
    }

    private static byte[] bytes(int marker) {
        byte[] out = new byte[8];
        out[7] = (byte) marker;
        return out;
    }
}
