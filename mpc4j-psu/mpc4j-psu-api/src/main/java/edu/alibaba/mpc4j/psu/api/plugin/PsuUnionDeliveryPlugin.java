package edu.alibaba.mpc4j.psu.api.plugin;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.psu.api.PsuUnionOutput;

/**
 * Phase B: COT / PHE union delivery to the union-holding party.
 */
public interface PsuUnionDeliveryPlugin extends PsuPlugin {
    PsuUnionOutput deliverUnion(UnionDeliveryInput input) throws MpcAbortException;
}
