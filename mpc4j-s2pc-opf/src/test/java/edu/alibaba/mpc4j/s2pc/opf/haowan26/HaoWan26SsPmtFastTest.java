package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.bitvector.BitVector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ssPMT-fast membership reconstruction against plaintext set membership.
 */
public class HaoWan26SsPmtFastTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int SERVER_N = 16;
    private static final int CLIENT_N = 12;
    private static final int ELEMENT_BYTE_LENGTH = 16;

    public HaoWan26SsPmtFastTest() {
        super("HAO_WAN26_SS_PMT_FAST");
    }

    @Test(timeout = 300_000)
    public void testPartialIntersectionMembership() throws InterruptedException {
        HaoWan26SsPmtFastConfig serverConfig = HaoWan26SsPmtFastConfig.createDefault(
            edu.alibaba.mpc4j.common.rpc.desc.SecurityModel.SEMI_HONEST, false
        );
        HaoWan26SsPmtFastConfig clientConfig = HaoWan26SsPmtFastConfig.createDefault(
            edu.alibaba.mpc4j.common.rpc.desc.SecurityModel.SEMI_HONEST, false
        );
        HaoWan26SsPmtFastServer server = new HaoWan26SsPmtFastServer(firstRpc, secondRpc.ownParty(), serverConfig);
        HaoWan26SsPmtFastClient client = new HaoWan26SsPmtFastClient(secondRpc, firstRpc.ownParty(), clientConfig);

        byte[][] clientElements = randomDistinct(CLIENT_N);
        byte[][] serverElements = new byte[SERVER_N][];
        // First half of server set intersects client; second half is disjoint.
        int intersect = CLIENT_N / 2;
        for (int i = 0; i < SERVER_N; i++) {
            if (i < intersect) {
                serverElements[i] = Arrays.copyOf(clientElements[i], ELEMENT_BYTE_LENGTH);
            } else {
                serverElements[i] = randomDistinct(1)[0];
            }
        }
        Set<ByteBufferKey> clientSet = toSet(clientElements);
        boolean[] expect = new boolean[SERVER_N];
        for (int i = 0; i < SERVER_N; i++) {
            expect[i] = clientSet.contains(new ByteBufferKey(serverElements[i]));
        }

        AtomicReference<SquareZ2Vector> share0 = new AtomicReference<>();
        AtomicReference<SquareZ2Vector> share1 = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread serverThread = new Thread(() -> {
            try {
                server.init(SERVER_N, CLIENT_N);
                share0.set(server.execute(serverElements, CLIENT_N));
            } catch (Throwable t) {
                err.set(t);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                client.init(CLIENT_N, SERVER_N);
                share1.set(client.execute(clientElements, SERVER_N));
            } catch (Throwable t) {
                err.set(t);
            }
        });
        serverThread.start();
        clientThread.start();
        serverThread.join();
        clientThread.join();
        if (err.get() != null) {
            throw new AssertionError(err.get());
        }

        BitVector b0 = share0.get().getBitVector();
        BitVector b1 = share1.get().getBitVector();
        Assert.assertEquals(SERVER_N, b0.bitNum());
        Assert.assertEquals(SERVER_N, b1.bitNum());
        for (int i = 0; i < SERVER_N; i++) {
            Assert.assertEquals("index " + i, expect[i], b0.get(i) ^ b1.get(i));
        }
    }

    private byte[][] randomDistinct(int n) {
        Set<ByteBufferKey> seen = new HashSet<>();
        byte[][] out = new byte[n][];
        int i = 0;
        while (i < n) {
            byte[] e = new byte[ELEMENT_BYTE_LENGTH];
            SECURE_RANDOM.nextBytes(e);
            ByteBufferKey key = new ByteBufferKey(e);
            if (seen.add(key)) {
                out[i++] = e;
            }
        }
        return out;
    }

    private static Set<ByteBufferKey> toSet(byte[][] elements) {
        Set<ByteBufferKey> set = new HashSet<>();
        for (byte[] e : elements) {
            set.add(new ByteBufferKey(e));
        }
        return set;
    }

    private static final class ByteBufferKey {
        private final byte[] bytes;

        ByteBufferKey(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ByteBufferKey other && Arrays.equals(bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }
}
