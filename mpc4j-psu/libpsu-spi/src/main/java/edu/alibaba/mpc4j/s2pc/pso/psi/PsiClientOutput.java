package edu.alibaba.mpc4j.s2pc.pso.psi;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * PSI client output (intersection from the client's view).
 */
public class PsiClientOutput {
    private final Set<ByteBuffer> intersection;

    public PsiClientOutput(Set<ByteBuffer> intersection) {
        this.intersection = intersection;
    }

    public Set<ByteBuffer> getIntersection() {
        return intersection;
    }

    public int getIntersectionSize() {
        return intersection.size();
    }
}
