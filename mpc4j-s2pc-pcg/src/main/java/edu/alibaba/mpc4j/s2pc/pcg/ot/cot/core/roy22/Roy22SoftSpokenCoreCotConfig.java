package edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory.CoreCotType;

/**
 * Roy22 SoftSpoken core COT configuration.
 * <p>
 * <strong>Status:</strong> scaffolding only. The cryptographic body of the sender/receiver pair is
 * not implemented yet (see {@link Roy22SoftSpokenCoreCotPtoDesc} class-level Javadoc and the package
 * {@code README.md}). The config exists today so that downstream consumers (PT26, TBZ25 nECRG, the
 * silent-OT setup used by CSS25) can already declare the SoftSpoken option in their own builders,
 * but invoking the sender/receiver will fail loudly with {@link UnsupportedOperationException} until
 * the body is filled in.
 * </p>
 *
 * <h3>Parameters</h3>
 * <ul>
 *     <li>{@link #fieldBits} ({@code k}): the subspace VOLE small-field exponent. libOTe's reference
 *         {@code SoftSpokenShOt} defaults to {@code FIELD_BITS = 2} (q = 4); larger k = fewer base
 *         OTs but more bandwidth per row. Allowed range: {@code [1, 8]} (libOTe checks the same
 *         bound). {@code k = 1} is the degenerate IKNP-equivalent case and is provided mainly for
 *         testing.</li>
 *     <li>{@link #baseOtConfig}: the underlying base OT. Roy22 needs {@code (2^k - 1) * kappa} base
 *         OT instances per init, vs ALSZ13's {@code kappa} — so the base OT cost dominates when
 *         {@code num} is small, but amortizes away at our PSU batch sizes.</li>
 * </ul>
 *
 * @author audit follow-up #5 (May 26, 2026)
 */
public class Roy22SoftSpokenCoreCotConfig extends AbstractMultiPartyPtoConfig implements CoreCotConfig {
    /**
     * Default small-field exponent {@code k}. Matches libOTe's {@code FIELD_BITS = 2}.
     */
    public static final int DEFAULT_FIELD_BITS = 2;
    /**
     * Maximum small-field exponent. libOTe also caps at 8 — beyond that the {@code 2^k - 1} base-OT
     * cost dominates everything.
     */
    public static final int MAX_FIELD_BITS = 8;

    /**
     * Underlying base OT.
     */
    private final BaseOtConfig baseOtConfig;
    /**
     * Subspace VOLE small-field exponent ({@code k} in Roy22 / {@code FIELD_BITS} in libOTe).
     */
    private final int fieldBits;

    private Roy22SoftSpokenCoreCotConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.baseOtConfig);
        baseOtConfig = builder.baseOtConfig;
        fieldBits = builder.fieldBits;
    }

    public BaseOtConfig getBaseOtConfig() {
        return baseOtConfig;
    }

    /**
     * Returns the small-field exponent {@code k} (Roy22 §2 / libOTe {@code FIELD_BITS}). Subspace VOLE
     * runs over GF(2^k).
     */
    public int getFieldBits() {
        return fieldBits;
    }

    @Override
    public CoreCotType getPtoType() {
        return CoreCotType.ROY22_SOFT_SPOKEN;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Roy22SoftSpokenCoreCotConfig> {
        private BaseOtConfig baseOtConfig;
        private int fieldBits = DEFAULT_FIELD_BITS;

        public Builder() {
            baseOtConfig = BaseOtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
        }

        public Builder setBaseOtConfig(BaseOtConfig baseOtConfig) {
            this.baseOtConfig = baseOtConfig;
            return this;
        }

        /**
         * Sets {@code k} (libOTe's {@code FIELD_BITS}). Default is {@value #DEFAULT_FIELD_BITS}.
         */
        public Builder setFieldBits(int fieldBits) {
            MathPreconditions.checkPositiveInRangeClosed("fieldBits", fieldBits, MAX_FIELD_BITS);
            this.fieldBits = fieldBits;
            return this;
        }

        @Override
        public Roy22SoftSpokenCoreCotConfig build() {
            return new Roy22SoftSpokenCoreCotConfig(this);
        }
    }
}
