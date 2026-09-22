package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

/**
 * Ours constants (Figure 8: ℓ = 128 bits, γ = 256 bits).
 */
public final class SmallEcConstants {
    /** Item length ℓ = 128 bits. */
    public static final int ITEM_BYTE_LENGTH = 16;
    /** Permutation / field input length γ = 256 bits. */
    public static final int DOMAIN_GAMMA_BYTES = 32;
    /** Trailing zero padding γ − ℓ. */
    public static final int DOMAIN_PAD_BYTES = DOMAIN_GAMMA_BYTES - ITEM_BYTE_LENGTH;
    public static final int POINT_BYTES = 32;
    public static final int SCALAR_BYTES = 32;

    private SmallEcConstants() {
        // empty
    }
}
