package edu.alibaba.mpc4j.psu.api;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable union result shared across balanced and unbalanced APIs.
 */
public class PsuUnionOutput {
    private final Set<ByteBuffer> unionSet;
    private final int psica;

    public PsuUnionOutput(Set<ByteBuffer> unionSet, int psica) {
        Objects.requireNonNull(unionSet, "unionSet");
        if (psica < 0 || psica > unionSet.size()) {
            throw new IllegalArgumentException("PSI-CA must be in [0, |union|]");
        }
        LinkedHashSet<ByteBuffer> copied = new LinkedHashSet<>(unionSet.size());
        for (ByteBuffer buffer : unionSet) {
            Objects.requireNonNull(buffer, "element");
            ByteBuffer dup = buffer.duplicate();
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            copied.add(ByteBuffer.wrap(bytes).asReadOnlyBuffer());
        }
        this.unionSet = Collections.unmodifiableSet(copied);
        this.psica = psica;
    }

    public Set<ByteBuffer> getUnionSet() {
        LinkedHashSet<ByteBuffer> out = new LinkedHashSet<>(unionSet.size());
        for (ByteBuffer buffer : unionSet) {
            ByteBuffer dup = buffer.duplicate();
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            out.add(ByteBuffer.wrap(bytes).asReadOnlyBuffer());
        }
        return Collections.unmodifiableSet(out);
    }

    public int getPsica() {
        return psica;
    }
}
