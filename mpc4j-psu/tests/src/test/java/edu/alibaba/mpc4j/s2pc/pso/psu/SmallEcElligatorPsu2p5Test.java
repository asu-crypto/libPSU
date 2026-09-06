package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcHashDhCore;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcPermUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Ours correctness at 2^5 (Figure 8, ℓ = 128 bits).
 */
public class SmallEcElligatorPsu2p5Test extends AbstractTwoPartyMemoryRpcPto {
    private static final int N = 1 << 5;

    public SmallEcElligatorPsu2p5Test() {
        super("SMALL_EC_ELLIGATOR_PSU_2P5");
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_EMPTY_INTERSECTION() throws Exception {
        runOnce(N, N, 0);
        runOnceRandomBytes(N, N, 0);
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_FULL_INTERSECTION() throws Exception {
        runOnce(N, N, N);
        runOnceRandomBytes(N, N, N);
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_PARTIAL_INTERSECTION() throws Exception {
        runOnce(N, N, N / 2);
        runOnceRandomBytes(N, N, N / 2);
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_ONE_DIFFERENCE() throws Exception {
        runOnce(N, N, N - 1);
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_RANDOM_TRIALS() throws Exception {
        for (int t = 0; t < 100; t++) {
            runOnce(N, N, SECURE_RANDOM.nextInt(N + 1));
        }
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_DEDUP_INPUTS() throws Exception {
        int elementByteLength = SmallEcConstants.ITEM_BYTE_LENGTH;
        Set<ByteBuffer> serverSet = multisetSet(elementByteLength, 1, 1, 2);
        Set<ByteBuffer> clientSet = multisetSet(elementByteLength, 1, 3, 3);
        int n = 2;
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder().build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int tid = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(tid);
        client.setTaskId(tid);
        PsuServerThread st = new PsuServerThread(server, serverSet, n, elementByteLength);
        PsuClientThread ct = new PsuClientThread(client, clientSet, n, elementByteLength);
        st.start();
        ct.start();
        st.join();
        ct.join();
        Set<ByteBuffer> expectUnion = new HashSet<>();
        for (int v : new int[] {1, 2, 3}) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES, v);
            expectUnion.add(ByteBuffer.wrap(bb.array().clone()));
        }
        PsuClientOutput out = ct.getClientOutput();
        Assert.assertEquals(3, out.getUnion().size());
        Assert.assertEquals(1, out.getPsiCa());
        assertUnionEqual(expectUnion, out.getUnion());
        server.destroy();
        client.destroy();
    }

    @Test
    public void SMALL_EC_ELLIGATOR_PSU_2p5_SHUFFLE_INDEPENDENCE() throws Exception {
        ElligatorCodec codec = ElligatorCodec.protocolInstance();
        byte[] item = new byte[SmallEcConstants.ITEM_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(item);
        byte[] k0 = EcGroupOps.randomNonZeroScalar(SECURE_RANDOM);
        byte[] k1 = EcGroupOps.randomNonZeroScalar(SECURE_RANDOM);
        byte[] v = EcGroupOps.scalarMul(codec.mapToPoint(item), k0);
        byte[][] blindedV = new byte[][] {v, v};
        byte[] shuffleSeed1 = SmallEcPermUtils.sampleSeed(SECURE_RANDOM);
        byte[] shuffleSeed2 = SmallEcPermUtils.sampleSeed(SECURE_RANDOM);
        int[] perm1 = SmallEcPermUtils.generatePermutation(2, shuffleSeed1);
        int[] perm2 = SmallEcPermUtils.generatePermutation(2, shuffleSeed2);
        byte[][] u1 = SmallEcHashDhCore.shuffleBlind(blindedV, k1, perm1);
        byte[][] u2 = SmallEcHashDhCore.shuffleBlind(blindedV, k1, perm2);
        byte[] invK0 = EcGroupOps.invertScalar(k0);
        Set<EcGroupOps.CanonicalPoint> wKeys = EcGroupOps.canonicalPointKeySet(new byte[0][]);
        List<byte[]> d1 = SmallEcHashDhCore.filterDifference(u1, invK0, wKeys);
        List<byte[]> d2 = SmallEcHashDhCore.filterDifference(u2, invK0, wKeys);
        Assert.assertEquals(d1.size(), d2.size());
    }

    private void runOnceRandomBytes(int serverSize, int clientSize, int intersectionSize) throws Exception {
        int elementByteLength = SmallEcConstants.ITEM_BYTE_LENGTH;
        ArrayList<Set<ByteBuffer>> sets = generateRandomBytesSets(
            serverSize, clientSize, intersectionSize, elementByteLength, SECURE_RANDOM
        );
        runWithSets(sets.get(0), sets.get(1), intersectionSize, elementByteLength);
    }

    private void runOnce(int serverSize, int clientSize, int intersectionSize) throws Exception {
        int elementByteLength = SmallEcConstants.ITEM_BYTE_LENGTH;
        ArrayList<Set<ByteBuffer>> sets = generateBytesSets(serverSize, clientSize, intersectionSize, elementByteLength);
        runWithSets(sets.get(0), sets.get(1), intersectionSize, elementByteLength);
    }

    private void runWithSets(
        Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, int intersectionSize, int elementByteLength
    ) throws Exception {
        SmallEcElligatorPsuConfig config = new SmallEcElligatorPsuConfig.Builder().build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int tid = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(tid);
        client.setTaskId(tid);

        PsuServerThread st = new PsuServerThread(server, serverSet, clientSet.size(), elementByteLength);
        PsuClientThread ct = new PsuClientThread(client, clientSet, serverSet.size(), elementByteLength);
        st.start();
        ct.start();
        st.join();
        ct.join();

        int n = serverSet.size();
        int expectedUnionSize = n + clientSet.size() - intersectionSize;
        int expectedRecoveredDiff = n - intersectionSize;

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        PsuClientOutput out = ct.getClientOutput();
        Assert.assertEquals(intersectionSize, out.getPsiCa());
        Assert.assertEquals(expectedUnionSize, out.getUnion().size());
        Assert.assertEquals(expectedRecoveredDiff, expectedUnionSize - clientSet.size());
        assertUnionEqual(expectUnion, out.getUnion());
        server.destroy();
        client.destroy();
    }

    private static void assertUnionEqual(Set<ByteBuffer> expected, Set<ByteBuffer> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        for (ByteBuffer e : expected) {
            Assert.assertTrue(unionContains(actual, e));
        }
    }

    private static boolean unionContains(Set<ByteBuffer> union, ByteBuffer needle) {
        byte[] target = byteArray(needle);
        for (ByteBuffer b : union) {
            if (Arrays.equals(target, byteArray(b))) {
                return true;
            }
        }
        return false;
    }

    private static Set<ByteBuffer> multisetSet(int elementByteLength, int... values) {
        List<ByteBuffer> list = new ArrayList<>(values.length);
        for (int v : values) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES, v);
            list.add(ByteBuffer.wrap(bb.array().clone()));
        }
        return new AbstractSet<ByteBuffer>() {
            @Override
            public Iterator<ByteBuffer> iterator() {
                return list.iterator();
            }

            @Override
            public int size() {
                return list.size();
            }
        };
    }

    private static byte[] byteArray(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] item = new byte[dup.remaining()];
        dup.get(item);
        return item;
    }

    private static ArrayList<Set<ByteBuffer>> generateBytesSets(
        int serverSize, int clientSize, int intersectionSize, int elementByteLength
    ) {
        Assert.assertTrue(intersectionSize <= Math.min(serverSize, clientSize));
        Set<ByteBuffer> serverSet = new HashSet<>(serverSize);
        Set<ByteBuffer> clientSet = new HashSet<>(clientSize);
        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES, i);
            byte[] v = bb.array();
            serverSet.add(ByteBuffer.wrap(v.clone()));
            clientSet.add(ByteBuffer.wrap(v.clone()));
        }
        int s = intersectionSize;
        while (serverSet.size() < serverSize) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES * 2, 1);
            bb.putInt(elementByteLength - Integer.BYTES, s++);
            serverSet.add(bb);
        }
        int c = intersectionSize;
        while (clientSet.size() < clientSize) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES * 2, 2);
            bb.putInt(elementByteLength - Integer.BYTES, c++);
            clientSet.add(bb);
        }
        ArrayList<Set<ByteBuffer>> out = new ArrayList<>(2);
        out.add(serverSet);
        out.add(clientSet);
        return out;
    }

    private static ArrayList<Set<ByteBuffer>> generateRandomBytesSets(
        int serverSize, int clientSize, int intersectionSize, int elementByteLength, SecureRandom random
    ) {
        Assert.assertTrue(intersectionSize <= Math.min(serverSize, clientSize));
        Set<ByteBuffer> serverSet = new HashSet<>(serverSize);
        Set<ByteBuffer> clientSet = new HashSet<>(clientSize);
        for (int i = 0; i < intersectionSize; i++) {
            byte[] item = new byte[elementByteLength];
            random.nextBytes(item);
            serverSet.add(ByteBuffer.wrap(item.clone()));
            clientSet.add(ByteBuffer.wrap(item.clone()));
        }
        while (serverSet.size() < serverSize) {
            byte[] item = randomItem(random, elementByteLength);
            if (!setContainsBytes(serverSet, item)) {
                serverSet.add(ByteBuffer.wrap(item));
            }
        }
        while (clientSet.size() < clientSize) {
            byte[] item = randomItem(random, elementByteLength);
            if (!setContainsBytes(serverSet, item) && !setContainsBytes(clientSet, item)) {
                clientSet.add(ByteBuffer.wrap(item));
            }
        }
        ArrayList<Set<ByteBuffer>> out = new ArrayList<>(2);
        out.add(serverSet);
        out.add(clientSet);
        return out;
    }

    private static byte[] randomItem(SecureRandom random, int elementByteLength) {
        byte[] item = new byte[elementByteLength];
        random.nextBytes(item);
        return item;
    }

    private static boolean setContainsBytes(Set<ByteBuffer> set, byte[] item) {
        for (ByteBuffer bb : set) {
            if (Arrays.equals(item, byteArray(bb))) {
                return true;
            }
        }
        return false;
    }
}
