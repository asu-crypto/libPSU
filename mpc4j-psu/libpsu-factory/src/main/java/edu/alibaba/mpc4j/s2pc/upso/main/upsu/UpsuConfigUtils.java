package edu.alibaba.mpc4j.s2pc.upso.main.upsu;

import edu.alibaba.libpsu.api.ProtocolNames;
import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.lll24.Lll24DosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.gmr21.Gmr21NetRosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.ms13.Ms13NetRosnConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23ByteEccDdhPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23EccDdhPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23PsOprfPmPeqtConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuConfig;

import java.util.Properties;

/**
 * UPSU config utils.
 *
 * @author Liqiang Peng
 * @date 2024/3/29
 */
public class UpsuConfigUtils {
    /** Same value as {@code UpsuMain.PTO_NAME_KEY}. */
    public static final String UPSU_PTO_NAME_KEY = "upsu_pto_name";
    /**
     * Legacy PSU fair-bench key (same protocol names as {@link UpsuMainType#TCL23}, etc.).
     */
    public static final String LEGACY_PSU_PTO_NAME_KEY = "psu_pto_name";
    /**
     * TCL23 pm-PEQT: {@code BYTE_ECC_DDH} (default) or {@code PS_OPRF}.
     */
    public static final String TCL23_PMPEQT_KEY = "tcl23_pmpeqt";

    /**
     * private constructor.
     */
    private UpsuConfigUtils() {
        // empty
    }

    /**
     * create config.
     *
     * @param properties properties.
     * @return config.
     */
    public static UpsuConfig createConfig(Properties properties) {
        UpsuMainType upsuMainType = readUpsuMainType(properties);
        switch (upsuMainType) {
            case TCL23_BYTE_ECC_DDH:
            case TCL23:
                return createTcl23UpsuConfig(properties, true);
            case TCL23_ECC_DDH:
                return new Tcl23UpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23EccDdhPmPeqtConfig.Builder().setCompressEncode(false).build())
                    .build();
            case TCL23_PS_OPRF_MS13:
                return new Tcl23UpsuConfig.Builder()
                    .setPmPeqtConfig(new Tcl23PsOprfPmPeqtConfig.Builder()
                        .setOsnConfig(new Lll24DosnConfig.Builder(new Ms13NetRosnConfig.Builder(false).build()).build())
                        .build())
                    .build();
            case TCL23_PS_OPRF_GMR21:
                return createTcl23UpsuConfig(properties, false);
            case USENIX_BinYujConYanYu25:
                return createTbz25UpsuConfig(properties);
            default:
                throw new IllegalArgumentException("Invalid " + UpsuMainType.class.getSimpleName() + ": " + upsuMainType.name());
        }
    }

    private static UpsuMainType readUpsuMainType(Properties properties) {
        String raw;
        if (properties.containsKey(UPSU_PTO_NAME_KEY)) {
            raw = properties.getProperty(UPSU_PTO_NAME_KEY);
        } else if (properties.containsKey(LEGACY_PSU_PTO_NAME_KEY)) {
            raw = properties.getProperty(LEGACY_PSU_PTO_NAME_KEY);
        } else {
            throw new IllegalArgumentException(
                "Missing UPSU protocol name: set " + UPSU_PTO_NAME_KEY + " or " + LEGACY_PSU_PTO_NAME_KEY
            );
        }
        String canonical = ProtocolNames.canonicalize(raw);
        if (ProtocolNames.CCS_TCLZ23.equals(canonical)) {
            return UpsuMainType.TCL23;
        }
        if (ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25.equals(canonical)) {
            return UpsuMainType.USENIX_BinYujConYanYu25;
        }
        return UpsuMainType.valueOf(raw.trim());
    }

    private static Tcl23UpsuConfig createTcl23UpsuConfig(Properties properties, boolean defaultByteEccDdh) {
        Tcl23UpsuConfig.Builder builder = new Tcl23UpsuConfig.Builder();
        if (properties.containsKey(TCL23_PMPEQT_KEY)) {
            String variant = properties.getProperty(TCL23_PMPEQT_KEY).trim();
            if ("PS_OPRF".equalsIgnoreCase(variant)) {
                builder.setPmPeqtConfig(new Tcl23PsOprfPmPeqtConfig.Builder()
                    .setOsnConfig(new Lll24DosnConfig.Builder(new Gmr21NetRosnConfig.Builder(false).build()).build())
                    .build());
            } else if ("BYTE_ECC_DDH".equalsIgnoreCase(variant)) {
                builder.setPmPeqtConfig(new Tcl23ByteEccDdhPmPeqtConfig.Builder().build());
            } else {
                throw new IllegalArgumentException("Invalid tcl23_pmpeqt: " + variant);
            }
        } else if (defaultByteEccDdh) {
            builder.setPmPeqtConfig(new Tcl23ByteEccDdhPmPeqtConfig.Builder().build());
        } else {
            builder.setPmPeqtConfig(new Tcl23PsOprfPmPeqtConfig.Builder()
                .setOsnConfig(new Lll24DosnConfig.Builder(new Gmr21NetRosnConfig.Builder(false).build()).build())
                .build());
        }
        return builder.build();
    }

    private static Tbz25UpsuConfig createTbz25UpsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        return new Tbz25UpsuConfig.Builder(silent).build();
    }
}
