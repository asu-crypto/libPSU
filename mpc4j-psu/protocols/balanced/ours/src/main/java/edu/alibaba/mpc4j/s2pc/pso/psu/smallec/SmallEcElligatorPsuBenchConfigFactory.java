package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import com.google.common.base.Preconditions;

import java.util.Optional;
import java.util.Properties;

/**
 * Maps {@code small_ec_bench_*} append strings to explicit {@link SmallEcElligatorPsuConfig} for fair benchmarks.
 */
public final class SmallEcElligatorPsuBenchConfigFactory {
    private static final String BENCH_PREFIX = "small_ec_bench_";

    private SmallEcElligatorPsuBenchConfigFactory() {
        // empty
    }

    public static boolean isBenchAppend(String appendString) {
        return appendString != null && appendString.contains(BENCH_PREFIX);
    }

    /**
     * If {@code append_string} is a known bench case, returns a fully configured builder (overrides .conf properties).
     * Longer case names are matched first to avoid substring collisions.
     */
    public static Optional<SmallEcElligatorPsuConfig.Builder> benchBuilderFromAppend(String appendString) {
        if (!isBenchAppend(appendString)) {
            return Optional.empty();
        }
        SmallEcElligatorPsuConfig.Builder builder = new SmallEcElligatorPsuConfig.Builder()
            .setItemBitLength(128)
            .setFingerprintMethod(SmallEcElligatorPsuConfig.FingerprintMethod.CANONICAL_POINT_PREFIX)
            .setStatisticalSecurityBits(40)
            .setAsyncPrecomputeThreshold(1024)
            .setParallelThreshold(1024);

        if (appendString.contains("case8_fp_auto_async_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
                .setFingerprintBitLength(0)
                .setAsyncPrecomputeW(true)
                .setParallelEc(true));
        }
        if (appendString.contains("case7_fp64_no_async_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
                .setFingerprintBitLength(64)
                .setAsyncPrecomputeW(false)
                .setParallelEc(true));
        }
        if (appendString.contains("case6_fp64_async_no_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
                .setFingerprintBitLength(64)
                .setAsyncPrecomputeW(true)
                .setParallelEc(false));
        }
        if (appendString.contains("case5_fp64_no_async_no_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
                .setFingerprintBitLength(64)
                .setAsyncPrecomputeW(false)
                .setParallelEc(false));
        }
        if (appendString.contains("case4_exact_async_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT)
                .setAsyncPrecomputeW(true)
                .setParallelEc(true));
        }
        if (appendString.contains("case3_exact_no_async_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT)
                .setAsyncPrecomputeW(false)
                .setParallelEc(true));
        }
        if (appendString.contains("case2_exact_async_no_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT)
                .setAsyncPrecomputeW(true)
                .setParallelEc(false));
        }
        if (appendString.contains("case1_exact_no_async_no_parallel")) {
            return Optional.of(builder
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT)
                .setAsyncPrecomputeW(false)
                .setParallelEc(false));
        }
        return Optional.empty();
    }

    public static SmallEcElligatorPsuConfig createForProperties(Properties properties) {
        String append = trim(properties.getProperty("append_string"));
        boolean logStats = readBoolean(properties, "small_ec_log_stats", false);

        Optional<SmallEcElligatorPsuConfig.Builder> bench = benchBuilderFromAppend(append);
        if (bench.isPresent()) {
            SmallEcElligatorPsuConfig config = bench.get().setLogStats(logStats).build();
            validateBenchCase(append, config);
            return config;
        }
        return null;
    }

    public static void validateBenchCase(String appendString, SmallEcElligatorPsuConfig config) {
        if (!isBenchAppend(appendString)) {
            return;
        }
        if (appendString.contains("fp64") || appendString.contains("fp_auto")) {
            Preconditions.checkArgument(
                config.getWCompareMode() == SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC,
                "fingerprint benchmark case did not enable TRUNCATED_W_PROBABILISTIC: %s", appendString
            );
        }
        if (appendString.contains("fp64")) {
            Preconditions.checkArgument(
                config.getFingerprintBitLength() == 64,
                "fp64 benchmark case must set fingerprintBitLength = 64"
            );
        }
        if (appendString.contains("fp_auto")) {
            Preconditions.checkArgument(
                config.getFingerprintBitLength() == 0,
                "fp_auto benchmark case must set fingerprintBitLength = 0"
            );
        }
    }

    static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean readBoolean(Properties properties, String key, boolean defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
