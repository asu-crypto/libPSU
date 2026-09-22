package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotFactory;

/**
 * Configuration for {@link HaoWan26SsOtdServer} / {@link HaoWan26SsOtdClient}.
 */
public class HaoWan26SsOtdConfig extends AbstractMultiPartyPtoConfig {
    private final CotConfig cotConfig;

    private HaoWan26SsOtdConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.cotConfig);
        cotConfig = builder.cotConfig;
    }

    public CotConfig getCotConfig() {
        return cotConfig;
    }

    public static HaoWan26SsOtdConfig createDefault(SecurityModel securityModel, boolean silent) {
        return new Builder(CotFactory.createDefaultConfig(securityModel, silent)).build();
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<HaoWan26SsOtdConfig> {
        private final CotConfig cotConfig;

        public Builder(CotConfig cotConfig) {
            this.cotConfig = cotConfig;
        }

        @Override
        public HaoWan26SsOtdConfig build() {
            return new HaoWan26SsOtdConfig(this);
        }
    }
}
