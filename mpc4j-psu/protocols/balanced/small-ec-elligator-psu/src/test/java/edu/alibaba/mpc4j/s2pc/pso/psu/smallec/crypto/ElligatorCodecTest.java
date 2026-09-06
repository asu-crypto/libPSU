package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class ElligatorCodecTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void testDirectStrictRoundTripWithoutCofactor() throws MpcAbortException {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        int failures = 0;
        int samples = 100_000;

        for (int i = 0; i < samples; i++) {
            int sampleIndex = i;
            byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            RANDOM.nextBytes(x);

            byte[] h = codec.mapToPointRawForTest(x);
            byte[] recovered = codec.inversePointToItemRawForTest(h)
                .orElseThrow(() -> new AssertionError("raw inverse failed at i=" + sampleIndex));

            if (!Arrays.equals(x, recovered)) {
                failures++;
                if (failures <= 3) {
                    Assert.fail("raw roundtrip mismatch at i=" + sampleIndex);
                }
            }
        }
        Assert.assertEquals(
            "raw strict roundtrip without cofactor must succeed for all samples",
            0,
            failures
        );
    }

    @Test
    public void testStrictInverse100k() throws MpcAbortException {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        for (int i = 0; i < 100_000; i++) {
            byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            RANDOM.nextBytes(x);

            byte[] h = codec.mapToPoint(x);
            byte[] recovered = codec.inversePointToItemStrict(h);

            Assert.assertArrayEquals(x, recovered);
        }
    }

    @Test
    public void testStrictInverseForRandom128BitItems() throws MpcAbortException {
        testStrictInverse100k();
    }

    @Test
    public void testDhRoundTripStrictInverse() throws MpcAbortException {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();

        for (int i = 0; i < 10_000; i++) {
            byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            RANDOM.nextBytes(x);

            byte[] k0 = EcGroupOps.randomNonZeroScalar(RANDOM);
            byte[] k1 = EcGroupOps.randomNonZeroScalar(RANDOM);

            byte[] h = codec.mapToPoint(x);
            byte[] v = EcGroupOps.scalarMul(h, k0);
            byte[] u = EcGroupOps.scalarMul(v, k1);

            byte[] invK0 = EcGroupOps.invertScalar(k0);
            byte[] uPrime = EcGroupOps.scalarMul(u, invK0);

            byte[] invK1 = EcGroupOps.invertScalar(k1);
            byte[] recoveredH = EcGroupOps.scalarMul(uPrime, invK1);

            byte[] recoveredX = codec.inversePointToItemStrict(recoveredH);
            Assert.assertArrayEquals(x, recoveredX);
        }
    }

    @Test
    public void testStrictInverseEdgeCases() throws MpcAbortException {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();

        byte[] zero = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
        Assert.assertArrayEquals(
            zero, codec.inversePointToItemStrict(codec.mapToPoint(zero))
        );

        byte[] ff = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
        Arrays.fill(ff, (byte) 0xFF);
        Assert.assertArrayEquals(
            ff, codec.inversePointToItemStrict(codec.mapToPoint(ff))
        );

        byte[] ascending = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
        for (int i = 0; i < ascending.length; i++) {
            ascending[i] = (byte) i;
        }
        Assert.assertArrayEquals(
            ascending,
            codec.inversePointToItemStrict(codec.mapToPoint(ascending))
        );
    }

    @Test
    public void testSubgroupScalarCancellation256() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        SecureRandom rnd = new SecureRandom();
        rnd.setSeed(20260906L);
        for (int i = 0; i < 256; i++) {
            byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            rnd.nextBytes(x);
            byte[] h = codec.mapToPoint(x);
            byte[] k = EcGroupOps.randomNonZeroScalar(rnd);
            byte[] inv = EcGroupOps.invertScalar(k);
            byte[] recovered = EcGroupOps.scalarMul(EcGroupOps.scalarMul(h, k), inv);
            Assert.assertArrayEquals("scalar cancellation failed at i=" + i, h, recovered);
        }
    }

    @Test
    public void testDeterministicRoundTrip256() throws MpcAbortException {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        SecureRandom rnd = new SecureRandom();
        rnd.setSeed(20260906L);
        for (int i = 0; i < 256; i++) {
            byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            rnd.nextBytes(x);
            Assert.assertArrayEquals(x, codec.inversePointToItemStrict(codec.mapToPoint(x)));
        }
    }

    @Test
    public void testMapToPointDeterministic() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
        Arrays.fill(x, (byte) 0x42);

        byte[] p0 = codec.mapToPoint(x);
        byte[] p1 = codec.mapToPoint(x);

        Assert.assertArrayEquals(p0, p1);
    }

    @Test
    public void testNoCollisionInRandomSample() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        Set<EcGroupOps.CanonicalPoint> seen = new HashSet<>();

        for (int i = 0; i < 100_000; i++) {
            byte[] x = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            RANDOM.nextBytes(x);

            byte[] p = codec.mapToPoint(x);
            Assert.assertTrue(seen.add(EcGroupOps.canonicalPoint(p)));
        }
    }

    @Test
    public void invalidPointReturnsEmpty() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[] garbage = new byte[SmallEcConstants.POINT_BYTES];
        RANDOM.nextBytes(garbage);
        garbage[31] &= 0x7F;
        if (EcGroupOps.isValidPoint(garbage)) {
            return;
        }
        try {
            codec.inversePointToItem(garbage);
            Assert.fail("expected invalid point");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void strictInverseAbortsOnNonImage() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[] garbage = new byte[SmallEcConstants.POINT_BYTES];
        RANDOM.nextBytes(garbage);
        garbage[31] &= 0x7F;
        if (!EcGroupOps.isValidPoint(garbage)) {
            return;
        }
        Optional<byte[]> inv = codec.inversePointToItem(garbage);
        if (inv.isPresent()) {
            return;
        }
        try {
            codec.inversePointToItemStrict(garbage);
            Assert.fail("expected abort");
        } catch (MpcAbortException expected) {
            // ok
        }
    }
}
