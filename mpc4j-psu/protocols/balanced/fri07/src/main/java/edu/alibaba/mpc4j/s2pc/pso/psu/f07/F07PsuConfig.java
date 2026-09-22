package edu.alibaba.mpc4j.s2pc.pso.psu.f07;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.crypto.phe.PheSecLevel;
import edu.alibaba.mpc4j.crypto.phe.PheType;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * ACNS_Frikken07 PSU config.
 */
public class F07PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * PHE type (paper uses (threshold) Paillier; here we use standard Paillier).
     */
    private final PheType pheType;
    /**
     * PHE security level.
     */
    private final PheSecLevel pheSecLevel;

    private F07PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
        pheType = builder.pheType;
        pheSecLevel = builder.pheSecLevel;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.ACNS_Frikken07;
    }

    public PheType getPheType() {
        return pheType;
    }

    public PheSecLevel getPheSecLevel() {
        return pheSecLevel;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<F07PsuConfig> {
        private PheType pheType;
        private PheSecLevel pheSecLevel;

        public Builder() {
            pheType = PheType.PAI99;
            pheSecLevel = PheSecLevel.LAMBDA_128;
        }

        public Builder setPheType(PheType pheType) {
            this.pheType = pheType;
            return this;
        }

        public Builder setPheSecLevel(PheSecLevel pheSecLevel) {
            this.pheSecLevel = pheSecLevel;
            return this;
        }

        @Override
        public F07PsuConfig build() {
            return new F07PsuConfig(this);
        }
    }
}

