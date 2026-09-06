package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.TwoPartyPto;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * PSI client (learns intersection).
 */
public interface PsiClient extends TwoPartyPto {
    void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException;

    PsiClientOutput psi(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException;
}
