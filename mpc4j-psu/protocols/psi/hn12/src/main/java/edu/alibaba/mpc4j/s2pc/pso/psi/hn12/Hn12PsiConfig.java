package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiType;

/**
 * HN12 malicious PSI configuration.
 */
public class Hn12PsiConfig extends AbstractMultiPartyPtoConfig implements PsiConfig {
    private final int groupBitLength;
    private final boolean enableSemiHonestDebug;
    private final boolean useIdealPrfForTesting;
    private final boolean allowRetryOnBinOverflow;

    private Hn12PsiConfig(Builder builder) {
        super(builder.enableSemiHonestDebug ? SecurityModel.SEMI_HONEST : SecurityModel.MALICIOUS);
        groupBitLength = builder.groupBitLength;
        enableSemiHonestDebug = builder.enableSemiHonestDebug;
        useIdealPrfForTesting = builder.useIdealPrfForTesting;
        allowRetryOnBinOverflow = builder.allowRetryOnBinOverflow;
    }

    public int getGroupBitLength() {
        return groupBitLength;
    }

    public boolean isEnableSemiHonestDebug() {
        return enableSemiHonestDebug;
    }

    public boolean isUseIdealPrfForTesting() {
        return useIdealPrfForTesting;
    }

    public boolean isAllowRetryOnBinOverflow() {
        return allowRetryOnBinOverflow;
    }

    @Override
    public PsiType getPtoType() {
        return PsiType.JOC_HazNis12;
    }

    public static class Builder {
        private int groupBitLength = 512;
        private boolean enableSemiHonestDebug = false;
        private boolean useIdealPrfForTesting = true;
        private boolean allowRetryOnBinOverflow = true;

        public Builder setGroupBitLength(int groupBitLength) {
            this.groupBitLength = groupBitLength;
            return this;
        }

        public Builder setEnableSemiHonestDebug(boolean enableSemiHonestDebug) {
            this.enableSemiHonestDebug = enableSemiHonestDebug;
            return this;
        }

        public Builder setUseIdealPrfForTesting(boolean useIdealPrfForTesting) {
            this.useIdealPrfForTesting = useIdealPrfForTesting;
            return this;
        }

        public Builder setAllowRetryOnBinOverflow(boolean allowRetryOnBinOverflow) {
            this.allowRetryOnBinOverflow = allowRetryOnBinOverflow;
            return this;
        }

        public Hn12PsiConfig build() {
            if (!useIdealPrfForTesting && !enableSemiHonestDebug) {
                throw new IllegalStateException("production malicious HN12 requires real OPRF (not yet implemented)");
            }
            return new Hn12PsiConfig(this);
        }
    }
}
