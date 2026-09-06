package edu.alibaba.mpc4j.psu.api.plugin;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Online input for membership phase.
 */
public class MembershipInput {
    private final Set<ByteBuffer> smallSet;
    private final int largeSetSize;

    public MembershipInput(Set<ByteBuffer> smallSet, int largeSetSize) {
        this.smallSet = smallSet;
        this.largeSetSize = largeSetSize;
    }

    public Set<ByteBuffer> getSmallSet() {
        return smallSet;
    }

    public int getLargeSetSize() {
        return largeSetSize;
    }
}
