package edu.alibaba.libpsu.core.set;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-length protocol elements with out-of-band padding metadata.
 *
 * <p>Padding is never encoded as a reserved user byte string. Callers must use
 * {@link #isPadding(int)} rather than comparing values to a BOT sentinel.
 */
public final class ProtocolElementVector {
    private final int elementByteLength;
    private final List<byte[]> values;
    private final BitSet paddingSlots;

    private ProtocolElementVector(int elementByteLength, List<byte[]> values, BitSet paddingSlots) {
        this.elementByteLength = elementByteLength;
        this.values = values;
        this.paddingSlots = paddingSlots;
    }

    public static ProtocolElementVector ofRealElements(List<byte[]> elements, int elementByteLength) {
        Objects.requireNonNull(elements, "elements");
        SetElementUtils.validateElementByteLength(elementByteLength);
        List<byte[]> copied = new ArrayList<>(elements.size());
        for (byte[] element : elements) {
            Objects.requireNonNull(element, "element");
            if (element.length != elementByteLength) {
                throw new IllegalArgumentException(
                    "element length " + element.length + " != expected " + elementByteLength
                );
            }
            copied.add(element.clone());
        }
        return new ProtocolElementVector(
            elementByteLength,
            Collections.unmodifiableList(copied),
            new BitSet(copied.size())
        );
    }

    /**
     * Builds a vector of {@code totalSlots} entries where padding slots hold no user value.
     * Non-padding slots must supply exactly {@code elementByteLength} bytes.
     */
    public static ProtocolElementVector ofSlots(byte[][] slotValuesOrNull, int elementByteLength) {
        Objects.requireNonNull(slotValuesOrNull, "slotValuesOrNull");
        SetElementUtils.validateElementByteLength(elementByteLength);
        List<byte[]> values = new ArrayList<>(slotValuesOrNull.length);
        BitSet padding = new BitSet(slotValuesOrNull.length);
        for (int i = 0; i < slotValuesOrNull.length; i++) {
            byte[] slot = slotValuesOrNull[i];
            if (slot == null) {
                padding.set(i);
                values.add(null);
            } else {
                if (slot.length != elementByteLength) {
                    throw new IllegalArgumentException(
                        "element length " + slot.length + " != expected " + elementByteLength
                    );
                }
                values.add(slot.clone());
            }
        }
        return new ProtocolElementVector(elementByteLength, Collections.unmodifiableList(values), padding);
    }

    public int size() {
        return values.size();
    }

    public int elementByteLength() {
        return elementByteLength;
    }

    public boolean isPadding(int index) {
        checkIndex(index);
        return paddingSlots.get(index);
    }

    public byte[] value(int index) {
        checkIndex(index);
        if (isPadding(index)) {
            throw new IllegalStateException("padding slot has no user value");
        }
        return values.get(index).clone();
    }

    public int realElementCount() {
        return size() - paddingSlots.cardinality();
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= values.size()) {
            throw new IndexOutOfBoundsException("index " + index + " not in [0, " + values.size() + ")");
        }
    }
}
