package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Hao–Wan 2026 ssPMT-fast (Figure 17): APRR24 F32 shared-output OPRF + OKVS + CGS22 ssPEQT.
 */
class HaoWan26SsPmtFastPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x48573626L);
    private static final String PTO_NAME = "HAO_WAN26_SS_PMT_FAST";

    enum PtoStep {
        /**
         * Client sends OKVS keys and encoded storage for {@code (y_i, truncate(F(y_i)))}.
         */
        CLIENT_SEND_OKVS,
    }

    private static final HaoWan26SsPmtFastPtoDesc INSTANCE = new HaoWan26SsPmtFastPtoDesc();

    private HaoWan26SsPmtFastPtoDesc() {
        // empty
    }

    static PtoDesc getInstance() {
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
