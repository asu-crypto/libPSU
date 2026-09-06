package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

/**
 * PGT26 (Pu-Gao-Trieu) protocol constants. This is not EUROCRYPT_PisTri26/IBLT.
 */
public final class Pgt26Constants {
    /** 128-bit items (paper Table 1). */
    public static final int ITEM_BYTE_LENGTH = 16;
    /** Field/domain size γ = 256 for 2M padding. */
    public static final int DOMAIN_FIELD_BYTES = 32;
    /** Trailing zero padding length γ − ℓ for 2M. */
    public static final int DOMAIN_PAD_BYTES = DOMAIN_FIELD_BYTES - ITEM_BYTE_LENGTH;

    private Pgt26Constants() {
        // empty
    }
}
