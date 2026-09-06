package edu.alibaba.mpc4j.s2pc.pso.main.psi;

import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiType;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiConfig;

import java.util.Properties;

/**
 * PSI protocol config helpers.
 */
public class PsiConfigUtils {
    public static final String PSI_PTO_NAME_KEY = "psi_pto_name";

    private PsiConfigUtils() {
        // empty
    }

    public static PsiConfig createConfig(Properties properties) {
        String rawName = properties.getProperty(PSI_PTO_NAME_KEY);
        PsiType type = PsiType.fromProtocolId(rawName);
        switch (type) {
            case C_KisSon05:
                return createKs05PsiConfig(properties);
            case JOC_HazNis12:
                return new Hn12PsiConfig.Builder().setUseIdealPrfForTesting(true).build();
            default:
                throw new IllegalArgumentException("Invalid PsiType: " + type.protocolId());
        }
    }

    private static Ks05PsiConfig createKs05PsiConfig(Properties properties) {
        int maxSetSize = PropertiesUtils.readInt(
            properties, "ks05_max_set_size", Ks05PsiConfig.DEFAULT_MAX_SET_SIZE
        );
        return new Ks05PsiConfig.Builder()
            .setMaxSetSize(maxSetSize)
            .build();
    }
}
