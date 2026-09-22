package edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * Hao–Wan 2026 balanced enhanced PSU (ePSU-fast, USENIX Security 2026).
 *
 * <p>ssPMT-fast + ssOTd: receiver outputs X ∪ Y, sender outputs Finished.</p>
 */
public class HaoWan2026PsuPtoDesc implements PtoDesc {
    private static final int PTO_ID = Math.abs((int) 0x484F3236L);
    private static final String PTO_NAME = "USENIX_HaoWan26";

    private static final HaoWan2026PsuPtoDesc INSTANCE = new HaoWan2026PsuPtoDesc();

    private HaoWan2026PsuPtoDesc() {
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
