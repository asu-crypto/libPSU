package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly.Hn12Polynomial;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * π_POLY: coefficient encryption shape + degree accounting (HN12 Protocol 5, Step 5).
 */
public final class Hn12ZkPoly {
    private Hn12ZkPoly() {
    }

    public static final class Proof {
        public final int totalDegree;
        public final byte[] bindingDigest;

        public Proof(int totalDegree, byte[] bindingDigest) {
            this.totalDegree = totalDegree;
            this.bindingDigest = bindingDigest;
        }
    }

    public static Proof prove(
        Hn12DdhGroup group, int expectedElementCount, List<List<BigInteger>> coeffMatrices,
        List<List<Hn12Ciphertext>> encMatrices, Hn12Transcript transcript
    ) {
        int degSum = 0;
        for (List<BigInteger> coeffs : coeffMatrices) {
            int d = Hn12Polynomial.trueDegree(coeffs);
            if (d < 0) {
                throw new IllegalArgumentException("identically zero polynomial");
            }
            degSum += d;
        }
        if (degSum != expectedElementCount) {
            throw new IllegalArgumentException("degree accounting mismatch");
        }
        appendMatrices(group, encMatrices, transcript);
        transcript.appendBytes(intToBytes(expectedElementCount));
        BigInteger challenge = transcript.challenge(group.getQ());
        return new Proof(degSum, challenge.toByteArray());
    }

    public static boolean verify(
        Hn12DdhGroup group, int expectedElementCount, int binCount, int maxDegree,
        List<List<Hn12Ciphertext>> encMatrices, Proof proof, Hn12Transcript transcript
    ) {
        if (proof.totalDegree != expectedElementCount) {
            return false;
        }
        if (encMatrices.size() != binCount) {
            return false;
        }
        for (List<Hn12Ciphertext> row : encMatrices) {
            if (row.isEmpty() || row.size() > maxDegree + 1) {
                return false;
            }
            for (Hn12Ciphertext ct : row) {
                ct.validate(group, false);
            }
        }
        appendMatrices(group, encMatrices, transcript);
        transcript.appendBytes(intToBytes(expectedElementCount));
        BigInteger challenge = transcript.challenge(group.getQ());
        return Arrays.equals(challenge.toByteArray(), proof.bindingDigest);
    }

    private static void appendMatrices(Hn12DdhGroup group, List<List<Hn12Ciphertext>> encMatrices, Hn12Transcript t) {
        for (List<Hn12Ciphertext> row : encMatrices) {
            for (Hn12Ciphertext ct : row) {
                t.appendBytes(ct.serialize(group));
            }
        }
    }

    private static byte[] intToBytes(int v) {
        return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }
}
