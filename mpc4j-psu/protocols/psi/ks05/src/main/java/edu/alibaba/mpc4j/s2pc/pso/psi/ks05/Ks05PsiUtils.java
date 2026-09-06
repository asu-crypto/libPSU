package edu.alibaba.mpc4j.s2pc.pso.psi.ks05;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for C_KisSon05 polynomial-based PSI (same algebra as ACNS_Frikken07 PSU).
 */
final class Ks05PsiUtils {
    private Ks05PsiUtils() {
        // empty
    }

    static BigInteger encodeElement(byte[] elementBytes, BigInteger modulus) {
        BigInteger x = new BigInteger(1, elementBytes);
        return x.mod(modulus);
    }

    static BigInteger encodeElement(ByteBuffer element, BigInteger modulus) {
        return encodeElement(elementBytes(element), modulus);
    }

    static List<BigInteger> buildRootPolynomial(List<BigInteger> roots, BigInteger modulus) {
        MathPreconditions.checkGreaterOrEqual("roots.size()", roots.size(), 1);
        List<BigInteger> coeffs = new ArrayList<>(1);
        coeffs.add(BigInteger.ONE);
        for (BigInteger root : roots) {
            BigInteger a = root.mod(modulus);
            List<BigInteger> newCoeffs = new ArrayList<>(coeffs.size() + 1);
            newCoeffs.add(coeffs.get(0).negate().multiply(a).mod(modulus));
            for (int i = 1; i < coeffs.size(); i++) {
                BigInteger v = coeffs.get(i - 1).subtract(a.multiply(coeffs.get(i))).mod(modulus);
                newCoeffs.add(v);
            }
            newCoeffs.add(coeffs.get(coeffs.size() - 1).mod(modulus));
            coeffs = newCoeffs;
        }
        return coeffs;
    }

    static List<BigInteger> multiplyPlainPolynomials(List<BigInteger> left, List<BigInteger> right, BigInteger modulus) {
        List<BigInteger> product = zeroPolynomial(left.size() + right.size() - 1);
        for (int i = 0; i < left.size(); i++) {
            BigInteger a = left.get(i).mod(modulus);
            for (int j = 0; j < right.size(); j++) {
                BigInteger b = right.get(j).mod(modulus);
                BigInteger value = product.get(i + j).add(a.multiply(b)).mod(modulus);
                product.set(i + j, value);
            }
        }
        return product;
    }

    static BigInteger evaluatePlainPolynomial(List<BigInteger> coeffs, BigInteger point, BigInteger modulus) {
        BigInteger value = BigInteger.ZERO;
        BigInteger x = point.mod(modulus);
        for (int i = coeffs.size() - 1; i >= 0; i--) {
            value = value.multiply(x).add(coeffs.get(i)).mod(modulus);
        }
        return value;
    }

    static List<BigInteger> randomPolynomial(int degree, BigInteger modulus, java.util.Random random) {
        List<BigInteger> coeffs = new ArrayList<>(degree + 1);
        for (int i = 0; i <= degree; i++) {
            coeffs.add(new BigInteger(modulus.bitLength(), random).mod(modulus));
        }
        return coeffs;
    }

    static void checkSetSizeLimit(int serverElementSize, int clientElementSize, int maxSetSize)
        throws MpcAbortException {
        if (serverElementSize > maxSetSize || clientElementSize > maxSetSize) {
            throw new MpcAbortException(String.format(
                "C_KisSon05 current Paillier polynomial implementation is limited to %d elements per party; "
                    + "requested server=%d, client=%d. Set ks05_max_set_size to raise this guard, "
                    + "or set skip_warmup=true for small benchmarks to avoid the default 2^10 warmup.",
                maxSetSize, serverElementSize, clientElementSize
            ));
        }
    }

    private static List<BigInteger> zeroPolynomial(int size) {
        List<BigInteger> coeffs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            coeffs.add(BigInteger.ZERO);
        }
        return coeffs;
    }

    private static byte[] elementBytes(ByteBuffer element) {
        ByteBuffer copy = element.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
