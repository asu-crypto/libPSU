package edu.alibaba.mpc4j.s2pc.pso.psu.dc17;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * ACISP_DavCid17 (Davidson-Cid 2017) EIBF-based PSU.
 *
 * @author Weiran Liu
 * @date 2026/05/28
 */
public class Dc17PsuPtoDesc implements PtoDesc {
    /**
     * protocol ID
     */
    private static final int PTO_ID = Math.abs((int) 2578074052378137405L);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "DC17_EIBF_PSU";

    /**
     * singleton mode
     */
    private static final Dc17PsuPtoDesc INSTANCE = new Dc17PsuPtoDesc();

    /**
     * private constructor.
     */
    private Dc17PsuPtoDesc() {
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
         * client sends PHE public key and BF params (B, k, hash seeds)
         */
        CLIENT_SEND_SETUP,
        /**
         * client sends encrypted inverted bloom filter
         */
        CLIENT_SEND_EIBF,
        /**
         * server sends (p~, c~) pairs (randomly permuted)
         */
        SERVER_SEND_PAIRS,
    }
}

