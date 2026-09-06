package edu.alibaba.mpc4j.s2pc.pso.psu.dc17;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Helpers for ACISP_DavCid17 EIBF PSU.
 */
final class Dc17PsuUtils {
    private Dc17PsuUtils() {
        // empty
    }

    /**
     * k = log2(1/epsilon).
     */
    static int optimalK(double epsilon) {
        MathPreconditions.checkPositive("epsilon", epsilon);
        return (int) Math.ceil(-Math.log(epsilon) / Math.log(2));
    }

    /**
     * B = ceil(n * log2(e) * log2(1/epsilon)) = ceil(n * log2(e) * k).
     */
    static int optimalB(int n, int k) {
        MathPreconditions.checkGreater("n", n, 1);
        MathPreconditions.checkGreater("k", k, 1);
        double log2e = 1.4426950408889634;
        long b = (long) Math.ceil(n * log2e * k);
        // keep in int range for current main usage
        MathPreconditions.checkLessOrEqual("B", b, Integer.MAX_VALUE);
        return (int) b;
    }

    static BigInteger elementToInteger(byte[] elementBytes) {
        return new BigInteger(1, elementBytes);
    }

    static byte[] elementBytes(ByteBuffer element) {
        ByteBuffer copy = element.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    static byte[] decodeElement(BigInteger x, int elementByteLength) {
        byte[] raw = x.toByteArray();
        // drop sign byte if present
        if (raw.length > 1 && raw[0] == 0x00) {
            raw = Arrays.copyOfRange(raw, 1, raw.length);
        }
        byte[] out = new byte[elementByteLength];
        int copyLen = Math.min(elementByteLength, raw.length);
        System.arraycopy(raw, raw.length - copyLen, out, elementByteLength - copyLen, copyLen);
        return out;
    }
}
