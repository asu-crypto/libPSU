package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * Small-set EC PSU (Figure 8, semi-honest, one-sided output).
 */
public class SmallEcElligatorPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * W comparison mode for Figure 8 step 3.
     */
    public enum WCompareMode {
        /** Exact Figure 8: client sends full W points; server compares full canonical EC points. */
        FULL_POINT_EXACT,

        /**
         * Optional probabilistic optimization:
         * client sends truncated fingerprints of W points instead of full W points.
         * This reduces communication but may cause false matches with probability about n^2 / 2^lambda.
         */
        TRUNCATED_W_PROBABILISTIC
    }

    /**
     * Fingerprint derivation for {@link WCompareMode#TRUNCATED_W_PROBABILISTIC}.
     */
    public enum FingerprintMethod {
        /**
         * Fastest: directly truncate canonical EC point encoding.
         * Suitable for DH-blinded random-looking points.
         */
        CANONICAL_POINT_PREFIX,

        /**
         * More conservative but slower: hash canonical EC point encoding, then truncate.
         * Do not make this default.
         */
        HASH_THEN_TRUNCATE
    }

    private final int itemBitLength;
    private final boolean logStats;
    private final WCompareMode wCompareMode;
    private final FingerprintMethod fingerprintMethod;
    private final int statisticalSecurityBits;
    private final int fingerprintBitLength;
    private final boolean asyncPrecomputeW;
    private final int asyncPrecomputeThreshold;
    private final boolean parallelEc;
    private final int parallelThreshold;

    private SmallEcElligatorPsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
        itemBitLength = builder.itemBitLength;
        logStats = builder.logStats;
        wCompareMode = builder.wCompareMode;
        fingerprintMethod = builder.fingerprintMethod;
        statisticalSecurityBits = builder.statisticalSecurityBits;
        fingerprintBitLength = builder.fingerprintBitLength;
        asyncPrecomputeW = builder.asyncPrecomputeW;
        asyncPrecomputeThreshold = builder.asyncPrecomputeThreshold;
        parallelEc = builder.parallelEc;
        parallelThreshold = builder.parallelThreshold;
        validateFingerprintBitLength();
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.Ours;
    }

    public int getItemBitLength() {
        return itemBitLength;
    }

    public boolean isLogStats() {
        return logStats;
    }

    public WCompareMode getWCompareMode() {
        return wCompareMode;
    }

    public FingerprintMethod getFingerprintMethod() {
        return fingerprintMethod;
    }

    public int getStatisticalSecurityBits() {
        return statisticalSecurityBits;
    }

    public int getFingerprintBitLength() {
        return fingerprintBitLength;
    }

    public boolean isAsyncPrecomputeW() {
        return asyncPrecomputeW;
    }

    public int getAsyncPrecomputeThreshold() {
        return asyncPrecomputeThreshold;
    }

    /** True when W precomputation should run on a background thread. */
    public boolean useAsyncPrecomputeW(int n) {
        return asyncPrecomputeW && n >= asyncPrecomputeThreshold;
    }

    public boolean isParallelEc() {
        return parallelEc;
    }

    public int getParallelThreshold() {
        return parallelThreshold;
    }

    /**
     * Resolves fingerprint bit length for set size {@code n} (auto when {@code fingerprintBitLength == 0}).
     */
    public int getResolvedFingerprintBitLength(int n) {
        Preconditions.checkArgument(n > 0);
        if (wCompareMode == WCompareMode.FULL_POINT_EXACT) {
            throw new IllegalStateException("fingerprint length not used in FULL_POINT_EXACT mode");
        }
        int lambda = fingerprintBitLength == 0
            ? autoFingerprintBitLength(n, statisticalSecurityBits)
            : fingerprintBitLength;
        validateFingerprintLambda(n, lambda, statisticalSecurityBits);
        return lambda;
    }

    public void validateElementByteLength(int elementByteLength) {
        Preconditions.checkArgument(
            elementByteLength == SmallEcConstants.ITEM_BYTE_LENGTH,
            "element_byte_length must be %s for Ours",
            SmallEcConstants.ITEM_BYTE_LENGTH
        );
        Preconditions.checkArgument(
            elementByteLength * Byte.SIZE <= itemBitLength,
            "element bits %s exceed configured max %s",
            elementByteLength * Byte.SIZE, itemBitLength
        );
    }

    public void validateWCompareForSetSize(int n) {
        if (wCompareMode == WCompareMode.TRUNCATED_W_PROBABILISTIC) {
            getResolvedFingerprintBitLength(n);
        }
    }

    static int ceilLog2(int n) {
        if (n <= 1) {
            return 0;
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(n - 1);
    }

    static int autoFingerprintBitLength(int n, int statisticalSecurityBits) {
        int required = statisticalSecurityBits + 2 * ceilLog2(n);
        if (required <= 64) {
            return 64;
        } else if (required <= 96) {
            return 96;
        } else if (required <= 128) {
            return 128;
        } else {
            throw new IllegalArgumentException(
                "fingerprint length above 128 bits required for this n/security"
            );
        }
    }

    private void validateFingerprintBitLength() {
        if (wCompareMode == WCompareMode.FULL_POINT_EXACT) {
            return;
        }
        if (fingerprintBitLength == 0) {
            return;
        }
        Preconditions.checkArgument(
            fingerprintBitLength == 64 || fingerprintBitLength == 96 || fingerprintBitLength == 128,
            "fingerprintBitLength must be 0 (auto), 64, 96, or 128"
        );
    }

    private static void validateFingerprintLambda(int n, int lambda, int statisticalSecurityBits) {
        int required = statisticalSecurityBits + 2 * ceilLog2(n);
        Preconditions.checkArgument(
            lambda >= required,
            "fingerprint lambda %s must be >= %s for n=%s",
            lambda, required, n
        );
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<SmallEcElligatorPsuConfig> {
        private int itemBitLength = 128;
        private boolean logStats;
        private WCompareMode wCompareMode = WCompareMode.FULL_POINT_EXACT;
        private FingerprintMethod fingerprintMethod = FingerprintMethod.CANONICAL_POINT_PREFIX;
        private int statisticalSecurityBits = 40;
        private int fingerprintBitLength = 0;
        private boolean asyncPrecomputeW = true;
        private int asyncPrecomputeThreshold = 1024;
        private boolean parallelEc = false;
        private int parallelThreshold = 1024;

        public Builder setItemBitLength(int itemBitLength) {
            this.itemBitLength = itemBitLength;
            return this;
        }

        public Builder setLogStats(boolean logStats) {
            this.logStats = logStats;
            return this;
        }

        public Builder setWCompareMode(WCompareMode wCompareMode) {
            this.wCompareMode = wCompareMode;
            return this;
        }

        public Builder setFingerprintMethod(FingerprintMethod fingerprintMethod) {
            this.fingerprintMethod = fingerprintMethod;
            return this;
        }

        public Builder setStatisticalSecurityBits(int statisticalSecurityBits) {
            this.statisticalSecurityBits = statisticalSecurityBits;
            return this;
        }

        public Builder setFingerprintBitLength(int fingerprintBitLength) {
            this.fingerprintBitLength = fingerprintBitLength;
            return this;
        }

        public Builder setAsyncPrecomputeW(boolean asyncPrecomputeW) {
            this.asyncPrecomputeW = asyncPrecomputeW;
            return this;
        }

        public Builder setAsyncPrecomputeThreshold(int asyncPrecomputeThreshold) {
            this.asyncPrecomputeThreshold = asyncPrecomputeThreshold;
            return this;
        }

        public Builder setParallelEc(boolean parallelEc) {
            this.parallelEc = parallelEc;
            return this;
        }

        public Builder setParallelThreshold(int parallelThreshold) {
            this.parallelThreshold = parallelThreshold;
            return this;
        }

        @Override
        public SmallEcElligatorPsuConfig build() {
            return new SmallEcElligatorPsuConfig(this);
        }
    }
}
