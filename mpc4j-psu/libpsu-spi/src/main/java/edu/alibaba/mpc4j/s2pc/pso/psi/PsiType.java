package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.libpsu.api.ProtocolNames;

import java.util.HashMap;
import java.util.Map;

/**
 * PSI protocol identifiers.
 */
public enum PsiType {
    C_KisSon05(ProtocolNames.C_KIS_SON05),
    JOC_HazNis12(ProtocolNames.JOC_HAZ_NIS12);

    private static final Map<String, PsiType> BY_PROTOCOL_ID = buildIndex();

    private final String protocolId;

    PsiType(String protocolId) {
        this.protocolId = protocolId;
    }

    public String protocolId() {
        return protocolId;
    }

    public String fileToken() {
        return ProtocolNames.toFileToken(protocolId);
    }

    public static PsiType fromProtocolId(String raw) {
        String canonical = ProtocolNames.canonicalize(raw);
        PsiType type = BY_PROTOCOL_ID.get(canonical);
        if (type == null) {
            throw new IllegalArgumentException("Invalid PsiType: " + raw);
        }
        return type;
    }

    private static Map<String, PsiType> buildIndex() {
        Map<String, PsiType> map = new HashMap<>();
        for (PsiType type : values()) {
            map.put(type.protocolId, type);
        }
        for (Map.Entry<String, String> alias : ProtocolNames.legacyAliases().entrySet()) {
            PsiType type = map.get(alias.getValue());
            if (type != null) {
                map.putIfAbsent(alias.getKey(), type);
            }
        }
        return map;
    }
}
