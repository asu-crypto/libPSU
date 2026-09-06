package edu.alibaba.libpsu.api;

/**
 * Underlying technique family (aligned with SoK PSU groupings).
 */
public enum ProtocolFamily {
    /** Multi-query reverse private membership test (e.g. PKC_GMRSS21-style). */
    RPMT,
    /** MCRG / ECRG / bECRG style constructions. */
    MCRG_ECRG,
    /** Polynomial or circuit PSI/PSU with AHE or similar. */
    POLYNOMIAL_AHE,
    /** Bloom-filter style with AHE. */
    BLOOM_AHE,
    /** Invertible Bloom Lookup Table (IBLT) style. */
    IBLT,
    /** Public-key or secret-key encryption centric (PKE/SKE UPSU/PSU). */
    PKE_SKE,
    /** Hash-OPRF + small-set additive HE (custom). */
    SMALL_SET_AHE,
    /** EC/Elligator small-set HashDH (semi-honest, no malicious proofs). */
    SMALL_SET_EC,
    /** Other / hybrid engineering variants. */
    OTHER,
}
