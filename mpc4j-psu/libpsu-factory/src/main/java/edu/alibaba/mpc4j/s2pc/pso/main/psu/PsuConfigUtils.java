package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2e.Gf2eDokvsFactory.Gf2eDokvsType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnConfig;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory.RosnType;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtConfig;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.zcl23.Zcl23PkeMqRpmtConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.structure.filter.FilterFactory.FilterType;
import edu.alibaba.mpc4j.common.tool.utils.PropertiesUtils;
import edu.alibaba.mpc4j.s2pc.pso.PsuPaperFidelity;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfcPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfsPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23PkePsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.ks05.Ks05PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuBenchConfigFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PSU协议配置项工具类。
 *
 * @author Weiran Liu
 * @date 2022/02/16
 */
public class PsuConfigUtils {
    /** Same value as {@code PsuMain.PTO_NAME_KEY} — defined here so factory does not depend on CLI. */
    public static final String PSU_PTO_NAME_KEY = "psu_pto_name";

    private static final Logger LOGGER = LoggerFactory.getLogger(PsuConfigUtils.class);
    /**
     * CP_INX_PIR_TYPE name
     */
    public final static String ROSN_TYPE = "rosn_type";
    /**
     * TCL23 pm-PEQT: {@code BYTE_ECC_DDH} (default) or {@code PS_OPRF}.
     */
    public static final String TCL23_PMPEQT_KEY = "tcl23_pmpeqt";
    /**
     * ASIACCS_CSSW25 reserved paper-exact flag.
     */
    public static final String CSS25_PAPER_EXACT_KEY = "css25_paper_exact";
    /**
     * ASIACCS_CSSW25 opt-in RS21 paper-comparison proxy flag.
     */
    public static final String CSS25_PAPER_COMPARISON_KEY = "css25_paper_comparison";
    /**
     * Deprecated ASIACCS_CSSW25 RS21 ablation flag, kept as an alias for {@link #CSS25_PAPER_COMPARISON_KEY}.
     */
    public static final String CSS25_RS21_PROXY_ABLATION_KEY = "css25_rs21_proxy_ablation";
    /**
     * private constructor.
     */
    private PsuConfigUtils() {
        // empty
    }

    /**
     * Creates config.
     *
     * @param properties properties.
     * @return config.
     */
    public static PsuConfig createConfig(Properties properties) {
        String rawName = properties.getProperty(PSU_PTO_NAME_KEY);
        PsuType psuType = PsuPaperFidelity.resolvePsuType(rawName);
        switch (psuType) {
            case AC_KRTW19:
                return createKrtw19PsuConfig();
            case PKC_GMRSS21:
                return generateGmr21PsuConfig(properties);
            case USENIX_ConYuWeiminDon23_PKE:
                return createZcl23PkePsuConfig(properties);
            case USENIX_ConYuWeiminDon23_SKE:
                return createZcl23SkePsuConfig();
            case USENIX_JSZDG22:
                return createJsz22SfcPsuConfig(properties);
            case USENIX_JSZDG22_SFS:
                return createJsz22SfsPsuConfig(properties);
            case PKC_CheZhaZha24:
                return createCzz24CwOprfPsuConfig(properties);
            case ASIACCS_CSSW25:
                return createCss25PsuConfig(properties);
            case EUROCRYPT_PisTri26:
                return createPt26PsuConfig(properties);
            case ACISP_DavCid17:
                return createDc17PsuConfig(properties);
            case ACNS_Frikken07:
                return createF07PsuConfig(properties);
            case C_KisSon05:
                return createKs05PsuConfig(properties);
            case JOC_HazNis12:
                return createHn12PsuConfig(properties);
            case EUROCRYPT_PuGaoTri26:
                return createPgt26_2mPsuConfig(properties);
            case USENIX_BinYujConYanYu25:
                return createTbz25PsuConfig(properties);
            case USENIX_HaoWan26:
                return createHaoWan2026PsuConfig(properties);
            case USENIX_YanShiHonDaw24:
                return createJszg24BecrgPsuConfig(properties);
            case Ours:
                return createSmallEcElligatorPsuConfig(properties);
            default:
                throw new IllegalArgumentException("Invalid " + PsuType.class.getSimpleName() + ": " + psuType.protocolId());
        }
    }

