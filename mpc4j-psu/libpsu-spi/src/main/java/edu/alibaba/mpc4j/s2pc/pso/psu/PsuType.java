package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.api.ProtocolNames;

import java.util.HashMap;
import java.util.Map;

/**
 * Balanced PSU protocol identifiers.
 * <p>
 * {@link #protocolId()} is the canonical wire name (e.g. {@code PKC:CheZhaZha24}) used in
 * configs and summaries. {@link #fileToken()} is the bench output basename token.
 * </p>
 */
public enum PsuType {
    AC_KRTW19(ProtocolNames.AC_KRTW19),
    PKC_GMRSS21(ProtocolNames.PKC_GMRSS21),
    USENIX_JSZDG22(ProtocolNames.USENIX_JSZDG22),
    USENIX_JSZDG22_SFS(ProtocolNames.USENIX_JSZDG22_SFS),
    USENIX_ConYuWeiminDon23_PKE(ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_PKE),
    USENIX_ConYuWeiminDon23_SKE(ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_SKE),
    PKC_CheZhaZha24(ProtocolNames.PKC_CHE_ZHA_ZHA24),
    ASIACCS_CSSW25(ProtocolNames.ASIACCS_CSSW25),
    EUROCRYPT_PisTri26(ProtocolNames.EUROCRYPT_PIS_TRI26),
    ACISP_DavCid17(ProtocolNames.ACISP_DAV_CID17),
    ACNS_Frikken07(ProtocolNames.ACNS_FRIKKEN07),
    C_KisSon05(ProtocolNames.C_KIS_SON05),
    JOC_HazNis12(ProtocolNames.JOC_HAZ_NIS12),
    USENIX_BinYujConYanYu25(ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25),
    USENIX_HaoWan26(ProtocolNames.USENIX_HAO_WAN26),
    USENIX_YanShiHonDaw24(ProtocolNames.USENIX_YAN_SHI_HON_DAW24),
    EUROCRYPT_PuGaoTri26(ProtocolNames.EUROCRYPT_PU_GAO_TRI26),
    Ours(ProtocolNames.OURS);

    private static final Map<String, PsuType> BY_PROTOCOL_ID = buildIndex();

    private final String protocolId;

    PsuType(String protocolId) {
        this.protocolId = protocolId;
    }

    public String protocolId() {
        return protocolId;
    }

    public String fileToken() {
        return ProtocolNames.toFileToken(protocolId);
    }

    public static PsuType fromProtocolId(String raw) {
        if (raw != null) {
            String trimmed = raw.trim();
            String canonicalAttempt = ProtocolNames.canonicalize(trimmed);
            if ("PGT26_1M".equals(trimmed)
                || "PGT26-1M".equals(trimmed)
                || "PGT26_1M".equals(canonicalAttempt)
                || "PGT26-1M".equals(canonicalAttempt)) {
                throw new IllegalArgumentException(
                    "PGT26_1M denotes internal one-sided PGT26 experimental code and has no public "
                        + "factory/config API. Use EUROCRYPT:PuGaoTri26 / PGT26_2M for public two-sided PGT26."
                );
            }
        }
        String canonical = ProtocolNames.canonicalize(raw);
        PsuType type = BY_PROTOCOL_ID.get(canonical);
        if (type == null) {
            throw new IllegalArgumentException("Invalid PsuType: " + raw);
        }
        return type;
    }

    private static Map<String, PsuType> buildIndex() {
        Map<String, PsuType> map = new HashMap<>();
        for (PsuType type : values()) {
            map.put(type.protocolId, type);
        }
        for (Map.Entry<String, String> alias : ProtocolNames.legacyAliases().entrySet()) {
            PsuType type = map.get(alias.getValue());
            if (type != null) {
                map.putIfAbsent(alias.getKey(), type);
            }
        }
        return map;
    }
}
