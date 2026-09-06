package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12ElGamal;

import java.math.BigInteger;
import java.util.List;

/**
 * Homomorphic evaluation of encrypted coefficient polynomials at public y (HN12 Protocol 5, Step 6).
 * Coefficients are [Q_0, ..., Q_m] with Q(z) = Σ Q_i z^i; uses Horner from high degree.
 */
public final class Hn12HomomorphicPolyEval {
    private Hn12HomomorphicPolyEval() {
    }

    public static Hn12Ciphertext evaluate(
        Hn12DdhGroup group, Hn12ElGamal elGamal, List<Hn12Ciphertext> encCoeffs, BigInteger y
    ) {
        if (encCoeffs.isEmpty()) {
            throw new IllegalArgumentException("empty coefficient list");
        }
        int m = encCoeffs.size() - 1;
        Hn12Ciphertext acc = encCoeffs.get(m);
        acc.validate(group, false);
        for (int j = m - 1; j >= 0; j--) {
            Hn12Ciphertext enc = encCoeffs.get(j);
            enc.validate(group, false);
            acc = elGamal.multiply(elGamal.powScalar(acc, y), enc);
        }
        return acc;
    }
}
