package edu.alibaba.libpsu.core.set;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ProtocolElementVectorTest {
    @Test
    public void realElementsHaveNoPadding() {
        byte[] a = new byte[]{1, 2, 3};
        byte[] b = new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        ProtocolElementVector vector = ProtocolElementVector.ofRealElements(Arrays.asList(a, b), 3);
        Assert.assertEquals(2, vector.size());
        Assert.assertEquals(2, vector.realElementCount());
        Assert.assertFalse(vector.isPadding(0));
        Assert.assertFalse(vector.isPadding(1));
        Assert.assertArrayEquals(a, vector.value(0));
        Assert.assertArrayEquals(b, vector.value(1));
    }

    @Test
    public void slotsTrackPaddingOutOfBand() {
        byte[] real = new byte[]{9, 9};
        ProtocolElementVector vector = ProtocolElementVector.ofSlots(new byte[][]{real, null, real}, 2);
        Assert.assertEquals(3, vector.size());
        Assert.assertEquals(2, vector.realElementCount());
        Assert.assertFalse(vector.isPadding(0));
        Assert.assertTrue(vector.isPadding(1));
        Assert.assertFalse(vector.isPadding(2));
        Assert.assertArrayEquals(real, vector.value(0));
    }

    @Test(expected = IllegalStateException.class)
    public void paddingSlotHasNoUserValue() {
        ProtocolElementVector vector = ProtocolElementVector.ofSlots(new byte[][]{null}, 4);
        vector.value(0);
    }

    @Test
    public void emptyRealListAllowed() {
        ProtocolElementVector vector = ProtocolElementVector.ofRealElements(Collections.emptyList(), 8);
        Assert.assertEquals(0, vector.size());
        Assert.assertEquals(0, vector.realElementCount());
    }
}
