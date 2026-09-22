package edu.alibaba.libpsu.core.set;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Regression: snapshots must isolate callers from shared mutable buffers.
 */
public class ByteBufferSetSnapshotTest {

    @Test
    public void copyIsolatesFromCallerMutation() {
        byte[] raw = new byte[]{1, 2, 3, 4};
        Set<ByteBuffer> input = new HashSet<>();
        input.add(ByteBuffer.wrap(raw));

        ByteBufferSetSnapshot snap = ByteBufferSetSnapshot.copyOf(input);
        raw[0] = (byte) 0xFF;
        input.clear();

        Set<ByteBuffer> view = snap.copyAsReadOnlySet();
        Assert.assertEquals(1, view.size());
        ByteBuffer only = view.iterator().next();
        Assert.assertEquals(1, only.get(0));
        Assert.assertTrue(only.isReadOnly());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void returnedSetIsUnmodifiable() {
        Set<ByteBuffer> input = new HashSet<>();
        input.add(ByteBuffer.wrap(new byte[]{9}));
        ByteBufferSetSnapshot.copyOf(input).copyAsReadOnlySet().add(ByteBuffer.wrap(new byte[]{1}));
    }
}
