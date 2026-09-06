package edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;

/**
 * Configuration for TBZ25 nECRG (ssPEQT + ROT).
 */
public class Tbz25NecRgConfig extends AbstractMultiPartyPtoConfig {
    private final PeqtConfig peqtConfig;
    private final CoreCotConfig coreCotConfig;

    private Tbz25NecRgConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.peqtConfig, builder.coreCotConfig);
        peqtConfig = builder.peqtConfig;
        coreCotConfig = builder.coreCotConfig;
    }

    public PeqtConfig getPeqtConfig() {
        return peqtConfig;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tbz25NecRgConfig> {
        private PeqtConfig peqtConfig;
        private CoreCotConfig coreCotConfig;

        public Builder(PeqtConfig peqtConfig, CoreCotConfig coreCotConfig) {
            this.peqtConfig = peqtConfig;
            this.coreCotConfig = coreCotConfig;
        }

        @Override
        public Tbz25NecRgConfig build() {
            return new Tbz25NecRgConfig(this);
        }
    }
}
