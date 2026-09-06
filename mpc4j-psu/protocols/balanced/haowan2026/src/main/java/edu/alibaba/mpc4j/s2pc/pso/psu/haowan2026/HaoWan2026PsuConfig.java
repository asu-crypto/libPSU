package edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsOtdConfig;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26SsPmtFastConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * Hao–Wan 2026 balanced ePSU-fast config (ssPMT-fast + ssOTd).
 */
public class HaoWan2026PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    private final HaoWan26SsPmtFastConfig ssPmtFastConfig;
    private final HaoWan26SsOtdConfig ssOtdConfig;

    private HaoWan2026PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.ssPmtFastConfig, builder.ssOtdConfig);
        ssPmtFastConfig = builder.ssPmtFastConfig;
        ssOtdConfig = builder.ssOtdConfig;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.USENIX_HaoWan26;
    }

    public HaoWan26SsPmtFastConfig getSsPmtFastConfig() {
        return ssPmtFastConfig;
    }

    public HaoWan26SsOtdConfig getSsOtdConfig() {
        return ssOtdConfig;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<HaoWan2026PsuConfig> {
        private HaoWan26SsPmtFastConfig ssPmtFastConfig;
        private HaoWan26SsOtdConfig ssOtdConfig;

        public Builder(boolean silent) {
            ssPmtFastConfig = HaoWan26SsPmtFastConfig.createDefault(SecurityModel.SEMI_HONEST, silent);
            ssOtdConfig = HaoWan26SsOtdConfig.createDefault(SecurityModel.SEMI_HONEST);
        }

        public Builder setSsPmtFastConfig(HaoWan26SsPmtFastConfig ssPmtFastConfig) {
            this.ssPmtFastConfig = ssPmtFastConfig;
            return this;
        }

        public Builder setSsOtdConfig(HaoWan26SsOtdConfig ssOtdConfig) {
            this.ssOtdConfig = ssOtdConfig;
            return this;
        }

        @Override
        public HaoWan2026PsuConfig build() {
            return new HaoWan2026PsuConfig(this);
        }
    }
}
