package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;

public class Pt26IbltParamsHashKeyTest {
    @Test
    public void deepCopiesHashKeys() {
        SecureRandom random = new SecureRandom();
        byte[][] keys = new byte[Pt26IbltParams.DEFAULT_K][];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = BlockUtils.randomBlock(random);
        }
        Pt26IbltParams params = Pt26IbltParams.createDefault(4, 4, 16, keys);
        keys[0][0] ^= 0x5A;
        Assert.assertNotEquals(keys[0][0], params.getHashKey(0)[0]);
        byte[] fromGetter = params.getHashKey(1);
        fromGetter[0] ^= 0x11;
        Assert.assertNotEquals(fromGetter[0], params.getHashKey(1)[0]);
    }

    @Test
    public void rejectsWrongKeyCountOrLength() {
        SecureRandom random = new SecureRandom();
        byte[][] four = new byte[4][];
        for (int i = 0; i < four.length; i++) {
            four[i] = BlockUtils.randomBlock(random);
        }
        Assert.assertThrows(IllegalArgumentException.class,
            () -> Pt26IbltParams.createDefault(4, 4, 16, four));

        byte[][] six = new byte[6][];
        for (int i = 0; i < six.length; i++) {
            six[i] = BlockUtils.randomBlock(random);
        }
        Assert.assertThrows(IllegalArgumentException.class,
            () -> Pt26IbltParams.createDefault(4, 4, 16, six));

        byte[][] badLen = new byte[Pt26IbltParams.DEFAULT_K][];
        for (int i = 0; i < badLen.length; i++) {
            badLen[i] = BlockUtils.randomBlock(random);
        }
        badLen[2] = Arrays.copyOf(badLen[2], CommonConstants.BLOCK_BYTE_LENGTH - 1);
        Assert.assertThrows(IllegalArgumentException.class,
            () -> Pt26IbltParams.createDefault(4, 4, 16, badLen));
    }
}
