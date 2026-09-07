package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * Public IBLT parameters {@code prm = (M, k, ℓ)} for EUROCRYPT_PisTri26 (§3).
 * <p>
 * Automatic expansion uses the total peel threshold {@code τ = n0 + n1} (not the larger party alone).
 * Between paper Table-1 thresholds the schedule retains the larger (more conservative) factor from
 * the previous validated threshold until the next threshold is reached. These factors are empirical
 * / extrapolated listing-failure parameters targeting roughly {@code 2^-40} failure for the
 * evaluated balanced profiles; ordinary PSU tests do not prove that bound.
 */
public class Pt26IbltParams implements Serializable {
    private static final long serialVersionUID = -4829103746510293847L;

    /** Default number of IBLT hash functions / subtables ({@code k}). */
    public static final int DEFAULT_K = 5;

    /**
     * Conservative expansion used for {@code τ < 2^16}. Prefer {@link #chooseExpansionForTau(int)}.
     */
    public static final double DEFAULT_EXPANSION = 4.5;

    /**
     * Expansion schedule indexed by total threshold {@code τ = n0 + n1}.
     * <pre>
     *   τ &lt; 2^16         ⇒ e = 4.5
     *   2^16 ≤ τ &lt; 2^18  ⇒ e = 3.5
     *   2^18 ≤ τ &lt; 2^20  ⇒ e = 2.0
     *   τ ≥ 2^20         ⇒ e = 1.5
     * </pre>
     */
    public static double chooseExpansionForTau(int tau) {
        MathPreconditions.checkGreater("tau", tau, 0);
        if (tau < (1 << 16)) {
            return 4.5;
        }
        if (tau < (1 << 18)) {
            return 3.5;
        }
        if (tau < (1 << 20)) {
            return 2.0;
        }
        return 1.5;
    }

    /**
     * @deprecated Use {@link #chooseExpansionForTau(int)} with {@code τ = n0 + n1}.
     *             This legacy entry point treated the argument as a per-party size.
     */
    @Deprecated
    public static double chooseExpansion(int perPartySize) {
        // Preserve old call sites used in unit tests that still pass a single party size by
        // interpreting it as a balanced-profile proxy: τ ≈ 2·perPartySize.
        return chooseExpansionForTau(Math.max(1, perPartySize) * 2);
    }

    private final int k;
    private final int subtableSize;
    private final BigInteger modulus;
    private final int zmByteLength;
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
        MathPreconditions.checkEqual("modulus", "2^(8·zmByteLength)",
            modulus, Pt26Zm.modulusFor(zmByteLength));
    }

    /**
     * Default parameters: {@code k = 5}, {@code M = 2^(8·(elementByteLength+1))}, expansion from
     * {@code τ = maxServerSize + maxClientSize}.
     */
    public static Pt26IbltParams createDefault(
        int maxServerSize, int maxClientSize, int elementByteLength, byte[][] hashKeys
    ) {
        int k = DEFAULT_K;
        int tau = maxServerSize + maxClientSize;
        double expansion = chooseExpansionForTau(tau);
        int subtableSize = Math.max(8, (int) Math.ceil(tau * expansion / k));
        int zmByteLength = elementByteLength + 1;
        BigInteger modulus = Pt26Zm.modulusFor(zmByteLength);
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
