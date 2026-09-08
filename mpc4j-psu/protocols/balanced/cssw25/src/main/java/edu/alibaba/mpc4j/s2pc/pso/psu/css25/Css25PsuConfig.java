package edu.alibaba.mpc4j.s2pc.pso.psu.css25;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory.RosnType;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.cm20.Cm20MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22.Roy22SoftSpokenCoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.psty19.Psty19CcpsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.rs21.Rs21CcpsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * CSSW25 PSU config (Chandran et al., ASIACCS 2025).
 * <p>
 * Default runnable backends: CM20 multi-point OPRF [12], <b>PSTY19 circuit PSI</b>
 * (MPC4J's stable OPPRF+GMW CCPSI path) wired to a stash-less 3-hash Cuckoo table with the
 * paper's packing ε&nbsp;=&nbsp;0.4 (β&nbsp;=&nbsp;⌈1.4·n⌉, type
 * {@link CuckooHashBinType#NO_STASH_PSZ18_3_HASH_E04}), LLL24 flat ROSN ShTr, Fig. 12 CnP,
 * silent core-COT [8]. The RS21 CCPSI paper-comparison proxy remains available via
 * {@link Builder#setPaperComparisonProxy()}.
 * </p>
 * <p>
 * <strong>The β = ⌈1.4·n⌉ Cuckoo size is paper-faithful. The default PSTY19 backend is a
 * runnable engineering proxy, not the paper's RR22 / volePSI back-end.</strong> RS21 is
 * structurally closer to RR22 than PSTY19, but the current MPC4J RS21 CCPSI path is too slow for
 * routine ASIACCS_CSSW25 unit tests and default benchmark configs. The remaining defaults are not yet an
 * exact reproduction of the paper's implementation. Specifically:
 * <ul>
 *   <li>the paper's CCPSI back-end is RR22 [43] (volePSI) wired to silent OT [8]; RR22 is not
 *   implemented in MPC4J. {@link Builder#setPaperComparisonProxy()} wires RS21 as the closest
 *   available family member (OKVS + VOLE-OPRF + PEQT on Cuckoo/simple-hash bins), but that mode
 *   should be reported as "ASIACCS_CSSW25 / RS21 paper-comparison proxy", not as "ASIACCS_CSSW25 final";</li>
 *   <li>the paper's ShTr stage is Waksman-in-BetaCircuit + the alternating-moduli permutation
 *   correlation generator. The current LLL24-flat ROSN path is a placeholder and is the next
 *   structural follow-up commit;</li>
 *   <li>the paper's final OT is silent-OT-precomputed with Beaver derandomization and the
 *   one-correction optimization. The current CoreCOT online send is also a placeholder.</li>
 * </ul>
 * </p>
 */
public class Css25PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * Engineering proxy vs paper-exact wiring.
     */
    public enum Css25Mode {
        /**
         * Runnable engineering proxy (PSTY19 CCPSI fallback + ROSN + CoreCOT).
         */
        PROXY,
        /**
         * Best-available paper-comparison proxy: RS21 CCPSI + LLL24 flat ROSN + silent Roy22 CoreCOT.
         */
        PAPER_COMPARISON_PROXY,
        /**
         * Reserved paper-exact ASIACCS_CSSW25 stack.
         * <p>
         * This mode is intentionally not runnable yet: MPC4J still lacks the authors' exact
         * MPOPRF, RR22/volePSI RsCpsi backend, ShTr correlation generator, and final OT
         * derandomization/one-correction path.
         * </p>
         */
        PAPER_EXACT,
    }

    private final Css25Mode mode;
    private final MpOprfConfig mpOprfConfig;
    private final CcpsiConfig ccpsiConfig;
    private final RosnConfig rosnConfig;
    private final CoreCotConfig coreCotConfig;

    private Css25PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.mpOprfConfig, builder.ccpsiConfig, builder.rosnConfig, builder.coreCotConfig);
        mode = builder.mode;
        mpOprfConfig = builder.mpOprfConfig;
        ccpsiConfig = builder.ccpsiConfig;
        rosnConfig = builder.rosnConfig;
        coreCotConfig = builder.coreCotConfig;
    }

    public Css25Mode getMode() {
        return mode;
    }

    /**
     * Returns whether this config uses the best-available paper-comparison proxy stack.
     */
    public boolean isPaperComparisonProxy() {
        return mode == Css25Mode.PAPER_COMPARISON_PROXY;
    }

    /**
     * Returns whether this config requests the reserved paper-exact ASIACCS_CSSW25 stack.
     */
    public boolean isPaperExact() {
        return mode == Css25Mode.PAPER_EXACT;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.ASIACCS_CSSW25;
    }

    public static String paperExactUnsupportedMessage() {
        return "ASIACCS_CSSW25 paper-exact is not implemented yet. Required missing pieces: "
            + "authors' silent-OT MPOPRF, RR22/volePSI RsCpsi backend, "
            + "Waksman/BetaCircuit or alternating-moduli ShTr correlation generator, "
            + "and silent-OT-precomputed final OT with derandomization/one-correction. "
            + "Use ASIACCS_CSSW25 without css25_paper_exact for the runnable PSTY19 proxy, "
            + "or css25_paper_comparison=true for the slower RS21 comparison proxy.";
    }

    public MpOprfConfig getMpOprfConfig() {
        return mpOprfConfig;
    }

    public CcpsiConfig getCcpsiConfig() {
        return ccpsiConfig;
    }

    public RosnConfig getRosnConfig() {
        return rosnConfig;
    }

    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Css25PsuConfig> {
        private final boolean silentCot;
        private Css25Mode mode = Css25Mode.PROXY;
        private MpOprfConfig mpOprfConfig;
        private CcpsiConfig ccpsiConfig;
        private RosnConfig rosnConfig;
        private CoreCotConfig coreCotConfig;

        public Builder(boolean silentCot) {
            this.silentCot = silentCot;
            mpOprfConfig = new Cm20MpOprfConfig.Builder().build();
            // Paper-faithful Cuckoo packing: ε = 0.4 → β = ⌈1.4·n⌉. The default PSTY19 backend
            // is the stable runnable proxy; setPaperComparisonProxy() switches to RS21 while
            // keeping the same ASIACCS_CSSW25 Cuckoo table size.
            ccpsiConfig = new Psty19CcpsiConfig.Builder(silentCot)
                .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                .build();
            rosnConfig = RosnFactory.createRosnConfig(RosnType.LLL24_FLAT_NET, silentCot);
            coreCotConfig = new Roy22SoftSpokenCoreCotConfig.Builder().build();
        }

        /**
         * Wires the best-available paper-comparison proxy stack (RS21 CCPSI, LLL24 flat ROSN,
         * Roy22 silent CoreCOT). This is not a bit-exact ASIACCS_CSSW25 implementation.
         */
        public Builder setPaperComparisonProxy() {
            mode = Css25Mode.PAPER_COMPARISON_PROXY;
            ccpsiConfig = new Rs21CcpsiConfig.Builder(silentCot)
                .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                .build();
            rosnConfig = RosnFactory.createRosnConfig(RosnType.LLL24_FLAT_NET, silentCot);
            coreCotConfig = new Roy22SoftSpokenCoreCotConfig.Builder().build();
            return this;
        }

        /**
         * Requests the reserved paper-exact ASIACCS_CSSW25 stack.
         * <p>
         * The returned config reports {@link PsuType#ASIACCS_CSSW25}, but server/client construction
         * rejects it until the missing paper-exact sub-protocols are implemented.
         * </p>
         */
        public Builder setPaperExact() {
            mode = Css25Mode.PAPER_EXACT;
            return this;
        }

        /**
         * Explicitly labelled PSTY19 OPPRF+GMW fallback. This is also the default runnable ASIACCS_CSSW25
         * proxy. PSTY19 is from 2019 and uses a fundamentally different OPPRF+GMW construction;
         * it is structurally further from the paper's RR22 / volePSI back-end than RS21. This
         * setter remains useful after {@link #setPaperComparisonProxy()} when a caller wants to
         * switch back to the stable proxy.
         * <p>
         * The Cuckoo packing stays at the paper-faithful ε&nbsp;=&nbsp;0.4
         * ({@link CuckooHashBinType#NO_STASH_PSZ18_3_HASH_E04}, β&nbsp;=&nbsp;⌈1.4·n⌉) so the
         * swap isolates the OPRF/OKVS backend choice and does not also change the table size.
         * </p>
         * <p>
         * <strong>Do not use this configuration to report ASIACCS_CSSW25 "paper-comparison" numbers.</strong>
         * Report as "ASIACCS_CSSW25 / PSTY19 runnable proxy".
         * </p>
         *
         * @return this builder, with CCPSI replaced by {@link Psty19CcpsiConfig}.
         */
        public Builder usePsty19CcpsiFallback() {
            mode = Css25Mode.PROXY;
            this.ccpsiConfig = new Psty19CcpsiConfig.Builder(silentCot)
                .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                .build();
            return this;
        }

        /**
         * Legacy explicit RS21 swap. This is now a deprecated alias for
         * {@link #setPaperComparisonProxy()} so older {@code css25_rs21_proxy_ablation}
         * configs still select the RS21 comparison proxy.
         *
         * @return this builder.
         * @deprecated use {@link #setPaperComparisonProxy()}.
         */
        @Deprecated
        public Builder useRs21CcpsiProxyAblation() {
            mode = Css25Mode.PAPER_COMPARISON_PROXY;
            this.ccpsiConfig = new Rs21CcpsiConfig.Builder(silentCot)
                .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                .build();
            return this;
        }

        public Builder setMpOprfConfig(MpOprfConfig mpOprfConfig) {
            this.mpOprfConfig = mpOprfConfig;
            return this;
        }

        public Builder setCcpsiConfig(CcpsiConfig ccpsiConfig) {
            this.ccpsiConfig = ccpsiConfig;
            return this;
        }

        public Builder setRosnConfig(RosnConfig rosnConfig) {
            this.rosnConfig = rosnConfig;
            return this;
        }

        public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
            this.coreCotConfig = coreCotConfig;
            return this;
        }

        @Override
        public Css25PsuConfig build() {
            return new Css25PsuConfig(this);
        }
    }
}
