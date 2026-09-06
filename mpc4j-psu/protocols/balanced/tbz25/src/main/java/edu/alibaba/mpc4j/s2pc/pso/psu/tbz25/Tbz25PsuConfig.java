package edu.alibaba.mpc4j.s2pc.pso.psu.tbz25;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced.Tbz25BalancedPnMcrgConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * USENIX_BinYujConYanYu25 balanced ePSU config (pnMCRG + OTP).
 */
public class Tbz25PsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    private final Tbz25BalancedPnMcrgConfig pnMcrgConfig;
    private final CuckooHashBinType cuckooHashBinType;

    private Tbz25PsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.pnMcrgConfig);
        pnMcrgConfig = builder.pnMcrgConfig;
        cuckooHashBinType = builder.cuckooHashBinType;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.USENIX_BinYujConYanYu25;
    }

    public Tbz25BalancedPnMcrgConfig getPnMcrgConfig() {
        return pnMcrgConfig;
    }

    public CuckooHashBinType getCuckooHashBinType() {
        return cuckooHashBinType;
    }

    public int getCuckooHashNum() {
        return CuckooHashBinFactory.getHashNum(cuckooHashBinType);
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tbz25PsuConfig> {
        private Tbz25BalancedPnMcrgConfig pnMcrgConfig;
        private CuckooHashBinType cuckooHashBinType;

        public Builder(boolean silent) {
            pnMcrgConfig = Tbz25BalancedPnMcrgConfig.createDefault(SecurityModel.SEMI_HONEST, silent);
            cuckooHashBinType = CuckooHashBinFactory.CuckooHashBinType.NO_STASH_PSZ18_3_HASH;
        }

        public Builder setPnMcrgConfig(Tbz25BalancedPnMcrgConfig pnMcrgConfig) {
            this.pnMcrgConfig = pnMcrgConfig;
            return this;
        }

        public Builder setCuckooHashBinType(CuckooHashBinType cuckooHashBinType) {
            this.cuckooHashBinType = cuckooHashBinType;
            return this;
        }

        @Override
        public Tbz25PsuConfig build() {
            return new Tbz25PsuConfig(this);
        }
    }
}
