package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.core.set.ByteBufferSetSnapshot;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Immutable PSU client output.
 */
public class PsuClientOutput {
    private final ByteBufferSetSnapshot union;
    private final int psica;

    public PsuClientOutput(Set<ByteBuffer> union, int psica) {
        this.union = ByteBufferSetSnapshot.copyOf(union);
        MathPreconditions.checkNonNegativeInRangeClosed("PSI-CA", psica, this.union.size());
        this.psica = psica;
    }

    public Set<ByteBuffer> getUnion() {
        return union.copyAsReadOnlySet();
    }

    public int getPsiCa() {
        return psica;
    }
}
