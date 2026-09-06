package edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * TBZ25 balanced permuted non-membership CRG (pnMCRG) session.
 */
class Tbz25BalancedPnMcrgPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x4EC11C11L);
    private static final String PTO_NAME = "TBZ25_BALANCED_PNMCRG";

    private static final Tbz25BalancedPnMcrgPtoDesc INSTANCE = new Tbz25BalancedPnMcrgPtoDesc();

    private Tbz25BalancedPnMcrgPtoDesc() {
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
