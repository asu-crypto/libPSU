package edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * TBZ25 DDH-based permuted equality conditional randomness generation (Figure 11, Tu et al., USENIX Security 2025).
 */
class Tbz25PecRgDdhPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x7EC11C11L);
    private static final String PTO_NAME = "TBZ25_PECRG_DDH";

    enum PtoStep {
        CLIENT_SEND_T_HAT,
        SERVER_SEND_PERMUTED_S_PRIME,
    }

    private static final Tbz25PecRgDdhPtoDesc INSTANCE = new Tbz25PecRgDdhPtoDesc();

    private Tbz25PecRgDdhPtoDesc() {
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
