package edu.alibaba.libpsu.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * SoK-style metadata for every benchmark-facing protocol name.
 * <p>
 * Config keys ({@code psu_pto_name}, {@code psi_pto_name}, {@code upsu_pto_name}) map to
 * {@link #findByName(String)} entries here.
 * </p>
 */
public final class ProtocolMetadataRegistry {
    private static final Map<String, ProtocolInfo> BY_NAME;

    static {
        Map<String, ProtocolInfo> map = new LinkedHashMap<>();
        registerBalancedPsu(map);
        registerUpsu(map);
        registerBa12(map);
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private ProtocolMetadataRegistry() {
    }

    public static Optional<ProtocolInfo> findByName(String protocolName) {
        if (protocolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(ProtocolNames.canonicalize(protocolName)));
    }

    public static Map<String, ProtocolInfo> all() {
        return BY_NAME;
    }

    public static boolean isKnownBenchmarkName(String protocolName) {
        return findByName(protocolName).isPresent();
    }

    private static void put(Map<String, ProtocolInfo> map, ProtocolInfo info) {
        map.put(info.getProtocolName(), info);
    }

    private static void registerBalancedPsu(Map<String, ProtocolInfo> map) {
        put(map, psu(ProtocolNames.AC_KRTW19, "KRTW19", 2019, ProtocolFamily.RPMT, "mpc4j-psu-protocol-krtw19").build());
        put(map, psu(ProtocolNames.PKC_GMRSS21, "GMR21", 2021, ProtocolFamily.RPMT, "mpc4j-psu-protocol-gmr21").build());
        put(map, psu(ProtocolNames.USENIX_JSZDG22, "JSZ22", 2022, ProtocolFamily.OTHER, "mpc4j-psu-protocol-jsz22").build());
        put(map, psu(ProtocolNames.USENIX_JSZDG22_SFS, "JSZ22", 2022, ProtocolFamily.OTHER, "mpc4j-psu-protocol-jsz22").build());
        put(map, psu(ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_PKE, "ZCL23", 2023, ProtocolFamily.PKE_SKE, "mpc4j-psu-protocol-zcl23").build());
        put(map, psu(ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_SKE, "ZCL23", 2023, ProtocolFamily.PKE_SKE, "mpc4j-psu-protocol-zcl23").build());
        put(map, psu(ProtocolNames.PKC_CHE_ZHA_ZHA24, "CZZ24", 2024, ProtocolFamily.OTHER, "mpc4j-psu-protocol-czz24").build());
        put(map, psu(ProtocolNames.ASIACCS_CSSW25, "CSS25", 2025, ProtocolFamily.OTHER, "mpc4j-psu-protocol-css25")
            .readiness(ProtocolReadiness.EXPERIMENTAL)
            .build());
        put(map, psu(ProtocolNames.EUROCRYPT_PIS_TRI26, "PT26", 2026, ProtocolFamily.IBLT, "mpc4j-psu-protocol-pt26").build());
        put(map, psu(ProtocolNames.ACISP_DAV_CID17, "DC17", 2017, ProtocolFamily.BLOOM_AHE, "mpc4j-psu-protocol-dc17").build());
        put(map, psu(ProtocolNames.ACNS_FRIKKEN07, "F07", 2007, ProtocolFamily.POLYNOMIAL_AHE, "mpc4j-psu-protocol-f07").build());
        put(map, psu(ProtocolNames.C_KIS_SON05, "KS05", 2005, ProtocolFamily.POLYNOMIAL_AHE, "mpc4j-psu-protocol-ks05").build());
        put(map, psu(ProtocolNames.JOC_HAZ_NIS12, "HN12", 2012, ProtocolFamily.OTHER, "mpc4j-psu-protocol-hn12")
            .readiness(ProtocolReadiness.EXPERIMENTAL)
            .build());
        put(map, psu(ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25, "TBZ25", 2025, ProtocolFamily.MCRG_ECRG, "mpc4j-psu-protocol-tbz25").build());
        put(map, psu(ProtocolNames.USENIX_HAO_WAN26, "HaoWan2026", 2026, ProtocolFamily.OTHER, "mpc4j-psu-protocol-haowan2026").build());
        put(map, psu(ProtocolNames.USENIX_YAN_SHI_HON_DAW24, "JSZG24", 2024, ProtocolFamily.MCRG_ECRG, "mpc4j-psu-protocol-jszg24").build());
        put(map, psu(ProtocolNames.EUROCRYPT_PU_GAO_TRI26, "PGT26", 2026, ProtocolFamily.OTHER, "mpc4j-psu-protocol-pgt26")
            .securityModel(LibPsuSecurityModel.MALICIOUS)
            .outputModel(OutputModel.MALICIOUS_TWO_SIDED)
            .build());
        put(map, ProtocolInfo.builder(ProtocolNames.OURS)
            .citationKey("custom-small-ec-elligator")
            .year(2026)
            .functionality(ProtocolFunctionality.PSU)
            .family(ProtocolFamily.SMALL_SET_EC)
            .outputModel(OutputModel.ONE_SIDED)
            .securityModel(LibPsuSecurityModel.SEMI_HONEST)
            .setSizeMode(SetSizeMode.BALANCED)
            .primitiveKind(PrimitiveKind.HYBRID)
            .readiness(ProtocolReadiness.EXPERIMENTAL)
            .smallSetOptimization(true)
            .mavenModule("mpc4j-psu-protocol-small-ec-elligator-psu")
            .configPropertyName("psu_pto_name")
            .build());
    }

    private static void registerUpsu(Map<String, ProtocolInfo> map) {
        put(map, upsu(ProtocolNames.CCS_TCLZ23, "TCL23", 2023, "mpc4j-psu-protocol-tcl23").build());
        put(map, upsu(ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25, "TBZ25", 2025, "mpc4j-psu-protocol-tbz25-upsu").build());
    }

    private static void registerBa12(Map<String, ProtocolInfo> map) {
        put(map, ProtocolInfo.builder(ProtocolNames.ASIACCS_BLA_AGU12)
            .citationKey("BA12")
            .year(2012)
            .functionality(ProtocolFunctionality.BA12)
            .family(ProtocolFamily.OTHER)
            .outputModel(OutputModel.SENDER_AUX)
            .securityModel(LibPsuSecurityModel.SEMI_HONEST)
            .setSizeMode(SetSizeMode.BALANCED)
            .primitiveKind(PrimitiveKind.HYBRID)
            .minimumInputSetSize(2)
            .mavenModule("mpc4j-psu-protocol-ba12")
            .configPropertyName("ba12_pto_name")
            .build());
    }

    private static ProtocolInfo.Builder psu(String name, String citation, int year, ProtocolFamily family, String module) {
        return ProtocolInfo.builder(name)
            .citationKey(citation)
            .year(year)
            .functionality(ProtocolFunctionality.PSU)
            .family(family)
            .outputModel(OutputModel.ONE_SIDED)
            .securityModel(LibPsuSecurityModel.SEMI_HONEST)
            .setSizeMode(SetSizeMode.BALANCED)
            .primitiveKind(family == ProtocolFamily.PKE_SKE || family == ProtocolFamily.BLOOM_AHE
                ? PrimitiveKind.PUBLIC_KEY : PrimitiveKind.HYBRID)
            .minimumInputSetSize(2)
            .mavenModule(module)
            .configPropertyName("psu_pto_name");
    }

    private static ProtocolInfo.Builder upsu(String name, String citation, int year, String module) {
        return ProtocolInfo.builder(name)
            .citationKey(citation)
            .year(year)
            .functionality(ProtocolFunctionality.UPSU)
            .family(ProtocolFamily.PKE_SKE)
            .outputModel(OutputModel.ONE_SIDED)
            .securityModel(LibPsuSecurityModel.SEMI_HONEST)
            .setSizeMode(SetSizeMode.UNBALANCED)
            .primitiveKind(PrimitiveKind.HYBRID)
            .minimumInputSetSize(1)
            .mavenModule(module)
            .configPropertyName("upsu_pto_name");
    }
}
