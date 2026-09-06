package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * PSI server (holder of the larger / second set in benchmarks).
 */
public interface PsiServer extends TwoPartyPto {
    void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException;

    void psi(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) throws MpcAbortException;
}
