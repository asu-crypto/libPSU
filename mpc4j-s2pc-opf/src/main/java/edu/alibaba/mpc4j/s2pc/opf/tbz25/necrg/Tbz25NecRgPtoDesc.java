package edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * TBZ25 non-equality conditional randomness generation (Figure 16, Tu et al., USENIX Security 2025).
 */
class Tbz25NecRgPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x6EC11C11L);
    private static final String PTO_NAME = "TBZ25_NECRG";

    private static final Tbz25NecRgPtoDesc INSTANCE = new Tbz25NecRgPtoDesc();

    private Tbz25NecRgPtoDesc() {
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
