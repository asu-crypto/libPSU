package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.libpsu.api.ProtocolNames;

import java.util.HashMap;
import java.util.Map;

/**
 * Unbalanced UPSU protocol identifiers.
 */
public enum UpsuType {
    CCS_TCLZ23(ProtocolNames.CCS_TCLZ23),
    USENIX_BinYujConYanYu25(ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25);

    private static final Map<String, UpsuType> BY_PROTOCOL_ID = buildIndex();

    private final String protocolId;

    UpsuType(String protocolId) {
        this.protocolId = protocolId;
    }

    public String protocolId() {
        return protocolId;
    }

    public String fileToken() {
        return ProtocolNames.toFileToken(protocolId);
    }

    public static UpsuType fromProtocolId(String raw) {
        String canonical = ProtocolNames.canonicalize(raw);
        UpsuType type = BY_PROTOCOL_ID.get(canonical);
        if (type == null) {
            throw new IllegalArgumentException("Invalid UpsuType: " + raw);
        }
        return type;
    }

    private static Map<String, UpsuType> buildIndex() {
        Map<String, UpsuType> map = new HashMap<>();
        for (UpsuType type : values()) {
            map.put(type.protocolId, type);
        }
        for (Map.Entry<String, String> alias : ProtocolNames.legacyAliases().entrySet()) {
            UpsuType type = map.get(alias.getValue());
            if (type != null) {
                map.putIfAbsent(alias.getKey(), type);
            }
        }
        return map;
    }
}
