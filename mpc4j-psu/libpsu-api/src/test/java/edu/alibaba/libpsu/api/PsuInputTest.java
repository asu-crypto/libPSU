package edu.alibaba.libpsu.api;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class PsuInputTest {
    @Test
    public void testDeduplicatesAndDefensivelyCopies() {
        byte[] a = new byte[] {1, 2, 3, 4};
        byte[] b = new byte[] {1, 2, 3, 4};
        byte[] c = new byte[] {4, 3, 2, 1};

        PsuInput input = PsuInput.fromByteArrays(Arrays.asList(a, b, c));
        a[0] = 9;

        Assert.assertEquals(2, input.size());
        Assert.assertEquals(4, input.elementByteLength());
        Assert.assertArrayEquals(new byte[] {1, 2, 3, 4}, input.elements().get(0).toByteArray());
    }

    @Test
    public void testSupportsEmptyAndSingletonInputs() {
        Assert.assertTrue(PsuInput.empty(16).isEmpty());
        PsuInput singleton = PsuInput.fromByteBuffers(
            Arrays.asList(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}))
        );
        Assert.assertEquals(1, singleton.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsMixedElementLengths() {
        PsuInput.fromByteArrays(Arrays.asList(new byte[] {1, 2}, new byte[] {1, 2, 3}));
    }
}
