package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

public class FeistelIdealPermutationTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void inverseRoundTrip() {
        IdealPermutation perm = FeistelIdealPermutation.protocolInstance();
        byte[] x = new byte[SmallEcConstants.DOMAIN_GAMMA_BYTES];
        RANDOM.nextBytes(x);
        byte[] y = perm.permute(x);
        byte[] back = perm.inversePermute(y);
        Assert.assertArrayEquals(x, back);
        byte[] y2 = perm.permute(back);
        Assert.assertArrayEquals(y, y2);
    }
}
