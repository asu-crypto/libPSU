package edu.alibaba.mpc4j.s2pc.pso.psu.ks05;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.crypto.phe.PheSecLevel;
import edu.alibaba.mpc4j.crypto.phe.PheType;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * C:KisSon05 PSU config (Kissner–Song polynomial union with Paillier).
 */
public class Ks05PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * Default small-set guard. The current implementation is Paillier-polynomial based and quadratic in set size.
     */
    public static final int DEFAULT_MAX_SET_SIZE = 1 << 8;

    private final PheType pheType;
    private final PheSecLevel pheSecLevel;
    private final int maxSetSize;

    private Ks05PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
        pheType = builder.pheType;
        pheSecLevel = builder.pheSecLevel;
        maxSetSize = builder.maxSetSize;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.C_KisSon05;
    }

    public PheType getPheType() {
        return pheType;
    }

    public PheSecLevel getPheSecLevel() {
        return pheSecLevel;
    }

    public int getMaxSetSize() {
        return maxSetSize;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Ks05PsuConfig> {
        private PheType pheType;
        private PheSecLevel pheSecLevel;
        private int maxSetSize;

        public Builder() {
            pheType = PheType.PAI99;
            pheSecLevel = PheSecLevel.LAMBDA_128;
            maxSetSize = DEFAULT_MAX_SET_SIZE;
        }

        public Builder setPheType(PheType pheType) {
            this.pheType = pheType;
            return this;
        }

        public Builder setPheSecLevel(PheSecLevel pheSecLevel) {
            this.pheSecLevel = pheSecLevel;
            return this;
        }

        public Builder setMaxSetSize(int maxSetSize) {
            if (maxSetSize < 2) {
                throw new IllegalArgumentException("maxSetSize must be at least 2: " + maxSetSize);
            }
            this.maxSetSize = maxSetSize;
            return this;
        }

        @Override
        public Ks05PsuConfig build() {
            return new Ks05PsuConfig(this);
        }
    }
}
