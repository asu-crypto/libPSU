package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for {@link HaoWan26AltModExpand}.
 */
public class HaoWan26AltModExpandTest {
    @Test
    public void testExpandLengthAndDomain() {
        byte[] item = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        item[0] = 0x3C;
        item[7] = 0x5A;
        byte[] expanded = HaoWan26AltModExpand.expand(item);
        Assert.assertEquals(F32Wprf.N, expanded.length);
        for (byte b : expanded) {
            Assert.assertTrue(b == 0 || b == 1);
        }
        // Identity on first 128 bits
        for (int i = 0; i < CommonConstants.BLOCK_BIT_LENGTH; i++) {
            Assert.assertEquals(BinaryUtils.getBoolean(item, i) ? 1 : 0, expanded[i]);
        }
    }

    @Test
    public void testAllZeroInputForcesReservedBit() {
        byte[] zeros = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        byte[] expanded = HaoWan26AltModExpand.expand(zeros);
        Assert.assertEquals(1, expanded[HaoWan26AltModExpand.RESERVED_NONZERO_INDEX]);
        Assert.assertFalse(Arrays.equals(new byte[F32Wprf.N], expanded));
    }

    @Test
    public void testDeterministic() {
        byte[] item = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        Arrays.fill(item, (byte) 0x11);
        Assert.assertArrayEquals(
            HaoWan26AltModExpand.expand(item),
            HaoWan26AltModExpand.expand(item, HaoWan26AltModExpand.ExpandProfile.MPC4J_NATIVE)
        );
    }

    @Test
    public void testEllBitsFormula() {
        Assert.assertEquals(40, HaoWan26Truncate.ellBits(1));
        Assert.assertEquals(45, HaoWan26Truncate.ellBits(32));
        Assert.assertEquals(6, HaoWan26Truncate.ellBytes(32));
    }
}
