package edu.alibaba.libpsu.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical protocol identifiers used in configs, benchmarks, and summaries.
 * <p>
 * Wire form uses a venue prefix and colon (e.g. {@code PKC:CheZhaZha24}). Bench output
 * filenames replace {@code :} with {@code -} via {@link #toFileToken(String)}.
 * </p>
 */
public final class ProtocolNames {
    // Balanced PSU
    public static final String AC_KRTW19 = "AC:KRTW19";
    public static final String PKC_GMRSS21 = "PKC:GMRSS21";
    public static final String USENIX_JSZDG22 = "USENIX:JSZDG22";
    public static final String USENIX_JSZDG22_SFS = "USENIX:JSZDG22_SFS";
    public static final String USENIX_CON_YU_WEIMIN_DON23_PKE = "USENIX:ConYuWeiminDon23_PKE";
    public static final String USENIX_CON_YU_WEIMIN_DON23_SKE = "USENIX:ConYuWeiminDon23_SKE";
    public static final String PKC_CHE_ZHA_ZHA24 = "PKC:CheZhaZha24";
    public static final String ASIACCS_CSSW25 = "ASIACCS:CSSW25";
    public static final String USENIX_BIN_YUJ_CON_YAN_YU25 = "USENIX:BinYujConYanYu25";
    public static final String USENIX_HAO_WAN26 = "USENIX:HaoWan26";
    public static final String EUROCRYPT_PIS_TRI26 = "EUROCRYPT:PisTri26";
    public static final String ACISP_DAV_CID17 = "ACISP:DavCid17";
    public static final String ACNS_FRIKKEN07 = "ACNS:Frikken07";
    public static final String C_KIS_SON05 = "C:KisSon05";
    public static final String JOC_HAZ_NIS12 = "JOC:HazNis12";
    public static final String USENIX_YAN_SHI_HON_DAW24 = "USENIX:YanShiHonDaw24";
    public static final String EUROCRYPT_PU_GAO_TRI26 = "EUROCRYPT:PuGaoTri26";
    public static final String OURS = "Ours";

    // UPSU
    public static final String CCS_TCLZ23 = "CCS:TCLZ23";

    // BA12
    public static final String ASIACCS_BLA_AGU12 = "ASIACCS:BlaAgu12";

    private static final Map<String, String> LEGACY_ALIASES = buildLegacyAliases();

    private ProtocolNames() {
        // empty
    }

    public static String toFileToken(String protocolId) {
        Objects.requireNonNull(protocolId, "protocolId");
        return protocolId.replace(':', '-');
    }

    public static String fromFileToken(String fileToken) {
        Objects.requireNonNull(fileToken, "fileToken");
        int dash = fileToken.indexOf('-');
        if (dash < 0) {
            return fileToken;
        }
        return fileToken.substring(0, dash) + ':' + fileToken.substring(dash + 1);
    }

    public static String canonicalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return LEGACY_ALIASES.getOrDefault(trimmed, trimmed);
    }

    public static Map<String, String> legacyAliases() {
        return LEGACY_ALIASES;
    }

    private static Map<String, String> buildLegacyAliases() {
        Map<String, String> map = new HashMap<>();
        alias(map, "KRTW19", AC_KRTW19);
        alias(map, "GMR21", PKC_GMRSS21);
        alias(map, "GMR21_PROXY", PKC_GMRSS21);
        alias(map, "JSZ22_SFC", USENIX_JSZDG22);
        alias(map, "JSZ22_SFC_PROXY", USENIX_JSZDG22);
        alias(map, "JSZ22_SFS", USENIX_JSZDG22_SFS);
        alias(map, "JSZ22_SFS_PROXY", USENIX_JSZDG22_SFS);
        alias(map, "ZCL23_PKE", USENIX_CON_YU_WEIMIN_DON23_PKE);
        alias(map, "ZCL23_SKE", USENIX_CON_YU_WEIMIN_DON23_SKE);
        alias(map, "CZZ24_CW_OPRF", PKC_CHE_ZHA_ZHA24);
        alias(map, "CSS25", ASIACCS_CSSW25);
        alias(map, "CSS25_PROXY", ASIACCS_CSSW25);
        alias(map, "TBZ25", USENIX_BIN_YUJ_CON_YAN_YU25);
        alias(map, "HAO_WAN2026", USENIX_HAO_WAN26);
        alias(map, "PT26", EUROCRYPT_PIS_TRI26);
        alias(map, "DC17", ACISP_DAV_CID17);
        alias(map, "F07", ACNS_FRIKKEN07);
        alias(map, "JSZG24_BECRG_PSU", USENIX_YAN_SHI_HON_DAW24);
        alias(map, "PGT26_2M", EUROCRYPT_PU_GAO_TRI26);
        alias(map, "PGT26_1M", EUROCRYPT_PU_GAO_TRI26);
        alias(map, "SMALL_EC_ELLIGATOR_PSU", OURS);
        alias(map, "CUSTOM_SMALL_EC_ELLIGATOR_PSU", OURS);
        alias(map, "KS05", C_KIS_SON05);
        alias(map, "HN12", JOC_HAZ_NIS12);
        alias(map, "HN12_PSI", JOC_HAZ_NIS12);
        alias(map, "TCL23", CCS_TCLZ23);
        alias(map, "BA12", ASIACCS_BLA_AGU12);
        // Enum / config names use '_' where canonical wire ids use ':'.
        for (String canonical : new String[]{
            AC_KRTW19, PKC_GMRSS21, USENIX_JSZDG22, USENIX_JSZDG22_SFS,
            USENIX_CON_YU_WEIMIN_DON23_PKE, USENIX_CON_YU_WEIMIN_DON23_SKE,
            PKC_CHE_ZHA_ZHA24, ASIACCS_CSSW25, EUROCRYPT_PIS_TRI26,
            ACISP_DAV_CID17, ACNS_FRIKKEN07, C_KIS_SON05, JOC_HAZ_NIS12,
            USENIX_BIN_YUJ_CON_YAN_YU25, USENIX_HAO_WAN26, USENIX_YAN_SHI_HON_DAW24,
            EUROCRYPT_PU_GAO_TRI26, OURS, CCS_TCLZ23, ASIACCS_BLA_AGU12
        }) {
            alias(map, canonical.replace(':', '_'), canonical);
        }
        for (Map.Entry<String, String> entry : new HashMap<>(map).entrySet()) {
            alias(map, toFileToken(entry.getValue()), entry.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    private static void alias(Map<String, String> map, String legacy, String canonical) {
        map.put(legacy, canonical);
    }
}
