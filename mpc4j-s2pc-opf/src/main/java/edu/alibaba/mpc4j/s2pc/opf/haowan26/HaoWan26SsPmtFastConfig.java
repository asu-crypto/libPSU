package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvsFactory.Gf2eDokvsType;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26AltModExpand.ExpandProfile;
import edu.alibaba.mpc4j.s2pc.pcg.ot.conv32.Conv32Factory.Conv32Type;

/**
 * Configuration for {@link HaoWan26SsPmtFastServer} / {@link HaoWan26SsPmtFastClient}.
 */
public class HaoWan26SsPmtFastConfig extends AbstractMultiPartyPtoConfig {
    private final F32SowOprfConfig f32SowOprfConfig;
    private final PeqtConfig peqtConfig;
    private final Gf2eDokvsType gf2eDokvsType;
    private final ExpandProfile expandProfile;
    /**
     * When true, force OKVS / PEQT length to a full 128-bit block (conservative mode).
     */
    private final boolean fullOutputLength;

    private HaoWan26SsPmtFastConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.f32SowOprfConfig, builder.peqtConfig);
        f32SowOprfConfig = builder.f32SowOprfConfig;
        peqtConfig = builder.peqtConfig;
        gf2eDokvsType = builder.gf2eDokvsType;
        expandProfile = builder.expandProfile;
        fullOutputLength = builder.fullOutputLength;
    }

    public F32SowOprfConfig getF32SowOprfConfig() {
        return f32SowOprfConfig;
    }

    public PeqtConfig getPeqtConfig() {
        return peqtConfig;
    }

    public Gf2eDokvsType getGf2eDokvsType() {
        return gf2eDokvsType;
    }

    public ExpandProfile getExpandProfile() {
        return expandProfile;
    }

    public boolean isFullOutputLength() {
        return fullOutputLength;
    }

    public static HaoWan26SsPmtFastConfig createDefault(SecurityModel securityModel, boolean silent) {
        F32SowOprfConfig f32 = F32SowOprfFactory.createDefaultConfig(Conv32Type.CCOT);
        PeqtConfig peqt = new Cgs22PeqtConfig.Builder(securityModel, silent).build();
        return new Builder(f32, peqt).build();
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<HaoWan26SsPmtFastConfig> {
        private final F32SowOprfConfig f32SowOprfConfig;
        private final PeqtConfig peqtConfig;
        private Gf2eDokvsType gf2eDokvsType;
        private ExpandProfile expandProfile;
        private boolean fullOutputLength;

        public Builder(F32SowOprfConfig f32SowOprfConfig, PeqtConfig peqtConfig) {
            this.f32SowOprfConfig = f32SowOprfConfig;
            this.peqtConfig = peqtConfig;
            this.gf2eDokvsType = Gf2eDokvsType.H3_SPARSE_CLUSTER_BLAZE_GCT;
            this.expandProfile = ExpandProfile.SECURE_JOIN_COMPAT;
            this.fullOutputLength = false;
        }

        public Builder setGf2eDokvsType(Gf2eDokvsType gf2eDokvsType) {
            this.gf2eDokvsType = gf2eDokvsType;
            return this;
        }

        public Builder setExpandProfile(ExpandProfile expandProfile) {
            this.expandProfile = expandProfile;
            return this;
        }

        public Builder setFullOutputLength(boolean fullOutputLength) {
            this.fullOutputLength = fullOutputLength;
            return this;
        }

        @Override
        public HaoWan26SsPmtFastConfig build() {
            return new HaoWan26SsPmtFastConfig(this);
        }
    }
}
