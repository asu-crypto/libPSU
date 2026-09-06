package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

/**
 * Invertible ideal permutation Π over fixed-length γ-bit strings.
 */
public interface IdealPermutation {
    /**
     * Applies Π to a γ-byte block (input length must equal domain size).
     *
     * @param bytesGamma γ-bit string (cloned internally if needed).
     * @return Π(bytesGamma).
     */
    byte[] permute(byte[] bytesGamma);

    /**
     * Applies Π⁻¹.
     *
     * @param bytesGamma γ-bit string.
     * @return Π⁻¹(bytesGamma).
     */
    byte[] inversePermute(byte[] bytesGamma);

    /** Domain byte length γ/8. */
    int domainByteLength();
}
