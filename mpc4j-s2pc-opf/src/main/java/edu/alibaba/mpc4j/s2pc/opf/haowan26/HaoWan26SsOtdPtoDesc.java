package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Hao–Wan 2026 ssOTd (Figure 15): ROT-based conditional element transfer.
 */
class HaoWan26SsOtdPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x48574F54L);
    private static final String PTO_NAME = "HAO_WAN26_SS_OTD";

    enum PtoStep {
        /**
         * Server sends masked elements and membership-bit shares {@code [b_i]_0}.
         */
        SERVER_SEND_TRANSFER,
    }

    private static final HaoWan26SsOtdPtoDesc INSTANCE = new HaoWan26SsOtdPtoDesc();

    private HaoWan26SsOtdPtoDesc() {
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
