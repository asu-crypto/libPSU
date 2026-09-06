package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * HN12 PSI protocol description (Protocol 5, π∩).
 */
public class Hn12PsiPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 2026052801L);
    private static final String PTO_NAME = "JOC_HazNis12";

    private static final Hn12PsiPtoDesc INSTANCE = new Hn12PsiPtoDesc();

    private Hn12PsiPtoDesc() {
    }

    public static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(getInstance());
    }

    enum PtoStep {
        P1_SEND_KEYS,
        P1_SEND_ALLOC,
        P2_SEND_COMMITMENTS,
        P1_SEND_ENC_POLYS,
        P1_SEND_POLY_PROOF,
        P2_SEND_EVALS,
        P2_SEND_PRF_OUT,
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
