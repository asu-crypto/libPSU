package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

public class SmallEcWireUtilsTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void packUnpackPointsOnly() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[][] pts = new byte[2][];
        for (int i = 0; i < 2; i++) {
            byte[] item = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            RANDOM.nextBytes(item);
            pts[i] = codec.mapToPoint(item);
        }
        List<byte[]> wire = SmallEcWireUtils.packPoints(pts);
        Assert.assertEquals(2, wire.size());
        for (byte[] w : wire) {
            Assert.assertEquals(SmallEcConstants.POINT_BYTES, w.length);
        }
        byte[][] back = SmallEcWireUtils.unpackPoints(wire, 2);
        Assert.assertTrue(EcGroupOps.pointEqual(pts[0], back[0]));
        Assert.assertTrue(EcGroupOps.pointEqual(pts[1], back[1]));
    }

    @Test
    public void diffPayloadIsPointsOnly() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[] item = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
        RANDOM.nextBytes(item);
        byte[] base = codec.mapToPoint(item);
        List<byte[]> wire = SmallEcWireUtils.packDiffPoints(Arrays.asList(base));
        Assert.assertEquals(1, wire.size());
        Assert.assertEquals(SmallEcConstants.POINT_BYTES, wire.get(0).length);
        List<byte[]> back = SmallEcWireUtils.unpackDiffPoints(wire);
        Assert.assertEquals(1, back.size());
        Assert.assertTrue(EcGroupOps.pointEqual(base, back.get(0)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unpackRejectsWrongCount() {
        SmallEcWireUtils.unpackPoints(List.of(), 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void packRejectsWrongPointLength() {
        SmallEcWireUtils.packPoints(new byte[][] {new byte[31]});
    }

    @Test
    public void packUnpackFingerprints() {
        byte[] fp = new byte[8];
        RANDOM.nextBytes(fp);
        List<byte[]> wire = SmallEcWireUtils.packFingerprints(List.of(fp), 8);
        Assert.assertEquals(1, wire.size());
        Assert.assertEquals(8, wire.get(0).length);
        List<byte[]> back = SmallEcWireUtils.unpackFingerprints(wire, 1, 8);
        Assert.assertArrayEquals(fp, back.get(0));
    }

    @Test
    public void exactModeWPayloadSize() {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[][] pts = new byte[32][];
        for (int i = 0; i < 32; i++) {
            byte[] item = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
            RANDOM.nextBytes(item);
            pts[i] = codec.mapToPoint(item);
        }
        List<byte[]> wire = SmallEcWireUtils.packPoints(pts);
        Assert.assertEquals(32, wire.size());
        for (byte[] w : wire) {
            Assert.assertEquals(SmallEcConstants.POINT_BYTES, w.length);
        }
    }

    @Test
    public void testPackFingerprints64Shape() {
        int n = 32;
        int fpBytes = SmallEcFingerprintUtils.fingerprintByteLength(64);

        List<byte[]> fingerprints = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            byte[] fp = new byte[fpBytes];
            Arrays.fill(fp, (byte) i);
            fingerprints.add(fp);
        }

        List<byte[]> payload = SmallEcWireUtils.packFingerprints(fingerprints, fpBytes);

        Assert.assertEquals(n, payload.size());
        for (byte[] entry : payload) {
            Assert.assertEquals(8, entry.length);
        }
    }

    @Test
    public void fingerprintModeWPayloadSize() {
        byte[][] fps = new byte[32][];
        for (int i = 0; i < 32; i++) {
            fps[i] = new byte[8];
            RANDOM.nextBytes(fps[i]);
        }
        List<byte[]> wire = SmallEcWireUtils.packFingerprints(Arrays.asList(fps), 8);
        Assert.assertEquals(32, wire.size());
        for (byte[] w : wire) {
            Assert.assertEquals(8, w.length);
        }
    }
}

