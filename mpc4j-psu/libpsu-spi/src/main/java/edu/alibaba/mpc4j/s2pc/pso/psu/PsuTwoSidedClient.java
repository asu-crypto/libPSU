package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Two-sided PSU client (both parties learn the union).
 */
public interface PsuTwoSidedClient extends TwoPartyPto {
    void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException;

    PsuTwoSidedOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException;
}
