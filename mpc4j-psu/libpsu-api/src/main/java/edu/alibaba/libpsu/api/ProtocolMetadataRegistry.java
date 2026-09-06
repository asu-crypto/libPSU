package edu.alibaba.libpsu.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SoK-style metadata for every benchmark-facing protocol name.
 * <p>
 * Config keys ({@code psu_pto_name}, {@code psi_pto_name}, {@code upsu_pto_name}) map to
 * entries here. Variants that share a paper name (e.g. balanced TBZ25 PSU and TBZ25 UPSU)
 * are keyed by {@code (canonicalName, functionality)} so neither silently overwrites the other.
 * </p>
 */
public final class ProtocolMetadataRegistry {
    private static final List<ProtocolInfo> ALL;
    private static final Map<String, ProtocolInfo> BY_NAME_AND_FUNCTIONALITY;
    private static final Map<String, List<ProtocolInfo>> BY_NAME;

    static {
        List<ProtocolInfo> list = new ArrayList<>();
        registerBalancedPsu(list);
        registerUpsu(list);
        registerBa12(list);

        Map<String, ProtocolInfo> byKey = new LinkedHashMap<>();
        Map<String, List<ProtocolInfo>> byName = new LinkedHashMap<>();
        for (ProtocolInfo info : list) {
            String key = compositeKey(info.getProtocolName(), info.getFunctionality());
            if (byKey.put(key, info) != null) {
                throw new IllegalStateException("Duplicate protocol metadata key: " + key);
            }
            byName.computeIfAbsent(info.getProtocolName(), ignored -> new ArrayList<>()).add(info);
        }
        ALL = Collections.unmodifiableList(list);
        BY_NAME_AND_FUNCTIONALITY = Collections.unmodifiableMap(byKey);
        Map<String, List<ProtocolInfo>> frozenByName = new LinkedHashMap<>();
        for (Map.Entry<String, List<ProtocolInfo>> e : byName.entrySet()) {
            frozenByName.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        BY_NAME = Collections.unmodifiableMap(frozenByName);
    }

    private ProtocolMetadataRegistry() {
    }

    /**
     * Unambiguous name-only lookup. Returns empty when the name is unknown or when multiple
     * functionalities share the same canonical name (callers must use
     * {@link #findByNameAndFunctionality(String, ProtocolFunctionality)}).
     */
    public static Optional<ProtocolInfo> findByName(String protocolName) {
        if (protocolName == null) {
            return Optional.empty();
        }
        List<ProtocolInfo> matches = BY_NAME.get(ProtocolNames.canonicalize(protocolName));
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        return Optional.empty();
    }

    public static Optional<ProtocolInfo> findByNameAndFunctionality(
        String protocolName, ProtocolFunctionality functionality
    ) {
        if (protocolName == null || functionality == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
            BY_NAME_AND_FUNCTIONALITY.get(compositeKey(ProtocolNames.canonicalize(protocolName), functionality))
        );
    }

    public static List<ProtocolInfo> findAllByName(String protocolName) {
        if (protocolName == null) {
            return List.of();
        }
        List<ProtocolInfo> matches = BY_NAME.get(ProtocolNames.canonicalize(protocolName));
        return matches == null ? List.of() : matches;
    }

    /**
     * All registered metadata entries in registration order.
     * Prefer this over name-keyed maps when variants may share a paper name.
     */
    public static List<ProtocolInfo> allEntries() {
        return ALL;
    }

    /**
     * Unambiguous name → info map (ambiguous shared names are omitted).
     *
     * @deprecated prefer {@link #allEntries()} or {@link #findByNameAndFunctionality}.
     */
    @Deprecated
    public static Map<String, ProtocolInfo> all() {
        Map<String, ProtocolInfo> map = new LinkedHashMap<>();
        for (ProtocolInfo info : ALL) {
            List<ProtocolInfo> matches = BY_NAME.get(info.getProtocolName());
            if (matches != null && matches.size() == 1) {
                map.put(info.getProtocolName(), info);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static boolean isKnownBenchmarkName(String protocolName) {
        if (protocolName == null) {
            return false;
        }
        List<ProtocolInfo> matches = BY_NAME.get(ProtocolNames.canonicalize(protocolName));
        return matches != null && !matches.isEmpty();
    }

    private static String compositeKey(String name, ProtocolFunctionality functionality) {
        return name + "|" + Objects.requireNonNull(functionality).name();
    }

    private static void put(List<ProtocolInfo> list, ProtocolInfo info) {
        list.add(info);
    }

    private static void registerBalancedPsu(List<ProtocolInfo> list) {
        put(list, psu(ProtocolNames.AC_KRTW19, "KRTW19", 2019, ProtocolFamily.RPMT, "mpc4j-psu-protocol-krtw19").build());
        put(list, psu(ProtocolNames.PKC_GMRSS21, "GMR21", 2021, ProtocolFamily.RPMT, "mpc4j-psu-protocol-gmr21").build());
        put(list, psu(ProtocolNames.USENIX_JSZDG22, "JSZ22", 2022, ProtocolFamily.OTHER, "mpc4j-psu-protocol-jsz22").build());
        put(list, psu(ProtocolNames.USENIX_JSZDG22_SFS, "JSZ22", 2022, ProtocolFamily.OTHER, "mpc4j-psu-protocol-jsz22").build());
        put(list, psu(ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_PKE, "ZCL23", 2023, ProtocolFamily.PKE_SKE, "mpc4j-psu-protocol-zcl23").build());
        put(list, psu(ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_SKE, "ZCL23", 2023, ProtocolFamily.PKE_SKE, "mpc4j-psu-protocol-zcl23").build());
        put(list, psu(ProtocolNames.PKC_CHE_ZHA_ZHA24, "CZZ24", 2024, ProtocolFamily.OTHER, "mpc4j-psu-protocol-czz24").build());
        put(list, psu(ProtocolNames.ASIACCS_CSSW25, "CSS25", 2025, ProtocolFamily.OTHER, "mpc4j-psu-protocol-css25")
            .readiness(ProtocolReadiness.EXPERIMENTAL)
            .build());
        put(list, psu(ProtocolNames.EUROCRYPT_PIS_TRI26, "PT26", 2026, ProtocolFamily.IBLT, "mpc4j-psu-protocol-pt26").build());
        put(list, psu(ProtocolNames.ACISP_DAV_CID17, "DC17", 2017, ProtocolFamily.BLOOM_AHE, "mpc4j-psu-protocol-dc17").build());
        put(list, psu(ProtocolNames.ACNS_FRIKKEN07, "F07", 2007, ProtocolFamily.POLYNOMIAL_AHE, "mpc4j-psu-protocol-f07").build());
        put(list, psu(ProtocolNames.C_KIS_SON05, "KS05", 2005, ProtocolFamily.POLYNOMIAL_AHE, "mpc4j-psu-protocol-ks05").build());
        put(list, psu(ProtocolNames.JOC_HAZ_NIS12, "HN12", 2012, ProtocolFamily.OTHER, "mpc4j-psu-protocol-hn12")
            .readiness(ProtocolReadiness.EXPERIMENTAL)
            .outputModel(OutputModel.TWO_SIDED)
            .securityModel(LibPsuSecurityModel.SEMI_HONEST)
            .build());
        put(list, psu(ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25, "TBZ25", 2025, ProtocolFamily.MCRG_ECRG, "mpc4j-psu-protocol-tbz25").build());
        put(list, psu(ProtocolNames.USENIX_HAO_WAN26, "HaoWan2026", 2026, ProtocolFamily.OTHER, "mpc4j-psu-protocol-haowan2026").build());
        put(list, psu(ProtocolNames.USENIX_YAN_SHI_HON_DAW24, "JSZG24", 2024, ProtocolFamily.MCRG_ECRG, "mpc4j-psu-protocol-jszg24").build());
        put(list, psu(ProtocolNames.EUROCRYPT_PU_GAO_TRI26, "PGT26", 2026, ProtocolFamily.OTHER, "mpc4j-psu-protocol-pgt26")
            .securityModel(LibPsuSecurityModel.MALICIOUS)
            .outputModel(OutputModel.MALICIOUS_TWO_SIDED)
            .build());
        put(list, ProtocolInfo.builder(ProtocolNames.OURS)
            .citationKey("custom-small-ec-elligator")
            .year(2026)
            .functionality(ProtocolFunctionality.PSU)
            .family(ProtocolFamily.SMALL_SET_EC)
            .outputModel(OutputModel.LEAKAGE_BASELINE)
            .securityModel(LibPsuSecurityModel.SEMI_HONEST)
            .setSizeMode(SetSizeMode.BALANCED)
            .primitiveKind(PrimitiveKind.HYBRID)
            .readiness(ProtocolReadiness.EXPERIMENTAL)
            .smallSetOptimization(true)
            .mavenModule("mpc4j-psu-protocol-small-ec-elligator-psu")
            .configPropertyName("psu_pto_name")
            .build());
    }

    private static void registerUpsu(List<ProtocolInfo> list) {
        put(list, upsu(ProtocolNames.CCS_TCLZ23, "TCL23", 2023, "mpc4j-psu-protocol-tcl23").build());
        put(list, upsu(ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25, "TBZ25", 2025, "mpc4j-psu-protocol-tbz25-upsu")
            .family(ProtocolFamily.MCRG_ECRG)
            .build());
    }

    private static void registerBa12(List<ProtocolInfo> list) {
        put(list, ProtocolInfo.builder(ProtocolNames.ASIACCS_BLA_AGU12)
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
