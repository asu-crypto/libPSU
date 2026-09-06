package edu.alibaba.mpc4j.s2pc.pso;

import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * Paper-fidelity labelling for PSU protocols.
 * <p>
 * Runnable CSS25 stacks are engineering proxies (PSTY19 default / RS21 comparison).
 * Paper-exact CSS25 remains reserved until missing paper components exist.
 * Two-sided PGT26 is publicly supported; one-sided PGT26 is internal/experimental only.
 * </p>
 */
public final class PsuPaperFidelity {
    private PsuPaperFidelity() {
        // empty
    }

    public static PsuType resolvePsuType(String name) {
        return PsuType.fromProtocolId(name);
    }

    public static boolean isProxyOrInspired(PsuType type) {
        return type == PsuType.ASIACCS_CSSW25;
    }

    public static boolean isProxyOrInspiredName(String ptoName) {
        if (ptoName == null || ptoName.isEmpty()) {
            return false;
        }
        String t = ptoName.trim();
        if (t.endsWith("_PROXY") || t.endsWith("_INSPIRED") || t.contains("_PROXY_")) {
            return true;
        }
        try {
            return isProxyOrInspired(resolvePsuType(t));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static boolean isReservedPaperExact(PsuType type) {
        // Paper-exact is a config mode of ASIACCS_CSSW25, not a separate enum value.
        return false;
    }

    /**
     * Detects CSS25 paper-exact configs without a compile-time dependency on the css25 module.
     */
    public static boolean isReservedPaperExact(PsuConfig config) {
        if (config == null || config.getPtoType() != PsuType.ASIACCS_CSSW25) {
            return false;
        }
        try {
            Object exact = config.getClass().getMethod("isPaperExact").invoke(config);
            return Boolean.TRUE.equals(exact);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /**
     * No public PSU types are removed. One-sided PGT26 remains internal/experimental and is not
     * exposed through {@code PsuFactory} under {@link PsuType#EUROCRYPT_PuGaoTri26} (two-sided only).
     */
    public static boolean isRemovedProtocol(PsuType type) {
        return false;
    }
}
