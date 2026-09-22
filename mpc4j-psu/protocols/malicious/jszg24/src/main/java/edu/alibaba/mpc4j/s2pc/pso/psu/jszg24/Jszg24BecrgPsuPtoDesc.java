package edu.alibaba.mpc4j.s2pc.pso.psu.jszg24;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * JSZG24 bECRG PSU protocol description.
 *
 * <p>Maps to JSZG24 (Jia–Sun–Zhou–Gu, 2024 / long version 2026-04-22) Section 5 / Figure 17:
 * Π^{bECRG}_PSU realizing F^{n1,n2}_{ePSU} (receiver outputs X ∪ Y, sender outputs Finished).</p>
 */
public class Jszg24BecrgPsuPtoDesc implements PtoDesc {
    /**
     * protocol ID (random fixed int)
     */
    private static final int PTO_ID = Math.abs((int) 0x4A535A47);
    /**
     * protocol name
     */
    private static final String PTO_NAME = "USENIX_YanShiHonDaw24";

    /**
     * protocol steps.
     */
    public enum PtoStep {
        /**
         * Sender sends hash keys for Cuckoo/simple hashing (Fig.17 Step 1).
         */
        HASH_KEYS,
        /**
         * Sender sends sampled t'_i (Fig.17 Step 2).
         */
        T_PRIME,
        /**
         * Receiver sends OPPRF pointNum (= total programmed points) so sender can run OPPRF receiver (Fig.17 Step 3).
         */
        OPPRF_POINT_NUM,
        /**
         * bECRG: PET (Fig.16 / Fig.17 Step 4).
         */
        BECRG_PET,
        /**
         * bECRG: eqOTe (Fig.16 / Fig.17 Step 4).
         */
        BECRG_EQOTE,
        /**
         * Permute+Share (FPS) (Fig.17 Step 5).
         */
        PERMUTE_SHARE,
        /**
         * Sender sends ciphertext candidates {c_i} (Fig.17 Step 6).
         */
        SEND_CIPHERTEXTS,
        /**
         * Output (Fig.17 Step 7-8).
         */
        OUTPUT,
    }

    /**
     * singleton
     */
    private static final Jszg24BecrgPsuPtoDesc INSTANCE = new Jszg24BecrgPsuPtoDesc();

    private Jszg24BecrgPsuPtoDesc() {
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

