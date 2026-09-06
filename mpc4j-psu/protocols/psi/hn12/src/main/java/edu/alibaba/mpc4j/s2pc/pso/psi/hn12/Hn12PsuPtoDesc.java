package edu.alibaba.mpc4j.s2pc.pso.psi.hn12;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * JOC:HazNis12 PSU (Protocol 8 π∪). Shares the Protocol 5 message layout, then sends the union.
 */
class Hn12PsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) -6128945730184726153L);
    private static final String PTO_NAME = "HN12_PSU";
    private static final Hn12PsuPtoDesc INSTANCE = new Hn12PsuPtoDesc();

    private Hn12PsuPtoDesc() {
    }

    static PtoDesc getInstance() {
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
        P1_SEND_UNION,
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
