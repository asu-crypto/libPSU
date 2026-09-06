package edu.alibaba.mpc4j.psu.api.plugin;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

/**
 * Phase A: derive membership / PEQT between encoded small and large sets.
 */
public interface PsuMembershipPlugin extends PsuPlugin {
    MembershipOutput runMembership(MembershipInput input) throws MpcAbortException;
}
