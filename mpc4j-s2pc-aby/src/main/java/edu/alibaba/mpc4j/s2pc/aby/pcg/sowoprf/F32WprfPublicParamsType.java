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
     * Fixed AltMod matrices from the original HaoWan {@code ePSU_fast} link path
     * ({@code Th0masAndy/secure-join@4a23526} via {@code ePSU-from-ssPMT@255bf1e}).
     */
    HAO_WAN_SECURE_JOIN,
}
