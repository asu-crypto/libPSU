package edu.alibaba.mpc4j.s2pc.pso.psu.css25;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * CSSW25 PSU (circuit-based PSI + mp-OPRF + CnP + OT).
 * <p>
 * Chandran, Schneider, Stillger, Weinert, "Concretely Efficient Private Set Union via Circuit-Based PSI",
 * ASIACCS 2025.
 * </p>
 */
class Css25PsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0xC5525A5A25L);
    private static final String PTO_NAME = "CSS25_PSU";

    enum PtoStep {
        /**
         * receiver sends m = x1 ⊕ a (CnP step 2)
         */
        CLIENT_SEND_CNP_M,
        /**
         * sender sends m̃ (CnP step 3)
         */
        SERVER_SEND_CNP_M_TILDE,
        /**
         * sender sends OT-encrypted Cuckoo payloads (permuted)
         */
        SERVER_SEND_ENC_ELEMENTS,
    }

    private static final Css25PsuPtoDesc INSTANCE = new Css25PsuPtoDesc();

    private Css25PsuPtoDesc() {
    }

    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(getInstance());
    }

    @Override
    public int getPtoId() {
        return PTO_ID;
    }

    @Override
    public String getPtoName() {
        return PTO_NAME;
    }
}
