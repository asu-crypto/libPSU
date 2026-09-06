package edu.alibaba.mpc4j.s2pc.pso;

import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * Paper-fidelity labelling for PSU protocols.
 */
public final class PsuPaperFidelity {
    private PsuPaperFidelity() {
        // empty
    }

    public static PsuType resolvePsuType(String name) {
        return PsuType.fromProtocolId(name);
    }

    public static boolean isProxyOrInspired(PsuType type) {
        return false;
    }

    public static boolean isProxyOrInspiredName(String ptoName) {
        if (ptoName == null || ptoName.isEmpty()) {
            return false;
        }
        String t = ptoName.trim();
        return t.endsWith("_PROXY") || t.endsWith("_INSPIRED") || t.contains("_PROXY_");
    }

    public static boolean isReservedPaperExact(PsuType type) {
        return false;
    }

    public static boolean isRemovedProtocol(PsuType type) {
        return false;
    }
}
