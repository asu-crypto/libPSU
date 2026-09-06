package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * PSI protocol config.
 */
public interface PsiConfig extends MultiPartyPtoConfig {
    /**
     * Gets protocol type.
     *
     * @return protocol type.
     */
    PsiType getPtoType();
}
