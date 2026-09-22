package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.core.set.ByteBufferSetSnapshot;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Immutable two-sided PSU output (both parties learn the union).
 */
public class PsuTwoSidedOutput {
    private final ByteBufferSetSnapshot union;

    public PsuTwoSidedOutput(Set<ByteBuffer> union) {
        this.union = ByteBufferSetSnapshot.copyOf(union);
    }

    public Set<ByteBuffer> getUnion() {
        return union.copyAsReadOnlySet();
    }
}
