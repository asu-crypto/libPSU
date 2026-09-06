package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Local Figure 8 simulation with optional fingerprint W comparison. */
public class SmallEcFingerprintFilterTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void fullIntersectionFingerprintFilterEmpty() {
        simulate(32, 32, 32, true);
    }

    @Test
    public void disjointFingerprintFilterFullDiff() {
        simulate(32, 32, 0, true);
    }

    @Test
    public void partialOverlapFingerprintFilter() {
        simulate(32, 32, 16, true);
    }

    @Test
    public void exactModeFullIntersection() {
        simulate(32, 32, 32, false);
    }

    private static void simulate(int n, int serverSize, int intersectionSize, boolean fingerprintMode) {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        SmallEcElligatorPsuConfig config = fingerprintMode
            ? new SmallEcElligatorPsuConfig.Builder()
                .setWCompareMode(SmallEcElligatorPsuConfig.WCompareMode.TRUNCATED_W_PROBABILISTIC)
                .setFingerprintBitLength(64)
                .build()
            : new SmallEcElligatorPsuConfig.Builder().build();

        ArrayList<ByteBuffer> serverItems = items(serverSize, 1);
        ArrayList<ByteBuffer> clientItems = items(n, 2);
        for (int i = 0; i < intersectionSize; i++) {
            clientItems.set(i, serverItems.get(i));
        }

        byte[][] x = toBytes(serverItems);
        byte[][] y = toBytes(clientItems);

        byte[] k0 = EcGroupOps.randomNonZeroScalar(RANDOM);
        byte[] k1 = EcGroupOps.randomNonZeroScalar(RANDOM);
        byte[][] v = SmallEcHashDhCore.blindItems(x, k0, codec);
        byte[] shuffleSeed = SmallEcPermUtils.sampleSeed(RANDOM);
        int[] perm = SmallEcPermUtils.generatePermutation(n, shuffleSeed);
        byte[][] u = SmallEcHashDhCore.shuffleBlind(v, k1, perm);
        byte[][] w = SmallEcHashDhCore.blindItems(y, k1, codec);
        byte[] invK0 = EcGroupOps.invertScalar(k0);

        int expectedDiff = n - intersectionSize;
        List<byte[]> diff;

        if (fingerprintMode) {
            int lambda = config.getResolvedFingerprintBitLength(n);
            List<byte[]> wFp = SmallEcFingerprintUtils.fingerprintPoints(
                w, lambda, config.getFingerprintMethod()
            );
            Set<FingerprintKey> wKeys = SmallEcFingerprintUtils.fingerprintKeySet(
                wFp, SmallEcFingerprintUtils.fingerprintByteLength(lambda)
            );
            diff = SmallEcHashDhCore.filterDifferenceByFingerprint(u, invK0, wKeys, lambda, config.getFingerprintMethod());
        } else {
            Set<EcGroupOps.CanonicalPoint> wKeys = EcGroupOps.canonicalPointKeySet(w);
            diff = SmallEcHashDhCore.filterDifference(u, invK0, wKeys);
        }

        Assert.assertEquals(expectedDiff, diff.size());
        for (byte[] p : diff) {
            Assert.assertEquals(SmallEcConstants.POINT_BYTES, p.length);
        }
    }

    private static ArrayList<ByteBuffer> items(int size, int tag) {
        ArrayList<ByteBuffer> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ByteBuffer bb = ByteBuffer.allocate(SmallEcConstants.ITEM_BYTE_LENGTH);
            bb.putInt(SmallEcConstants.ITEM_BYTE_LENGTH - Integer.BYTES * 2, tag);
            bb.putInt(SmallEcConstants.ITEM_BYTE_LENGTH - Integer.BYTES, i);
            out.add(ByteBuffer.wrap(bb.array().clone()));
        }
        return out;
    }

    private static byte[][] toBytes(ArrayList<ByteBuffer> list) {
        byte[][] out = new byte[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            out[i] = SmallEcWireUtils.fixedBytes(list.get(i), SmallEcConstants.ITEM_BYTE_LENGTH);
        }
        return out;
    }
}
