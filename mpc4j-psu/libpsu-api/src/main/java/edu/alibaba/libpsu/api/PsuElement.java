package edu.alibaba.libpsu.api;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable fixed-width set element for library callers.
 */
public final class PsuElement {
    private final byte[] value;

    private PsuElement(byte[] value) {
        if (value.length == 0) {
            throw new IllegalArgumentException("PSU elements must contain at least one byte");
        }
        this.value = value;
    }

    public static PsuElement copyOf(byte[] value) {
        Objects.requireNonNull(value, "value");
        return new PsuElement(value.clone());
    }

    public static PsuElement from(ByteBuffer value) {
        Objects.requireNonNull(value, "value");
        ByteBuffer duplicate = value.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return new PsuElement(bytes);
    }

    public int byteLength() {
        return value.length;
    }

    public byte[] toByteArray() {
        return value.clone();
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(toByteArray()).asReadOnlyBuffer();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PsuElement)) {
            return false;
        }
        PsuElement that = (PsuElement) obj;
        return Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "PsuElement[" + value.length + " bytes]";
    }
}
