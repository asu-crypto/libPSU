package edu.alibaba.mpc4j.s2pc.pso.psu.ks05;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * C_KisSon05 (Kissner–Song 2005) polynomial-based PSU (two-party honest-but-curious).
 *
 * The client (P1) learns the union; the server (P2) learns nothing.
 *
 * @author Weiran Liu
 * @date 2026/05/28
 */
public class Ks05PsuPtoDesc implements PtoDesc {
    /**
     * protocol ID
     */
    private static final int PTO_ID = Math.abs((int) -5137822868682603542L);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "KS05_POLY_PSU";

    /**
     * singleton mode
     */
    private static final Ks05PsuPtoDesc INSTANCE = new Ks05PsuPtoDesc();

    /**
     * private constructor.
     */
    private Ks05PsuPtoDesc() {
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

