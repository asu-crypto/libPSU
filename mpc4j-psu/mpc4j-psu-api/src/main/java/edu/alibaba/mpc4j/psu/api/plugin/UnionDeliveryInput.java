package edu.alibaba.mpc4j.psu.api.plugin;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * Input for union delivery after membership and server ciphertexts are received.
 */
public class UnionDeliveryInput {
    private final Set<ByteBuffer> smallSet;
    private final int largeSetSize;
    private final boolean[] cotChoices;
    private final List<byte[]> encPayload;
    private final int elementByteLength;

    public UnionDeliveryInput(
        Set<ByteBuffer> smallSet, int largeSetSize, int elementByteLength,
        boolean[] cotChoices, List<byte[]> encPayload) {
        this.smallSet = smallSet;
        this.largeSetSize = largeSetSize;
        this.elementByteLength = elementByteLength;
        this.cotChoices = cotChoices;
        this.encPayload = encPayload;
    }

    public Set<ByteBuffer> getSmallSet() {
        return smallSet;
    }

    public int getLargeSetSize() {
        return largeSetSize;
    }

    public boolean[] getCotChoices() {
        return cotChoices;
    }

    public List<byte[]> getEncPayload() {
        return encPayload;
    }

    public int getElementByteLength() {
        return elementByteLength;
    }
}
