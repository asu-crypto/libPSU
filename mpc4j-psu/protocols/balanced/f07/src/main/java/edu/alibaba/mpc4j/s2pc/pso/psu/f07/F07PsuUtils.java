package edu.alibaba.mpc4j.s2pc.pso.psu.f07;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for ACNS_Frikken07 polynomial-based PSU.
 */
final class F07PsuUtils {
    private F07PsuUtils() {
        // empty
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
        if (raw.length > 1 && raw[0] == 0x00) {
            raw = Arrays.copyOfRange(raw, 1, raw.length);
        }
        byte[] out = new byte[elementByteLength];
        int copyLen = Math.min(elementByteLength, raw.length);
        System.arraycopy(raw, raw.length - copyLen, out, elementByteLength - copyLen, copyLen);
        return out;
    }

    /**
     * Builds coefficients of f(x) = Π_i (x - r_i) in Z_modulus.
     *
     * Output is [a0, a1, ..., an] where f(x) = a0 + a1 x + ... + an x^n.
     */
    static List<BigInteger> buildRootPolynomial(List<BigInteger> roots, BigInteger modulus) {
        MathPreconditions.checkGreaterOrEqual("roots.size()", roots.size(), 1);
        List<BigInteger> coeffs = new ArrayList<>(1);
        coeffs.add(BigInteger.ONE); // degree 0
        for (BigInteger root : roots) {
            BigInteger a = root.mod(modulus);
            // newCoeffs = (x - a) * coeffs
            List<BigInteger> newCoeffs = new ArrayList<>(coeffs.size() + 1);
            // degree increases by 1
            newCoeffs.add(coeffs.get(0).negate().multiply(a).mod(modulus)); // constant term
            for (int i = 1; i < coeffs.size(); i++) {
                // (x * coeffs) contributes coeffs[i-1] to x^i; (-a * coeffs) contributes -a*coeffs[i]
                BigInteger v = coeffs.get(i - 1).subtract(a.multiply(coeffs.get(i))).mod(modulus);
                newCoeffs.add(v);
            }
            // highest degree term: coeffs[last]
            newCoeffs.add(coeffs.get(coeffs.size() - 1).mod(modulus));
            coeffs = newCoeffs;
        }
        return coeffs;
    }

    /**
     * Samples an invertible scalar in {@code Z_modulus}.
     */
    static BigInteger sampleInvertibleScalar(BigInteger modulus, java.util.Random random) {
        BigInteger r;
        do {
            r = new BigInteger(modulus.bitLength(), random).mod(modulus);
        } while (r.signum() == 0 || !r.gcd(modulus).equals(BigInteger.ONE));
        return r;
    }

    /**
     * Recovers a server element from decrypted tuple {@code (x, y) = (f(s)·s·r, f(s)·r)}.
     * Returns {@code null} only for intersection tuples {@code (0, 0)}.
     */
    static BigInteger recoverServerElement(BigInteger x, BigInteger y, BigInteger modulus) {
        if (x.signum() == 0 && y.signum() == 0) {
            return null;
        }
        return x.multiply(y.modInverse(modulus)).mod(modulus);
    }
}
