package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * JOC:HazNis12 PSU config (Protocol 8 π∪).
 * <p>
 * The runnable path is the semi-honest/debug implementation: ideal PRF and skipped
 * malicious ZK (πCOUNT/πNZ). Full malicious security is not implemented; requesting
 * it fails closed at {@link Builder#build()}.
 * </p>
 */
public class Hn12PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    private final boolean enableSemiHonestDebug;
    private final boolean useIdealPrfForTesting;
    private final int groupBitLength;

    private Hn12PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
        enableSemiHonestDebug = builder.enableSemiHonestDebug;
        useIdealPrfForTesting = builder.useIdealPrfForTesting;
        groupBitLength = builder.groupBitLength;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.JOC_HazNis12;
    }

    public boolean isEnableSemiHonestDebug() {
        return enableSemiHonestDebug;
    }

    /**
     * Ideal PRF is for unit tests and fair-bench debug only; not a production OPRF.
     */
    public boolean isUseIdealPrfForTesting() {
        return useIdealPrfForTesting;
    }

    public int getGroupBitLength() {
        return groupBitLength;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Hn12PsuConfig> {
        private boolean enableSemiHonestDebug = true;
        private boolean useIdealPrfForTesting = true;
        private int groupBitLength = 256;

        public Builder setEnableSemiHonestDebug(boolean v) {
            this.enableSemiHonestDebug = v;
            return this;
        }

        public Builder setUseIdealPrfForTesting(boolean v) {
            this.useIdealPrfForTesting = v;
            return this;
        }

        public Builder setGroupBitLength(int groupBitLength) {
            this.groupBitLength = groupBitLength;
            return this;
        }

        @Override
        public Hn12PsuConfig build() {
            if (!enableSemiHonestDebug) {
                throw new UnsupportedOperationException(
                    "JOC:HazNis12 malicious mode (enableSemiHonestDebug=false) is not implemented: "
                        + "πCOUNT/πNZ verification and production OPRF are incomplete. "
                        + "Use the semi-honest/debug path (default)."
                );
            }
            return new Hn12PsuConfig(this);
        }
    }
}
