package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.prf.Prf;
import edu.alibaba.mpc4j.common.tool.crypto.prf.PrfFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * JSZG24 bECRG PSU (Fig.17) correctness tests at 2^5 with exact union and PSI-CA.
 */
public class Jszg24BecrgPsu2p5Test extends AbstractTwoPartyMemoryRpcPto {
    private static final int N = 1 << 5;
    private static final int ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;

    public Jszg24BecrgPsu2p5Test() {
        super("JSZG24_BECRG_PSU_2P5");
    }

    @Test
    public void testDisjoint() throws Exception {
        runOnce(N, N, 0);
    }

    @Test
    public void testPartialIntersection() throws Exception {
        runOnce(N, N, N / 2);
    }

    @Test
    public void testFullIntersection() throws Exception {
        runOnce(N, N, N);
    }

    @Test
    public void testUnequalSizes() throws Exception {
        runOnce(N, N / 2, N / 4);
        runOnce(N / 2, N, N / 4);
    }

    @Test
    public void testClientAndCuckooShareNegativePrfBinIndex() {
        byte[][] keys = BlockUtils.randomBlocks(3, SECURE_RANDOM);
        Prf hash = PrfFactory.createInstance(EnvType.STANDARD_JDK, Integer.BYTES);
        hash.setKey(keys[0]);
        int binNum = 1 << 8;
        byte[] item = null;
        int getIntegerBin = -1;
        int floorModBin = -1;
        for (int i = 0; i < 100_000; i++) {
            byte[] candidate = BlockUtils.randomBlock(SECURE_RANDOM);
            int raw = java.nio.ByteBuffer.wrap(hash.getBytes(candidate)).getInt();
            if (raw >= 0) {
                continue;
            }
            int candidateGetInteger = hash.getInteger(candidate, binNum);
            int candidateFloorMod = Math.floorMod(raw, binNum);
            if (candidateGetInteger == candidateFloorMod) {
                continue;
            }
            item = candidate;
            getIntegerBin = candidateGetInteger;
            floorModBin = candidateFloorMod;
            break;
        }
        Assert.assertNotNull("failed to sample negative PRF integer that diverges under floorMod", item);
        Assert.assertTrue(getIntegerBin >= 0 && getIntegerBin < binNum);
        Assert.assertNotEquals(
            "negative PRF ints must diverge between floorMod and Prf.getInteger",
            floorModBin,
            getIntegerBin
        );
        // Cuckoo and client both use Prf.getInteger; verify all three hash locations.
        for (int h = 0; h < keys.length; h++) {
            Prf prf = PrfFactory.createInstance(EnvType.STANDARD_JDK, Integer.BYTES);
            prf.setKey(keys[h]);
            int bin = prf.getInteger(item, binNum);
            Assert.assertTrue(bin >= 0 && bin < binNum);
            Assert.assertEquals(bin, Math.abs(java.nio.ByteBuffer.wrap(prf.getBytes(item)).getInt() % binNum));
        }
    }

    private void runOnce(int serverSize, int clientSize, int intersectionSize) throws Exception {
        Jszg24BecrgPsuConfig config = new Jszg24BecrgPsuConfig.Builder(false).build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int tid = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(tid);
        client.setTaskId(tid);

        ArrayList<Set<ByteBuffer>> sets = generateBytesSets(serverSize, clientSize, intersectionSize, ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = sets.get(1);

        PsuServerThread st = new PsuServerThread(server, serverSet, clientSet.size(), ELEMENT_BYTE_LENGTH);
        PsuClientThread ct = new PsuClientThread(client, clientSet, serverSet.size(), ELEMENT_BYTE_LENGTH);
        st.start();
        ct.start();
        st.join();
        ct.join();
        st.rethrowIfFailed();
        ct.rethrowIfFailed();

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        PsuClientOutput out = ct.getClientOutput();
        Assert.assertNotNull(out);
        Assert.assertEquals(expectUnion.size(), out.getUnion().size());
        Assert.assertTrue(out.getUnion().containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(out.getUnion()));
        Assert.assertEquals(intersectionSize, out.getPsiCa());

        new Thread(server::destroy).start();
        new Thread(client::destroy).start();
    }

    private static ArrayList<Set<ByteBuffer>> generateBytesSets(int serverSize, int clientSize, int intersectionSize,
                                                                int elementByteLength) {
        Assert.assertTrue(serverSize >= 1);
        Assert.assertTrue(clientSize >= 1);
        Assert.assertTrue(intersectionSize >= 0);
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
}
