package edu.alibaba.libpsu.api;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable PSU input set with union-semantics deduplication.
 */
public final class PsuInput {
    private final List<PsuElement> elements;
    private final int elementByteLength;

    private PsuInput(Collection<PsuElement> elements, int explicitElementByteLength) {
        Objects.requireNonNull(elements, "elements");
        if (explicitElementByteLength < 0) {
            throw new IllegalArgumentException("elementByteLength must be non-negative");
        }
        LinkedHashSet<PsuElement> deduplicated = new LinkedHashSet<>(elements);
        int detectedByteLength = explicitElementByteLength;
        for (PsuElement element : deduplicated) {
            Objects.requireNonNull(element, "element");
            if (detectedByteLength == 0) {
                detectedByteLength = element.byteLength();
            }
            if (element.byteLength() != detectedByteLength) {
                throw new IllegalArgumentException(
                    "all elements must have byte length " + detectedByteLength
                        + ", found " + element.byteLength()
                );
            }
        }
        this.elements = Collections.unmodifiableList(new ArrayList<>(deduplicated));
        this.elementByteLength = detectedByteLength;
    }

    public static PsuInput empty(int elementByteLength) {
        if (elementByteLength <= 0) {
            throw new IllegalArgumentException("elementByteLength must be positive");
        }
        return new PsuInput(Collections.emptyList(), elementByteLength);
    }

    public static PsuInput of(Collection<PsuElement> elements) {
        return new PsuInput(elements, 0);
    }

    public static PsuInput fromByteArrays(Collection<byte[]> elements) {
        Objects.requireNonNull(elements, "elements");
        List<PsuElement> psuElements = new ArrayList<>(elements.size());
        for (byte[] element : elements) {
            psuElements.add(PsuElement.copyOf(element));
        }
        return of(psuElements);
    }

    public static PsuInput fromByteBuffers(Collection<ByteBuffer> elements) {
        Objects.requireNonNull(elements, "elements");
        List<PsuElement> psuElements = new ArrayList<>(elements.size());
        for (ByteBuffer element : elements) {
            psuElements.add(PsuElement.from(element));
        }
        return of(psuElements);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int elementByteLength() {
        return elementByteLength;
    }

    public List<PsuElement> elements() {
        return elements;
    }

    public Set<ByteBuffer> toByteBufferSet() {
        LinkedHashSet<ByteBuffer> out = new LinkedHashSet<>(elements.size());
        for (PsuElement element : elements) {
            out.add(ByteBuffer.wrap(element.toByteArray()).asReadOnlyBuffer());
        }
        return Collections.unmodifiableSet(out);
    }
}
