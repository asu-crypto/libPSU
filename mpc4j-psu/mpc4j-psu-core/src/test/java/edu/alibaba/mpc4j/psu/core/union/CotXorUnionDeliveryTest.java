package edu.alibaba.mpc4j.psu.core.union;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for tagged COT-XOR union wire encoding (no reserved all-0xFF user sentinel).
 */
public class CotXorUnionDeliveryTest {
    @Test
    public void wireByteLengthIncludesTag() {
        Assert.assertEquals(17, CotXorUnionDelivery.wireByteLength(16));
    }

    @Test
    public void encodeRealElement() {
        byte[] element = new byte[8];
        Arrays.fill(element, (byte) 0xFF);
        byte[] wire = CotXorUnionDelivery.encodeRealElement(element);
        Assert.assertEquals(9, wire.length);
        Assert.assertEquals(CotXorUnionDelivery.TAG_REAL, wire[0]);
        Assert.assertArrayEquals(element, Arrays.copyOfRange(wire, 1, wire.length));
    }

    @Test
    public void encodePaddingIsTaggedZeros() {
        byte[] wire = CotXorUnionDelivery.encodePadding(4);
        Assert.assertEquals(5, wire.length);
        Assert.assertEquals(CotXorUnionDelivery.TAG_PADDING, wire[0]);
        for (int i = 1; i < wire.length; i++) {
            Assert.assertEquals(0, wire[i]);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void wireByteLengthRejectsNonPositive() {
        CotXorUnionDelivery.wireByteLength(0);
    }
}
