package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.libpsu.core.set.ByteBufferSetSnapshot;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Immutable UPSU receiver output.
 */
public class UpsuReceiverOutput {
    private final ByteBufferSetSnapshot unionSet;
    private final int psica;

    public UpsuReceiverOutput(Set<ByteBuffer> unionSet, int psica) {
        this.unionSet = ByteBufferSetSnapshot.copyOf(unionSet);
        this.psica = psica;
    }

    public Set<ByteBuffer> getUnion() {
        return unionSet.copyAsReadOnlySet();
    }

    public int getPsica() {
        return psica;
    }
}
