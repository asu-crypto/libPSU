package edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf;

/**
 * Public-parameter profile for {@link F32Wprf} matrices {@code A} and {@code B}.
 */
public enum F32WprfPublicParamsType {
    /**
     * Seeded random matrices (historical MPC4J default).
     */
    MPC4J_NATIVE,
    /**
     * Fixed AltMod matrices from ladnir/secure-join @ 1e1dddf (Hao–Wan fidelity).
     */
    HAO_WAN_SECURE_JOIN,
}
