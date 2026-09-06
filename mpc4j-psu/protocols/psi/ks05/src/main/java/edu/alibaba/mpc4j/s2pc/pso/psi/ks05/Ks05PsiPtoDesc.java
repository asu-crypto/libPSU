package edu.alibaba.mpc4j.s2pc.pso.psi.ks05;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * C_KisSon05 PSI protocol description.
 */
public class Ks05PsiPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 2025052801L);
    private static final String PTO_NAME = "KS05_PSI";

    private static final Ks05PsiPtoDesc INSTANCE = new Ks05PsiPtoDesc();

    private Ks05PsiPtoDesc() {
        // empty
    }

    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(getInstance());
    }

    enum PtoStep {
        CLIENT_SEND_PK_EPOLY,
        SERVER_SEND_MASKED_POLY,
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
