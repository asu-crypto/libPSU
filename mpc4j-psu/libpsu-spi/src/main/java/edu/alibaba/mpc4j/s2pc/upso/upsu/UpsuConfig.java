package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

/**
 * UPSU config interface.
 *
 * @author Liqiang Peng
 * @date 2024/3/7
 */
public interface UpsuConfig extends MultiPartyPtoConfig {
    /**
     * return protocol type.
     *
     * @return protocol type.
     */
    UpsuType getPtoType();
}
