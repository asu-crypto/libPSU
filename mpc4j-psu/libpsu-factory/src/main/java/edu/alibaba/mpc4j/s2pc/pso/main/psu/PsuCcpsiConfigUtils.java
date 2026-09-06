package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiFactory.CcpsiType;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.cgs22.Cgs22CcpsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.psty19.Psty19CcpsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.rs21.Rs21CcpsiConfig;

import java.util.Properties;

/**
 * CCPSI config helpers for ASIACCS_CSSW25 PSU bench files ({@code ccpsi_pto_name}).
 */
public class PsuCcpsiConfigUtils {
    public static final String CCPSI_PTO_NAME_KEY = "ccpsi_pto_name";

    private PsuCcpsiConfigUtils() {
    }

    public static CcpsiConfig createConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        CcpsiType type = MainPtoConfigUtils.readEnum(CcpsiType.class, properties, CCPSI_PTO_NAME_KEY);
        switch (type) {
            case PSTY19:
                return new Psty19CcpsiConfig.Builder(silent)
                    .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                    .build();
            case RS21:
                return new Rs21CcpsiConfig.Builder(silent)
                    .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                    .build();
            case CGS22:
                return new Cgs22CcpsiConfig.Builder(silent)
                    .setCuckooHashBinType(CuckooHashBinType.NO_STASH_PSZ18_3_HASH_E04)
                    .build();
            default:
                throw new IllegalArgumentException("Invalid " + CcpsiType.class.getSimpleName() + ": " + type.name());
        }
    }
}
