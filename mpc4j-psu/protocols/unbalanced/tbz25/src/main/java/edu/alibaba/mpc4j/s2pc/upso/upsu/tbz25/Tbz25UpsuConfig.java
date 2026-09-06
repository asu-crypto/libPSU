package edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuType;

/**
 * USENIX_BinYujConYanYu25 UPSU config — wraps balanced pnMCRG + OTP ({@link Tbz25PsuConfig}).
 * <p>
 * Supports unequal sender/receiver sizes (linear in both). Paper sublinear FHE MCRG (Fig. 14) is not implemented.
 * </p>
 */
public class Tbz25UpsuConfig extends AbstractMultiPartyPtoConfig implements UpsuConfig {
    private final Tbz25PsuConfig psuConfig;

    private Tbz25UpsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
        psuConfig = builder.psuConfig;
    }

    @Override
    public UpsuType getPtoType() {
        return UpsuType.USENIX_BinYujConYanYu25;
    }

    public Tbz25PsuConfig getPsuConfig() {
        return psuConfig;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tbz25UpsuConfig> {
        private Tbz25PsuConfig psuConfig;

        public Builder(boolean silent) {
            psuConfig = new Tbz25PsuConfig.Builder(silent).build();
        }

        public Builder setPsuConfig(Tbz25PsuConfig psuConfig) {
            this.psuConfig = psuConfig;
            return this;
        }

        @Override
        public Tbz25UpsuConfig build() {
            return new Tbz25UpsuConfig(this);
        }
    }
}
