package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Two-sided PSU server (both parties learn the union).
 */
public interface PsuTwoSidedServer extends TwoPartyPto {
    void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException;

    PsuTwoSidedOutput psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException;
}
