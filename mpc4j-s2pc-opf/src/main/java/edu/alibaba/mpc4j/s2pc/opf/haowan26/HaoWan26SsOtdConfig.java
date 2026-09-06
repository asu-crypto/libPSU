package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

/**
 * Configuration for {@link HaoWan26SsOtdServer} / {@link HaoWan26SsOtdClient}.
 */
public class HaoWan26SsOtdConfig extends AbstractMultiPartyPtoConfig {
    private final CoreCotConfig coreCotConfig;

    private HaoWan26SsOtdConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.coreCotConfig);
        coreCotConfig = builder.coreCotConfig;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public static HaoWan26SsOtdConfig createDefault(SecurityModel securityModel) {
        return new Builder(CoreCotFactory.createDefaultConfig(securityModel)).build();
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<HaoWan26SsOtdConfig> {
        private final CoreCotConfig coreCotConfig;

        public Builder(CoreCotConfig coreCotConfig) {
            this.coreCotConfig = coreCotConfig;
        }

        @Override
        public HaoWan26SsOtdConfig build() {
            return new HaoWan26SsOtdConfig(this);
        }
    }
}
