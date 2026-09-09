package edu.alibaba.libpsu.core.set;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable deep-copied snapshot of a {@code Set<ByteBuffer>} of set elements.
 *
 * <p>Callers cannot mutate internal state via the original set, shared arrays, or returned buffers.
 */
public final class ByteBufferSetSnapshot {
    private final List<byte[]> elements;

    private ByteBufferSetSnapshot(List<byte[]> elements) {
        this.elements = elements;
    }

    public static ByteBufferSetSnapshot copyOf(Set<ByteBuffer> input) {
        Objects.requireNonNull(input, "input");
        // Preserve set semantics by content while deep-copying bytes.
        LinkedHashSet<ByteBuffer> dedup = new LinkedHashSet<>();
        List<byte[]> copied = new ArrayList<>(input.size());
        for (ByteBuffer buffer : input) {
            Objects.requireNonNull(buffer, "element");
            ByteBuffer dup = buffer.duplicate();
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            ByteBuffer key = ByteBuffer.wrap(bytes);
            if (dedup.add(key)) {
                copied.add(bytes);
            }
        }
        return new ByteBufferSetSnapshot(Collections.unmodifiableList(copied));
    }

    public int size() {
        return elements.size();
    }

    /**
     * Fresh read-only {@link ByteBuffer}s over cloned arrays (safe against position mutation).
     */
    public Set<ByteBuffer> copyAsReadOnlySet() {
        LinkedHashSet<ByteBuffer> out = new LinkedHashSet<>(elements.size());
        for (byte[] element : elements) {
            out.add(ByteBuffer.wrap(element.clone()).asReadOnlyBuffer());
        }
        return Collections.unmodifiableSet(out);
    }
}
