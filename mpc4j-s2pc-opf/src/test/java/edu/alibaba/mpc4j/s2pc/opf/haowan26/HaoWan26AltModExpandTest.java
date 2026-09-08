package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.opf.haowan26.HaoWan26AltModExpand.ExpandProfile;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for {@link HaoWan26AltModExpand}.
 */
public class HaoWan26AltModExpandTest {
    @Test
    public void testExpandLengthAndDomainSecureJoin() {
        byte[] item = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        item[0] = 0x3C;
        item[7] = 0x5A;
        byte[] expanded = HaoWan26AltModExpand.expand(item);
        Assert.assertEquals(F32Wprf.N, expanded.length);
        for (byte b : expanded) {
            Assert.assertTrue(b == 0 || b == 1);
        }
        // Systematic G: first 128 bits equal the LE bit string of the input block.
        for (int i = 0; i < CommonConstants.BLOCK_BIT_LENGTH; i++) {
            boolean bit = ((item[i >> 3] >> (i & 7)) & 1) != 0;
            Assert.assertEquals(bit ? 1 : 0, expanded[i]);
        }
    }

    @Test
    public void testSecureJoinZeroRejectedAtBoundary() {
        byte[] zeros = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        // Exact G(0)=0 remains available on the raw loader; public expand rejects it.
        Assert.assertArrayEquals(
            new byte[F32Wprf.N],
            edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams.expandG(zeros)
        );
        try {
            HaoWan26AltModExpand.expand(zeros, ExpandProfile.HAO_WAN_SECURE_JOIN);
            Assert.fail("expected rejection of G(0)=0");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("G(x)=0"));
        }
    }

    @Test
    public void testMpc4jNativeAllZeroForcesReservedBit() {
        byte[] zeros = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        byte[] expanded = HaoWan26AltModExpand.expand(zeros, ExpandProfile.MPC4J_NATIVE);
        Assert.assertEquals(1, expanded[HaoWan26AltModExpand.RESERVED_NONZERO_INDEX]);
        Assert.assertFalse(Arrays.equals(new byte[F32Wprf.N], expanded));
    }

    @Test
    public void testMpc4jNativeIdentityPrefix() {
        byte[] item = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        item[0] = 0x3C;
        byte[] expanded = HaoWan26AltModExpand.expand(item, ExpandProfile.MPC4J_NATIVE);
        for (int i = 0; i < CommonConstants.BLOCK_BIT_LENGTH; i++) {
            Assert.assertEquals(BinaryUtils.getBoolean(item, i) ? 1 : 0, expanded[i]);
        }
    }

    @Test
    public void testDeterministicSecureJoinDefault() {
        byte[] item = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        Arrays.fill(item, (byte) 0x11);
        Assert.assertArrayEquals(
            HaoWan26AltModExpand.expand(item),
            HaoWan26AltModExpand.expand(item, ExpandProfile.HAO_WAN_SECURE_JOIN)
        );
    }

    @Test
    public void testEllBitsFormula() {
        Assert.assertEquals(40, HaoWan26Truncate.ellBits(1));
        Assert.assertEquals(45, HaoWan26Truncate.ellBits(32));
        Assert.assertEquals(6, HaoWan26Truncate.ellBytes(32));
    }
}
