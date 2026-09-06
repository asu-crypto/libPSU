package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * EUROCRYPT_PisTri26 PSU protocol (Piske &amp; Trieu, EUROCRYPT 2026): IBLT-based PSU with UnionPeel.
 *
 * @author mpc4j
 * @date 2026/05/17
 */
class Pt26PsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 9182736450192837465L);
    private static final String PTO_NAME = "PT26_PSU";

    enum PtoStep {
        SERVER_SEND_HASH_KEYS,
        CLIENT_SEND_OT12_PAYLOAD,
        SERVER_SEND_OT3_PAYLOAD,
        CLIENT_SEND_PEEL_VALUES,
    }

    private static final Pt26PsuPtoDesc INSTANCE = new Pt26PsuPtoDesc();

    private Pt26PsuPtoDesc() {
        // empty
    }

    static Pt26PsuPtoDesc getInstance() {
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
