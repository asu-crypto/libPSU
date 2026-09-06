package edu.alibaba.mpc4j.psu.api;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Union result shared across balanced and unbalanced APIs.
 */
public class PsuUnionOutput {
    private final Set<ByteBuffer> unionSet;
    private final int psica;

    public PsuUnionOutput(Set<ByteBuffer> unionSet, int psica) {
        this.unionSet = unionSet;
        this.psica = psica;
    }

    public Set<ByteBuffer> getUnionSet() {
        return unionSet;
    }

    public int getPsica() {
        return psica;
    }
}
