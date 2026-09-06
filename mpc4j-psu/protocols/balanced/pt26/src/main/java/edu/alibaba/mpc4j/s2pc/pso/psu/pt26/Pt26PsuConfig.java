package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * EUROCRYPT_PisTri26 PSU config (Piske &amp; Trieu, EUROCRYPT 2026: IBLT + UnionPeel).
 * <p>
 * Per the paper's §6 optimization, the OPRF is now a <em>multi-point</em> OPRF run once
 * on the client's input set during the online setup; per-round OPRF traffic is zero.
 * </p>
 * <p>
 * Default MP-OPRF is RS21 (Rindal-Schoppmann VOLE-OPRF), the mpc4j port of the
 * {@code volePSI::RsOprf} primitive used by the authors' reference implementation
 * ({@code asu-crypto/IBLT-based-PSU}). It is built on the WYKW21 GF2K-NC-VOLE with the
 * Blazing-fast cluster GCT OKVS (the same construction class as the paper).
 * </p>
 * <p>
 * Default CoreCOT is ALSZ13 (mpc4j's semi-honest CoreCotFactory default). The authors'
 * implementation uses {@code libOTe::SoftSpokenShOt} with {@code FIELD_BITS = 2}; that
 * primitive is not yet available in mpc4j-s2pc-pcg, so the base OT remains an explicit
 * proxy and the resulting benchmark should be labeled accordingly.
 * </p>
 */
public class Pt26PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    private final MpOprfConfig mpOprfConfig;
    private final CoreCotConfig coreCotConfig;

    private Pt26PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.mpOprfConfig, builder.coreCotConfig);
        mpOprfConfig = builder.mpOprfConfig;
        coreCotConfig = builder.coreCotConfig;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.EUROCRYPT_PisTri26;
    }

    public MpOprfConfig getMpOprfConfig() {
        return mpOprfConfig;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Pt26PsuConfig> {
        private MpOprfConfig mpOprfConfig;
        private CoreCotConfig coreCotConfig;

        public Builder() {
            // RS21 MP-OPRF (Rindal-Schoppmann VOLE-OPRF) — exact mpc4j analogue of the authors'
            // volePSI::RsOprf. Inner GF2K-NC-VOLE backend is forced to semi-honest to match the
            // authors' semi-honest evaluation setting; the RS21 wrapper itself is malicious-secure
            // but composes down to SEMI_HONEST via AbstractMultiPartyPtoConfig's min-security rule.
            mpOprfConfig = new Rs21MpOprfConfig.Builder(SecurityModel.SEMI_HONEST).build();
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setMpOprfConfig(MpOprfConfig mpOprfConfig) {
            this.mpOprfConfig = mpOprfConfig;
            return this;
        }

        public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
            this.coreCotConfig = coreCotConfig;
            return this;
        }

        @Override
        public Pt26PsuConfig build() {
            return new Pt26PsuConfig(this);
        }
    }
}
