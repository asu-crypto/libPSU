package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.libpsu.core.set.ByteBufferSetSnapshot;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Immutable PSI client output (intersection from the client's view).
 */
public class PsiClientOutput {
    private final ByteBufferSetSnapshot intersection;

    public PsiClientOutput(Set<ByteBuffer> intersection) {
        this.intersection = ByteBufferSetSnapshot.copyOf(intersection);
    }

    public Set<ByteBuffer> getIntersection() {
        return intersection.copyAsReadOnlySet();
    }

    public int getIntersectionSize() {
        return intersection.size();
    }
}
