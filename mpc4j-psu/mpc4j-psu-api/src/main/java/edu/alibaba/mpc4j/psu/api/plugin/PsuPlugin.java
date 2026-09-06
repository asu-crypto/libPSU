package edu.alibaba.mpc4j.psu.api.plugin;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.psu.api.PluginId;

/**
 * Base type for PSU pipeline plugins (membership, union delivery, offline).
 */
public interface PsuPlugin {
    PluginId getPluginId();

    void init(PsuPluginInit init) throws MpcAbortException;
}
