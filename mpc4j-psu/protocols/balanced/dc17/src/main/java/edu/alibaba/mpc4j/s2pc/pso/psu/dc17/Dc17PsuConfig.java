package edu.alibaba.mpc4j.s2pc.pso.psu.dc17;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.crypto.phe.PheSecLevel;
import edu.alibaba.mpc4j.crypto.phe.PheType;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
/**
 * ACISP_DavCid17 (Davidson-Cid 2017) EIBF-based PSU config.
 */
public class Dc17PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * false positive probability epsilon, default 2^-30.
     */
    private final double epsilon;
    /**
     * PHE scheme: use Paillier (PAI99) by default.
     */
    private final PheType pheType;
    /**
     * PHE security level.
     */
    private final PheSecLevel pheSecLevel;

    private Dc17PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
        epsilon = builder.epsilon;
        pheType = builder.pheType;
        pheSecLevel = builder.pheSecLevel;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.ACISP_DavCid17;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public PheType getPheType() {
        return pheType;
    }

    public PheSecLevel getPheSecLevel() {
        return pheSecLevel;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Dc17PsuConfig> {
        private double epsilon;
        private PheType pheType;
        private PheSecLevel pheSecLevel;

        public Builder() {
            epsilon = Math.pow(2, -30);
            pheType = PheType.PAI99;
            pheSecLevel = PheSecLevel.LAMBDA_80;
        }

        public Builder setEpsilon(double epsilon) {
            this.epsilon = epsilon;
            return this;
        }

        public Builder setPheSecLevel(PheSecLevel pheSecLevel) {
            this.pheSecLevel = pheSecLevel;
            return this;
        }

        @Override
        public Dc17PsuConfig build() {
            return new Dc17PsuConfig(this);
        }
    }
}

