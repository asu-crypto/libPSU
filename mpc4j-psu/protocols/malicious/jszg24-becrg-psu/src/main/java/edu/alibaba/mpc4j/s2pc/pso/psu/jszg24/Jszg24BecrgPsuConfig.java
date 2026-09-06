package edu.alibaba.mpc4j.s2pc.pso.psu.jszg24;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtConfig;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.opf.opprf.batch.BopprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.opprf.batch.BopprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * JSZG24 bECRG PSU config (Fig.17).
 */
public class Jszg24BecrgPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * item bit length (ℓ1). This is also the ciphertext / pad byte length used in Fig.17 Steps 4-7.
     */
    private final int itemBitLength;
    /**
     * statistical security parameter (λ) used for OPPRF length (ℓ2).
     */
    private final int lambda;
    /**
     * number of hash functions (γ) for Cuckoo/simple hashing.
     */
    private final int gamma;
    /**
     * Cuckoo/simple hash expansion factor (ε). Bin num b ≈ ε · n1.
     */
    private final double epsilon;
    /**
     * no-stash cuckoo hash type (must have stash size 0).
     */
    private final CuckooHashBinFactory.CuckooHashBinType cuckooHashBinType;
    /**
     * maximum cuckoo retries with fresh keys before aborting.
     */
    private final int maxCuckooRetry;
    /**
     * batch OPPRF config (Π_OPPRF).
     */
    private final BopprfConfig bopprfConfig;
    /**
     * PET config (Π_PET via PEQT).
     */
    private final PeqtConfig peqtConfig;
    /**
     * OT extension config used to implement eqOTe (Π_eqOTe).
     */
    private final LnotConfig lnotConfig;
    /**
     * Permute+Share config (Π_PS via DOSN).
     */
    private final DosnConfig dosnConfig;

    private Jszg24BecrgPsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.bopprfConfig, builder.peqtConfig, builder.lnotConfig, builder.dosnConfig);
        itemBitLength = builder.itemBitLength;
        lambda = builder.lambda;
        gamma = builder.gamma;
        epsilon = builder.epsilon;
        cuckooHashBinType = builder.cuckooHashBinType;
        maxCuckooRetry = builder.maxCuckooRetry;
        bopprfConfig = builder.bopprfConfig;
        peqtConfig = builder.peqtConfig;
        lnotConfig = builder.lnotConfig;
        dosnConfig = builder.dosnConfig;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.USENIX_YanShiHonDaw24;
    }

    public int getItemBitLength() {
        return itemBitLength;
    }

    public int getLambda() {
        return lambda;
    }

    public int getGamma() {
        return gamma;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public CuckooHashBinFactory.CuckooHashBinType getCuckooHashBinType() {
        return cuckooHashBinType;
    }

    public int getMaxCuckooRetry() {
        return maxCuckooRetry;
    }

    public BopprfConfig getBopprfConfig() {
        return bopprfConfig;
    }

    public PeqtConfig getPeqtConfig() {
        return peqtConfig;
    }

    public LnotConfig getLnotConfig() {
        return lnotConfig;
    }

    public DosnConfig getDosnConfig() {
        return dosnConfig;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Jszg24BecrgPsuConfig> {
        /**
         * default item bit length (128-bit items).
         */
        private int itemBitLength;
        /**
         * default λ = 40 (paper).
         */
        private int lambda;
        /**
         * default γ = 3 (paper).
         */
        private int gamma;
        /**
         * default ε = 1.27 (paper).
         */
        private double epsilon;
        /**
         * no-stash cuckoo hash type.
         */
        private CuckooHashBinFactory.CuckooHashBinType cuckooHashBinType;
        /**
         * max cuckoo retries.
         */
        private int maxCuckooRetry;
        /**
         * batch OPPRF config.
         */
        private BopprfConfig bopprfConfig;
        /**
         * PET config.
         */
        private PeqtConfig peqtConfig;
        /**
         * LNOT config used for eqOTe.
         */
        private LnotConfig lnotConfig;
        /**
         * DOSN config used for Permute+Share.
         */
        private DosnConfig dosnConfig;

        public Builder(boolean silent) {
            itemBitLength = CommonConstants.BLOCK_BIT_LENGTH;
            lambda = CommonConstants.STATS_BIT_LENGTH;
            gamma = 3;
            epsilon = 1.27;
            cuckooHashBinType = CuckooHashBinFactory.CuckooHashBinType.NO_STASH_PSZ18_3_HASH;
            maxCuckooRetry = 40;
            bopprfConfig = BopprfFactory.createDefaultConfig();
            peqtConfig = PeqtFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, silent);
            lnotConfig = LnotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, silent);
            dosnConfig = DosnFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, silent);
        }

        public Builder setItemBitLength(int itemBitLength) {
            this.itemBitLength = itemBitLength;
            return this;
        }

        public Builder setLambda(int lambda) {
            this.lambda = lambda;
            return this;
        }

        public Builder setGamma(int gamma) {
            this.gamma = gamma;
            return this;
        }

        public Builder setEpsilon(double epsilon) {
            this.epsilon = epsilon;
            return this;
        }

        public Builder setCuckooHashBinType(CuckooHashBinFactory.CuckooHashBinType cuckooHashBinType) {
            this.cuckooHashBinType = cuckooHashBinType;
            return this;
        }

        public Builder setMaxCuckooRetry(int maxCuckooRetry) {
            this.maxCuckooRetry = maxCuckooRetry;
            return this;
        }

        public Builder setBopprfConfig(BopprfConfig bopprfConfig) {
            this.bopprfConfig = bopprfConfig;
            return this;
        }

        public Builder setPeqtConfig(PeqtConfig peqtConfig) {
            this.peqtConfig = peqtConfig;
            return this;
        }

        public Builder setLnotConfig(LnotConfig lnotConfig) {
            this.lnotConfig = lnotConfig;
            return this;
        }

        public Builder setDosnConfig(DosnConfig dosnConfig) {
            this.dosnConfig = dosnConfig;
            return this;
        }

        @Override
        public Jszg24BecrgPsuConfig build() {
            return new Jszg24BecrgPsuConfig(this);
        }
    }
}

