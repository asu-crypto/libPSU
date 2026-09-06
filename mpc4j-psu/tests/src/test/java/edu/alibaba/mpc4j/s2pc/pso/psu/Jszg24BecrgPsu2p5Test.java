package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * JSZG24 bECRG PSU (Fig.17) correctness tests at 2^5 only.
 */
public class Jszg24BecrgPsu2p5Test extends AbstractTwoPartyMemoryRpcPto {
    /**
     * 2^5
     */
    private static final int N = 1 << 5;
    /**
     * item byte length (128-bit)
     */
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

    private void runOnce(int serverSize, int clientSize, int intersectionSize) throws Exception {
        // protocol config (semi-honest; symmetric-key primitives only)
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

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        PsuClientOutput out = ct.getClientOutput();
        Assert.assertNotNull(out);
        Assert.assertEquals(expectUnion.size(), out.getUnion().size());
        Assert.assertTrue(out.getUnion().containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(out.getUnion()));

        new Thread(server::destroy).start();
        new Thread(client::destroy).start();
    }

    /**
     * Deterministically generates two fixed-length byte-array sets with the requested intersection size.
     */
    private static ArrayList<Set<ByteBuffer>> generateBytesSets(int serverSize, int clientSize, int intersectionSize,
                                                                int elementByteLength) {
        Assert.assertTrue(serverSize >= 1);
        Assert.assertTrue(clientSize >= 1);
        Assert.assertTrue(intersectionSize >= 0);
        Assert.assertTrue(intersectionSize <= Math.min(serverSize, clientSize));
        Set<ByteBuffer> serverSet = new HashSet<>(serverSize);
        Set<ByteBuffer> clientSet = new HashSet<>(clientSize);
        // shared items: [0, 0, 0, i]
        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES, i);
            byte[] v = bb.array();
            serverSet.add(ByteBuffer.wrap(v.clone()));
            clientSet.add(ByteBuffer.wrap(v.clone()));
        }
        // server-only items: [0, 0, 1, i]
        int s = intersectionSize;
        while (serverSet.size() < serverSize) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES * 2, 1);
            bb.putInt(elementByteLength - Integer.BYTES, s++);
            serverSet.add(bb);
        }
        // client-only items: [0, 0, 2, i]
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

