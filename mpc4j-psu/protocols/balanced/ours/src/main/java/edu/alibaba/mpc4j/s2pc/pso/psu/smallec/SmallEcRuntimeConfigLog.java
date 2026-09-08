package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import org.slf4j.Logger;

/**
 * Shared runtime config / timing logs for Ours.
 */
final class SmallEcRuntimeConfigLog {
    private SmallEcRuntimeConfigLog() {
        // empty
    }

    static void logRuntimeConfig(Logger log, SmallEcElligatorPsuConfig config, int n) {
        int resolvedFpBits = -1;
        if (config.getWCompareMode() == SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC) {
            resolvedFpBits = config.getResolvedFingerprintBitLength(n);
        }

        log.info(
            "SMALL_EC config: mode={}, fpBits={}, resolvedFpBits={}, fpMethod={}, asyncW={}, asyncThreshold={}, "
                + "parallelEc={}, parallelThreshold={}, n={}",
            config.getWCompareMode(),
            config.getFingerprintBitLength(),
            resolvedFpBits,
            config.getFingerprintMethod(),
            config.isAsyncPrecomputeW(),
            config.getAsyncPrecomputeThreshold(),
            config.isParallelEc(),
            config.getParallelThreshold(),
            n
        );
    }

    static void logTiming(Logger log, SmallEcElligatorPsuConfig config, String label, long nanos) {
        if (!config.isLogStats()) {
            return;
        }
        log.info("SMALL_EC timing: {}={} ms", label, nanos / 1_000_000L);
    }
}
