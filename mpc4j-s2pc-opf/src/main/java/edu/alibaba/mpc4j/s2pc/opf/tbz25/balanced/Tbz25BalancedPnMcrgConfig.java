package edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.cgs22.Cgs22PeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg.Tbz25NecRgConfig;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg.Tbz25PecRgDdhConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

/**
 * Configuration for {@link Tbz25BalancedPnMcrgServer} / {@link Tbz25BalancedPnMcrgClient}.
 * <p>
 * <strong>ssPEQT backend (May 2026 paper audit):</strong> the default ssPEQT inside nECRG is now
 * {@link Cgs22PeqtConfig CGS22} — Chandran–Gupta–Shah, "Circuit-PSI With Linear Complexity via Relaxed
 * Batch OPPRF" (PETS 2022), exactly reference [12] cited in the TBZ25 paper. Prior to May 2026 the
 * default was the generic {@code NaivePeqtConfig} (boolean Z2c EQ-tree), which is functionally
 * equivalent but uses a different construction than the volePSI/CGS22 OPPRF-based ssPEQT the authors'
 * artifact links against. Pass a custom {@link PeqtConfig} via the
 * {@link Builder#Builder(Rs21MpOprfConfig, Tbz25PecRgDdhConfig, Tbz25NecRgConfig)} constructor to revert.
 * </p>
 */
public class Tbz25BalancedPnMcrgConfig extends AbstractMultiPartyPtoConfig {
    private final Rs21MpOprfConfig rs21MpOprfConfig;
    private final Tbz25PecRgDdhConfig pecRgDdhConfig;
    private final Tbz25NecRgConfig necrgConfig;

    private Tbz25BalancedPnMcrgConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.rs21MpOprfConfig, builder.pecRgDdhConfig, builder.necrgConfig);
        rs21MpOprfConfig = builder.rs21MpOprfConfig;
        pecRgDdhConfig = builder.pecRgDdhConfig;
        necrgConfig = builder.necrgConfig;
    }

    public Rs21MpOprfConfig getRs21MpOprfConfig() {
        return rs21MpOprfConfig;
    }

    public Tbz25PecRgDdhConfig getPecRgDdhConfig() {
        return pecRgDdhConfig;
    }

    public Tbz25NecRgConfig getNecRgConfig() {
        return necrgConfig;
    }

    public static Tbz25BalancedPnMcrgConfig createDefault(SecurityModel securityModel, boolean silent) {
        Rs21MpOprfConfig rs21 = new Rs21MpOprfConfig.Builder(securityModel).build();
        Tbz25PecRgDdhConfig pec = new Tbz25PecRgDdhConfig.Builder().build();
        // CGS22 = paper reference [12] (Chandran–Gupta–Shah, PETS 2022). Linear-complexity OPPRF-based
        // ssPEQT, structurally closer to the volePSI ssPEQT the authors' Zenodo artifact uses than the
        // legacy NaivePeqt (boolean EQ-tree). See class-level Javadoc for the audit context.
        PeqtConfig peqt = new Cgs22PeqtConfig.Builder(securityModel, silent).build();
        CoreCotConfig cot = CoreCotFactory.createDefaultConfig(securityModel);
        Tbz25NecRgConfig necrg = new Tbz25NecRgConfig.Builder(peqt, cot).build();
        return new Builder(rs21, pec, necrg).build();
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tbz25BalancedPnMcrgConfig> {
        private Rs21MpOprfConfig rs21MpOprfConfig;
        private Tbz25PecRgDdhConfig pecRgDdhConfig;
        private Tbz25NecRgConfig necrgConfig;

        public Builder(Rs21MpOprfConfig rs21MpOprfConfig, Tbz25PecRgDdhConfig pecRgDdhConfig, Tbz25NecRgConfig necrgConfig) {
            this.rs21MpOprfConfig = rs21MpOprfConfig;
            this.pecRgDdhConfig = pecRgDdhConfig;
            this.necrgConfig = necrgConfig;
        }

        @Override
        public Tbz25BalancedPnMcrgConfig build() {
            return new Tbz25BalancedPnMcrgConfig(this);
        }
    }
}
