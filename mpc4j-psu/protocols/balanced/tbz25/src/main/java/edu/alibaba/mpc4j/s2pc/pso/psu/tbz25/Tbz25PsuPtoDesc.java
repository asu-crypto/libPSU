package edu.alibaba.mpc4j.s2pc.pso.psu.tbz25;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * USENIX_BinYujConYanYu25 balanced enhanced PSU (Tu–Bai–Zhang, USENIX Security 2025 / ePSU balanced).
 *
 * <p>pnMCRG + one-time pad: receiver outputs X ∪ Y, sender outputs Finished.</p>
 */
public class Tbz25PsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x54425A32);
    private static final String PTO_NAME = "USENIX_BinYujConYanYu25";

    public enum PtoStep {
        /**
         * Sender sends cuckoo hash keys for simple hashing on the receiver side.
         */
        CUCKOO_HASH_KEYS,
        /**
         * Sender sends XOR one-time pad ciphertexts after pnMCRG.
         */
        SEND_OTP,
    }

    private static final Tbz25PsuPtoDesc INSTANCE = new Tbz25PsuPtoDesc();

    private Tbz25PsuPtoDesc() {
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
}
