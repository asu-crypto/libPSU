package edu.alibaba.libpsu.api;

/**
 * SoK security-model tag (maps to {@link LibPsuSecurityModel} in metadata).
 */
public enum SecurityModelTag {
    SEMI_HONEST,
    AON,
    MALICIOUS;

    public LibPsuSecurityModel toLibPsuSecurityModel() {
        switch (this) {
            case AON:
                return LibPsuSecurityModel.AON;
            case MALICIOUS:
                return LibPsuSecurityModel.MALICIOUS;
            case SEMI_HONEST:
            default:
                return LibPsuSecurityModel.SEMI_HONEST;
        }
    }

    public static SecurityModelTag from(LibPsuSecurityModel model) {
        switch (model) {
            case AON:
                return AON;
            case MALICIOUS:
                return MALICIOUS;
            case SEMI_HONEST:
            default:
                return SEMI_HONEST;
        }
    }
}
