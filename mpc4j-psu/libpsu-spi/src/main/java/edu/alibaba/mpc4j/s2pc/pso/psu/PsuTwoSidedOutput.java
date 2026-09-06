package edu.alibaba.mpc4j.s2pc.pso.psu;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Two-sided PSU output (both parties learn the union).
 */
public class PsuTwoSidedOutput {
    private final Set<ByteBuffer> union;

    public PsuTwoSidedOutput(Set<ByteBuffer> union) {
        this.union = union;
    }

    public Set<ByteBuffer> getUnion() {
        return union;
    }
}
