package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bin polynomial Q(z) = ∏_{x ∈ B_i} (z - x); empty bin => Q(z) = 1 (HN12 §2).
 */
public final class Hn12Polynomial {
    private Hn12Polynomial() {
    }

    public static List<BigInteger> buildRootPolynomial(List<BigInteger> roots, BigInteger modulus, int paddedDegree) {
        List<BigInteger> coeffs;
        if (roots.isEmpty()) {
            coeffs = new ArrayList<>(Collections.singletonList(BigInteger.ONE));
        } else {
            coeffs = new ArrayList<>(1);
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
        }
        while (coeffs.size() <= paddedDegree) {
            coeffs.add(BigInteger.ZERO);
        }
        if (coeffs.size() > paddedDegree + 1) {
            throw new IllegalStateException("polynomial degree exceeds padded capacity");
        }
        return coeffs;
    }

    public static BigInteger evaluate(List<BigInteger> coeffs, BigInteger z, BigInteger modulus) {
        BigInteger acc = BigInteger.ZERO;
        for (int i = coeffs.size() - 1; i >= 0; i--) {
            acc = acc.multiply(z).add(coeffs.get(i)).mod(modulus);
        }
        return acc;
    }

    public static boolean isIdenticallyZero(List<BigInteger> coeffs) {
        for (BigInteger c : coeffs) {
            if (c.signum() != 0) {
                return false;
            }
        }
        return true;
    }

    public static int trueDegree(List<BigInteger> coeffs) {
        int d = -1;
        for (int i = coeffs.size() - 1; i >= 0; i--) {
            if (coeffs.get(i).signum() != 0) {
                d = i;
                break;
            }
        }
        return d;
    }

    /** Drops trailing zero coefficients (padding must not be evaluated). */
    public static List<BigInteger> trimTrailingZeros(List<BigInteger> coeffs) {
        int d = trueDegree(coeffs);
        if (d < 0) {
            return List.of(BigInteger.ONE);
        }
        return coeffs.subList(0, d + 1);
    }
}