    private static Tbz25PsuConfig createTbz25PsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        return new Tbz25PsuConfig.Builder(silent).build();
    }

    private static HaoWan2026PsuConfig createHaoWan2026PsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        return new HaoWan2026PsuConfig.Builder(silent).build();
    }

    private static Jszg24BecrgPsuConfig createJszg24BecrgPsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        int lambda = PropertiesUtils.readInt(properties, "jszg24_lambda", 40);
        int gamma = PropertiesUtils.readInt(properties, "jszg24_gamma", 3);
        double epsilon = PropertiesUtils.readDouble(properties, "jszg24_epsilon", 1.27);
        int maxRetry = PropertiesUtils.readInt(properties, "jszg24_max_cuckoo_retry", 40);
        int itemBitLength = PropertiesUtils.readInt(properties, "jszg24_item_bit_length", 128);
        return new Jszg24BecrgPsuConfig.Builder(silent)
            .setLambda(lambda)
            .setGamma(gamma)
            .setEpsilon(epsilon)
            .setMaxCuckooRetry(maxRetry)
            .setItemBitLength(itemBitLength)
            .build();
    }

    private static Pt26PsuConfig createPt26PsuConfig(Properties properties) {
        return new Pt26PsuConfig.Builder().build();
    }

    private static Dc17PsuConfig createDc17PsuConfig(Properties properties) {
        return new Dc17PsuConfig.Builder().build();
    }

    private static SmallEcElligatorPsuConfig createSmallEcElligatorPsuConfig(Properties properties) {
        SmallEcElligatorPsuConfig benchConfig = SmallEcElligatorPsuBenchConfigFactory.createForProperties(properties);
        if (benchConfig != null) {
            LOGGER.info(
                "Ours bench config from append_string: mode={}, fpBits={}, asyncW={}, parallelEc={}",
                benchConfig.getWCompareMode(),
                benchConfig.getFingerprintBitLength(),
                benchConfig.isAsyncPrecomputeW(),
                benchConfig.isParallelEc()
            );
            return benchConfig;
        }

        int itemBitLength = PropertiesUtils.readInt(properties, "small_ec_item_bit_length", 128);
        boolean logStats = PropertiesUtils.readBoolean(properties, "small_ec_log_stats", false);
        SmallEcElligatorPsuConfig.WCompareMode wCompareMode = readSmallEcEnum(
            properties, "small_ec_w_compare_mode", SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT
        );
        SmallEcElligatorPsuConfig.FingerprintMethod fingerprintMethod = readSmallEcEnum(
            properties,
            "small_ec_fingerprint_method",
            SmallEcElligatorPsuConfig.FingerprintMethod.CANONICAL_POINT_PREFIX
        );
        int statisticalSecurityBits = PropertiesUtils.readInt(properties, "small_ec_statistical_security_bits", 40);
        int fingerprintBitLength = PropertiesUtils.readInt(properties, "small_ec_fingerprint_bits", 0);
        boolean asyncPrecomputeW = PropertiesUtils.readBoolean(properties, "small_ec_async_precompute_w", true);
        int asyncPrecomputeThreshold = PropertiesUtils.readInt(
            properties, "small_ec_async_precompute_threshold", 1024
        );
        boolean parallelEc = PropertiesUtils.readBoolean(properties, "small_ec_parallel_ec", false);
        int parallelThreshold = PropertiesUtils.readInt(properties, "small_ec_parallel_threshold", 1024);
        if (logStats) {
            LOGGER.info(
                "Ours item_bit_length={}, wCompareMode={}, fingerprintBits={}, "
                    + "asyncPrecomputeW={}, asyncThreshold={}, parallelEc={}",
                itemBitLength, wCompareMode, fingerprintBitLength, asyncPrecomputeW,
                asyncPrecomputeThreshold, parallelEc
            );
        }
        return new SmallEcElligatorPsuConfig.Builder()
            .setItemBitLength(itemBitLength)
            .setLogStats(logStats)
            .setWCompareMode(wCompareMode)
            .setFingerprintMethod(fingerprintMethod)
            .setStatisticalSecurityBits(statisticalSecurityBits)
            .setFingerprintBitLength(fingerprintBitLength)
            .setAsyncPrecomputeW(asyncPrecomputeW)
            .setAsyncPrecomputeThreshold(asyncPrecomputeThreshold)
            .setParallelEc(parallelEc)
            .setParallelThreshold(parallelThreshold)
            .build();
    }

    private static <T extends Enum<T>> T readSmallEcEnum(Properties properties, String key, T defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        return Enum.valueOf(defaultValue.getDeclaringClass(), raw.trim());
    }

    private static F07PsuConfig createF07PsuConfig(Properties properties) {
        return new F07PsuConfig.Builder().build();
    }

    private static Ks05PsuConfig createKs05PsuConfig(Properties properties) {
        int maxSetSize = PropertiesUtils.readInt(
            properties, "ks05_max_set_size", Ks05PsuConfig.DEFAULT_MAX_SET_SIZE
        );
        return new Ks05PsuConfig.Builder().setMaxSetSize(maxSetSize).build();
    }

    private static Hn12PsuConfig createHn12PsuConfig(Properties properties) {
        boolean semiHonest = PropertiesUtils.readBoolean(properties, "hn12_semi_honest_debug", true);
        boolean idealPrf = PropertiesUtils.readBoolean(properties, "hn12_use_ideal_prf", true);
        if (!idealPrf) {
            throw new UnsupportedOperationException(
                "hn12_use_ideal_prf=false is unsupported: JOC:HazNis12 has no production OPRF/PRF. "
                    + "Only experimental ideal-PRF debug mode (hn12_use_ideal_prf=true) is implemented."
            );
        }
        int groupBits = PropertiesUtils.readInt(properties, "hn12_group_bit_length", 256);
        return new Hn12PsuConfig.Builder()
            .setEnableSemiHonestDebug(semiHonest)
            .setUseIdealPrfForTesting(true)
            .setGroupBitLength(groupBits)
            .build();
    }

    private static Css25PsuConfig createCss25PsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        Css25PsuConfig.Builder builder = new Css25PsuConfig.Builder(silent);
        if (PropertiesUtils.readBoolean(properties, CSS25_PAPER_EXACT_KEY, false)) {
            return builder.setPaperExact().build();
        }
        boolean paperComparison = PropertiesUtils.readBoolean(properties, CSS25_PAPER_COMPARISON_KEY, false);
        if (PropertiesUtils.readBoolean(properties, CSS25_RS21_PROXY_ABLATION_KEY, false)) {
            LOGGER.warn(
                "{} is deprecated; use {}=true to select the ASIACCS_CSSW25 RS21 comparison proxy",
                CSS25_RS21_PROXY_ABLATION_KEY, CSS25_PAPER_COMPARISON_KEY
            );
            paperComparison = true;
        }
        if (paperComparison && properties.containsKey(PsuCcpsiConfigUtils.CCPSI_PTO_NAME_KEY)) {
            throw new IllegalArgumentException(
                CSS25_PAPER_COMPARISON_KEY + " / " + CSS25_RS21_PROXY_ABLATION_KEY
                    + " cannot be combined with " + PsuCcpsiConfigUtils.CCPSI_PTO_NAME_KEY
            );
        }
        if (paperComparison) {
            builder.setPaperComparisonProxy();
        }
        if (properties.containsKey(PsuCcpsiConfigUtils.CCPSI_PTO_NAME_KEY)) {
            builder.setCcpsiConfig(PsuCcpsiConfigUtils.createConfig(properties));
        }
        if (properties.containsKey(ROSN_TYPE)) {
            RosnType rosnType = MainPtoConfigUtils.readEnum(RosnType.class, properties, ROSN_TYPE);
            builder.setRosnConfig(RosnFactory.createRosnConfig(rosnType, silent));
        }
        return builder.build();
    }

    private static Pgt26_2mPsuConfig createPgt26_2mPsuConfig(Properties properties) {
        boolean skipShuffle = PropertiesUtils.readBoolean(properties, "pgt26_2m_skip_shuffle_proof", false);
        boolean skipRddh = PropertiesUtils.readBoolean(properties, "pgt26_2m_skip_rddh_proof", false);
        String append = properties.getProperty("append_string", "");
        if (append.contains("fair_bench") && (skipShuffle || skipRddh)) {
            throw new IllegalArgumentException(
                "EUROCRYPT_PuGaoTri26 fair benchmark must not skip shuffle/RDDH proofs"
            );
        }
        if (skipShuffle || skipRddh) {
            LOGGER.warn(
                "EUROCRYPT_PuGaoTri26 debug mode: skipShuffleProof={}, skipRddhProof={} (not malicious-security accounting)",
                skipShuffle, skipRddh
            );
        }
        return new Pgt26_2mPsuConfig.Builder()
            .setSkipShuffleProof(skipShuffle)
            .setSkipRddhProof(skipRddh)
            .build();
    }

    private static Krtw19PsuConfig createKrtw19PsuConfig() {
        return new Krtw19PsuConfig.Builder().build();
    }

    private static Gmr21PsuConfig generateGmr21PsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        RosnType rosnType = MainPtoConfigUtils.readEnum(RosnType.class, properties, ROSN_TYPE);
        RosnConfig rosnConfig = RosnFactory.createRosnConfig(rosnType, silent);
        Gmr21MqRpmtConfig gmr21MqRpmtConfig = new Gmr21MqRpmtConfig.Builder(silent)
            .setOkvsType(Gf2eDokvsType.MEGA_BIN)
            .setRosnConfig(rosnConfig)
            .build();
        return new Gmr21PsuConfig.Builder(silent)
            .setGmr21MqRpmtConfig(gmr21MqRpmtConfig)
            .build();
    }

    private static Zcl23SkePsuConfig createZcl23SkePsuConfig() {
        return new Zcl23SkePsuConfig.Builder(SecurityModel.SEMI_HONEST, true).build();
    }

    private static Zcl23PkePsuConfig createZcl23PkePsuConfig(Properties properties) {
        boolean compressEncode = MainPtoConfigUtils.readCompressEncode(properties);
        Zcl23PkeMqRpmtConfig zcl23PkeMqRpmtConfig = new Zcl23PkeMqRpmtConfig.Builder()
            .setCompressEncode(compressEncode)
            .build();
        return new Zcl23PkePsuConfig.Builder()
            .setCoreCotConfig(CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST))
            .setZcl23PkeMqRpmtConfig(zcl23PkeMqRpmtConfig)
            .build();
    }

    private static Jsz22SfcPsuConfig createJsz22SfcPsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        RosnType rosnType = MainPtoConfigUtils.readEnum(RosnType.class, properties, ROSN_TYPE);
        RosnConfig rosnConfig = RosnFactory.createRosnConfig(rosnType, silent);
        return new Jsz22SfcPsuConfig.Builder(silent).setRosnConfig(rosnConfig).build();
    }

    private static Jsz22SfsPsuConfig createJsz22SfsPsuConfig(Properties properties) {
        boolean silent = MainPtoConfigUtils.readSilentCot(properties);
        RosnType rosnType = MainPtoConfigUtils.readEnum(RosnType.class, properties, ROSN_TYPE);
        RosnConfig rosnConfig = RosnFactory.createRosnConfig(rosnType, silent);
        return new Jsz22SfsPsuConfig.Builder(silent).setRosnConfig(rosnConfig).build();
    }

    private static Czz24CwOprfPsuConfig createCzz24CwOprfPsuConfig(Properties properties) {
        Czz24CwOprfPsuConfig.Builder builder = new Czz24CwOprfPsuConfig.Builder();
        String filterTypeRaw = properties.getProperty("filter_type");
        if (filterTypeRaw != null && !filterTypeRaw.trim().isEmpty()) {
            FilterType filterType = MainPtoConfigUtils.readFilterType(properties);
            builder.setFilterType(filterType);
            LOGGER.info("PKC:CheZhaZha24 filter_type={}", filterType);
        }
        return builder.build();
    }
}
