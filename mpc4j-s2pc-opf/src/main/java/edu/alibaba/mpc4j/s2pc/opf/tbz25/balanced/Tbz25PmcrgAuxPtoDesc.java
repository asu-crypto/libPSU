package edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Auxiliary messages for TBZ25 balanced pMCRG OKVS (after RS21), Tu et al. Figure 13.
 */
class Tbz25PmcrgAuxPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x5EC11C11L);
    private static final String PTO_NAME = "TBZ25_PMCRG_AUX";

    enum PtoStep {
        CLIENT_SEND_OKVS_FIG13,
    }

    private static final Tbz25PmcrgAuxPtoDesc INSTANCE = new Tbz25PmcrgAuxPtoDesc();

    private Tbz25PmcrgAuxPtoDesc() {
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
