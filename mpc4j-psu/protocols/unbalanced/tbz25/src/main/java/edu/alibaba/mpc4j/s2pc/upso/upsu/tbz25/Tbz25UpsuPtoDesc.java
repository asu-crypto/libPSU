package edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * USENIX_BinYujConYanYu25 unbalanced UPSU (pnMCRG + OTP via balanced ePSU core).
 */
class Tbz25UpsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x8b3c5d2eL);
    private static final String PTO_NAME = "TBZ25_UPSU";

    private static final Tbz25UpsuPtoDesc INSTANCE = new Tbz25UpsuPtoDesc();

    private Tbz25UpsuPtoDesc() {
        // empty
    }

    static Tbz25UpsuPtoDesc getInstance() {
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
