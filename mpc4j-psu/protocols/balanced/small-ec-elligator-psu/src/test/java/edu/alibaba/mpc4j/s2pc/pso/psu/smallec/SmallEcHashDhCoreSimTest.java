package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Local Figure 8 HashDH simulation (no RPC). */
public class SmallEcHashDhCoreSimTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Test
    public void fullIntersectionFilterEmpty() {
        int n = 32;
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        ArrayList<ByteBuffer> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ByteBuffer bb = ByteBuffer.allocate(SmallEcConstants.ITEM_BYTE_LENGTH);
            bb.putInt(SmallEcConstants.ITEM_BYTE_LENGTH - Integer.BYTES, i);
            items.add(ByteBuffer.wrap(bb.array().clone()));
        }
        byte[][] serverItems = toItems(items);
        byte[][] clientItems = toItems(items);

        byte[] k0 = EcGroupOps.randomNonZeroScalar(RANDOM);
        byte[] k1 = EcGroupOps.randomNonZeroScalar(RANDOM);
        byte[][] v = SmallEcHashDhCore.blindItems(serverItems, k0, codec);
        byte[] shuffleSeed = SmallEcPermUtils.sampleSeed(RANDOM);
        int[] perm = SmallEcPermUtils.generatePermutation(n, shuffleSeed);
        byte[][] u = SmallEcHashDhCore.shuffleBlind(v, k1, perm);
        byte[][] w = SmallEcHashDhCore.blindItems(clientItems, k1, codec);
        Set<EcGroupOps.CanonicalPoint> wKeys = EcGroupOps.canonicalPointKeySet(w);
        List<byte[]> diff = SmallEcHashDhCore.filterDifference(u, EcGroupOps.invertScalar(k0), wKeys);
        Assert.assertEquals("expected empty difference for X=Y", 0, diff.size());
    }

    private static byte[][] toItems(ArrayList<ByteBuffer> list) {
        byte[][] out = new byte[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            out[i] = SmallEcWireUtils.fixedBytes(list.get(i), SmallEcConstants.ITEM_BYTE_LENGTH);
        }
        return out;
    }
}
