package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Ours — Figure 8 small-set PSU (semi-honest, one-sided output).
 */
public class SmallEcElligatorPsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 9182736455012345678L);
    private static final String PTO_NAME = "Ours";
    private static final SmallEcElligatorPsuPtoDesc INSTANCE = new SmallEcElligatorPsuPtoDesc();

    private SmallEcElligatorPsuPtoDesc() {
        // empty
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

    /**
     * Server = P_S sender (no output); client = P_R receiver (union output).
     */
    enum PtoStep {
        /** P_S → P_R: V_i = H(x_i)^{k0}. */
        SERVER_SEND_V,

        /** P_R → P_S: U_j = V_{π(j)}^{k1}. */
        CLIENT_SEND_U,

        /** P_R → P_S: W_i = H(y_i)^{k1}. */
        CLIENT_SEND_W,

        /** P_S → P_R: filtered U'_j only. */
        SERVER_SEND_U_PRIME,
    }
}
