package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

public class EcGroupOpsTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void blindUnblindK0CancelsK1() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[] k0 = EcGroupOps.randomNonZeroScalar(RANDOM);
        byte[] k1 = EcGroupOps.randomNonZeroScalar(RANDOM);
        for (int i = 0; i < 32; i++) {
            ByteBuffer bb = ByteBuffer.allocate(16);
            bb.putInt(12, i);
            byte[] h = codec.mapToPoint(bb.array());
            byte[] u = EcGroupOps.scalarMul(EcGroupOps.scalarMul(h, k0), k1);
            byte[] uPrime = EcGroupOps.scalarMul(u, EcGroupOps.invertScalar(k0));
            byte[] direct = EcGroupOps.scalarMul(h, k1);
            Assert.assertTrue("item index " + i, EcGroupOps.pointEqual(uPrime, direct));
        }
    }
}
