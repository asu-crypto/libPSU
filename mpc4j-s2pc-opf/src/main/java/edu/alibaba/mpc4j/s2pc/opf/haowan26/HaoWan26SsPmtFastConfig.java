package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;

/**
 * Configuration for {@link HaoWan26SsPmtFastServer} / {@link HaoWan26SsPmtFastClient}.
 */
public class HaoWan26SsPmtFastConfig extends AbstractMultiPartyPtoConfig {
    private final Rs21MpOprfConfig rs21MpOprfConfig;
    private final PeqtConfig peqtConfig;

    private HaoWan26SsPmtFastConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.rs21MpOprfConfig, builder.peqtConfig);
        rs21MpOprfConfig = builder.rs21MpOprfConfig;
        peqtConfig = builder.peqtConfig;
    }

    public Rs21MpOprfConfig getRs21MpOprfConfig() {
        return rs21MpOprfConfig;
    }

    public PeqtConfig getPeqtConfig() {
        return peqtConfig;
    }

    public static HaoWan26SsPmtFastConfig createDefault(SecurityModel securityModel, boolean silent) {
        Rs21MpOprfConfig rs21 = new Rs21MpOprfConfig.Builder(securityModel).build();
        PeqtConfig peqt = new Cgs22PeqtConfig.Builder(securityModel, silent).build();
        return new Builder(rs21, peqt).build();
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<HaoWan26SsPmtFastConfig> {
        private final Rs21MpOprfConfig rs21MpOprfConfig;
        private final PeqtConfig peqtConfig;

        public Builder(Rs21MpOprfConfig rs21MpOprfConfig, PeqtConfig peqtConfig) {
            this.rs21MpOprfConfig = rs21MpOprfConfig;
            this.peqtConfig = peqtConfig;
        }

        @Override
        public HaoWan26SsPmtFastConfig build() {
            return new HaoWan26SsPmtFastConfig(this);
        }
    }
}
