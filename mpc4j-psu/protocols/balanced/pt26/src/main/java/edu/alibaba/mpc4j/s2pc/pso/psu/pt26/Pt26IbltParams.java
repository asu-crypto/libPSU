package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Public IBLT parameters {@code prm = (M, k, ℓ)} for EUROCRYPT_PisTri26 (§3).
 */
public class Pt26IbltParams implements Serializable {
    private static final long serialVersionUID = -4829103746510293847L;
    /**
     * Default number of IBLT subtables / hash functions ({@code k}) used by {@link #createDefault}.
     * Single source of truth for callers that need to size their {@code hashKeys[]} array; do not
     * hard-code a literal in {@code Pt26PsuServer}/{@code Pt26PsuClient}, or the constructor's
     * {@code hashKeys.length == k} check will throw.
     */
    public static final int DEFAULT_K = 5;
    /**
     * Conservative IBLT expansion factor (subtableSize ≈ expansion*(|X|+|Y|)/k) used for tiny sets
     * smaller than 2^14, and as a safety upper bound. Most callers should not read this directly;
     * use {@link #chooseExpansion(int)} which mirrors Table 1 in the EUROCRYPT_PisTri26 paper and the
     * {@code IBLT_MULT_FAC} schedule in the reference impl
     * (asu-crypto/IBLT-based-PSU, {@code src/benchmarks/psu.bench.cpp}).
     */
    public static final double DEFAULT_EXPANSION = 4.5;

    /**
     * Picks the IBLT expansion factor {@code e} the way EUROCRYPT_PisTri26 Table 1 / ref impl does, indexed by the
     * larger party's set size (each row of Table 1 is the largest set size for which {@code e} keeps
     * {@code List} failure ≤ 2^-40). For balanced 2^18 × 2^18 this returns {@code 2.0}, matching the
     * paper's reported communication numbers (130.07/99.87 MB).
     */
    public static double chooseExpansion(int perPartySize) {
        if (perPartySize <= (1 << 14)) {
            return 4.5;
        }
        if (perPartySize <= (1 << 16)) {
            return 3.5;
        }
        if (perPartySize <= (1 << 18)) {
            return 2.0;
        }
        return 1.5;
    }
    /**
     * number of subtables / hash functions
     */
    private final int k;
    /**
     * subtable size ℓ
     */
    private final int subtableSize;
    /**
     * modulus M (prime)
     */
    private final BigInteger modulus;
    /**
     * fixed-length encoding of elements in Z_M
     */
    private final int zmByteLength;
    /**
     * per-subtable hash keys
     */
    private final byte[][] hashKeys;

    public Pt26IbltParams(int k, int subtableSize, BigInteger modulus, int zmByteLength, byte[][] hashKeys) {
        MathPreconditions.checkGreater("k", k, 1);
        MathPreconditions.checkGreater("subtableSize", subtableSize, 1);
        MathPreconditions.checkGreater("zmByteLength", zmByteLength, 1);
        this.k = k;
        this.subtableSize = subtableSize;
        this.modulus = modulus;
        this.zmByteLength = zmByteLength;
        this.hashKeys = hashKeys;
        MathPreconditions.checkEqual("hashKeys.length", "k", hashKeys.length, k);
    }

    /**
     * Default parameters for semi-honest PSU tests (Theorem 1 style sizing).
     * Reads {@link #DEFAULT_K} (single source of truth for {@code hashKeys.length}
     * so {@code Pt26PsuServer} / {@code Pt26PsuClient} cannot drift out of sync)
     * and {@link #chooseExpansion(int)} (matches paper Table 1 by per-party size).
     */
    public static Pt26IbltParams createDefault(int maxServerSize, int maxClientSize, int elementByteLength, byte[][] hashKeys) {
        int k = DEFAULT_K;
        int threshold = maxServerSize + maxClientSize;
        double expansion = chooseExpansion(Math.max(maxServerSize, maxClientSize));
        int subtableSize = Math.max(8, (int) Math.ceil(threshold * expansion / k));
        int zmByteLength = elementByteLength + 1;
        BigInteger modulus = BigInteger.ONE.shiftLeft(zmByteLength * 8).nextProbablePrime();
        return new Pt26IbltParams(k, subtableSize, modulus, zmByteLength, hashKeys);
    }

    public int getK() {
        return k;
    }

    public int getSubtableSize() {
        return subtableSize;
    }

    public BigInteger modulus() {
        return modulus;
    }

    public int getZmByteLength() {
        return zmByteLength;
    }

    public byte[] getHashKey(int hashIndex) {
        return hashKeys[hashIndex];
    }

    public int totalBins() {
        return k * subtableSize;
    }
}
