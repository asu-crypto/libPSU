package edu.alibaba.mpc4j.s2pc.pso.psu.f07;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * ACNS_Frikken07 (Frikken 2007) polynomial-based PSU (two-party honest-but-curious).
 *
 * The client (P1) learns the union; the server (P2) learns nothing.
 *
 * @author Weiran Liu
 * @date 2026/05/28
 */
public class F07PsuPtoDesc implements PtoDesc {
    /**
     * protocol ID
     */
    private static final int PTO_ID = Math.abs((int) -4137822868682603541L);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "F07_POLY_PSU";

    /**
     * singleton mode
     */
    private static final F07PsuPtoDesc INSTANCE = new F07PsuPtoDesc();

    /**
     * private constructor.
     */
    private F07PsuPtoDesc() {
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
     * protocol steps.
     */
    enum PtoStep {
        /**
         * client sends public key + encrypted polynomial coefficients
         */
        CLIENT_SEND_PK_EPOLY,
        /**
         * server sends randomized tuples (Enc(f(s)*s*r), Enc(f(s)*r))
         */
        SERVER_SEND_TUPLES,
    }
}

