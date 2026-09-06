package edu.alibaba.mpc4j.s2pc.pso.psu.jsz22;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.lll24.Lll24DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.cm20.Cm20MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.iknp03.Iknp03CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.OoPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * JSZ22-SFS-PSU协议配置项。
 *
 * @author Weiran Liu
 * @date 2022/03/18
 */
public class Jsz22SfsPsuConfig extends AbstractMultiPartyPtoConfig implements OoPsuConfig {
    /**
     * OPRF协议配置项
     */
    private final OprfConfig oprfConfig;
    /**
     * OSN协议配置项
     */
    private final DosnConfig dosnConfig;
    /**
     * random-OSN
     */
    private final RosnConfig rosnConfig;
    /**
     * 布谷鸟哈希类型
     */
    private final CuckooHashBinType cuckooHashBinType;

    private Jsz22SfsPsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.oprfConfig, builder.dosnConfig);
        oprfConfig = builder.oprfConfig;
        dosnConfig = builder.dosnConfig;
        rosnConfig = builder.rosnConfig;
        cuckooHashBinType = builder.cuckooHashBinType;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.USENIX_JSZDG22_SFS;
    }

    public OprfConfig getOprfConfig() {
        return oprfConfig;
    }

    public DosnConfig getOsnConfig() {
        return dosnConfig;
    }

    public RosnConfig getRosnConfig() {
        return rosnConfig;
    }

    public CuckooHashBinType getCuckooHashBinType() {
        return cuckooHashBinType;
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Jsz22SfsPsuConfig> {
        /**
         * OPRF协议配置项
         */
        private final OprfConfig oprfConfig;
        /**
         * OSN协议配置项
         */
        private DosnConfig dosnConfig;
        /**
         * random-OSN
         */
        private RosnConfig rosnConfig;
        /**
         * 布谷鸟哈希类型
         */
        private CuckooHashBinType cuckooHashBinType;

        public Builder(boolean silent) {
            // JSZ22 uses the CM20 multi-point OPRF construction (Appendix B / Fig. 18) rather than KKRT16.
            // The authors' artifact wires this MP-OPRF on top of IKNP OT extension.
            // In mpc4j, CM20 MP-OPRF is modeled as an OPRFConfig and uses a CoreCOT inside.
            oprfConfig = new Cm20MpOprfConfig.Builder()
                .setCoreCotConfig(new Iknp03CoreCotConfig.Builder().build())
                .build();
            dosnConfig = DosnFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, silent);
            rosnConfig = RosnFactory.createDefaultConfig(SecurityModel.SEMI_HONEST, silent);
            // JSZ22 reference implementation (dujiajun/PSU) uses PSZ18 cuckoo hashing:
            //   - large/balanced: 4 hashes, scaler ε = 1.09
            //   - unbalanced:     3 hashes, scaler ε = 1.27
            // mpc4j's PSZ18 no-stash types bake these ε values into getBinNum().
            cuckooHashBinType = CuckooHashBinType.NO_STASH_PSZ18_4_HASH;
        }

        public Builder setCuckooHashBinType(CuckooHashBinType cuckooHashBinType) {
            this.cuckooHashBinType = cuckooHashBinType;
            return this;
        }

        public Builder setRosnConfig(RosnConfig rosnConfig) {
            this.rosnConfig = rosnConfig;
            this.dosnConfig = new Lll24DosnConfig.Builder(rosnConfig).build();
            return this;
        }

        @Override
        public Jsz22SfsPsuConfig build() {
            return new Jsz22SfsPsuConfig(this);
        }
    }
}
