package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26IbltParams;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Two-sided EUROCRYPT_PisTri26 correctness and factory routing tests.
 */
public class Pt26PsuTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(Pt26PsuTest.class);
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);

    public Pt26PsuTest() {
        super("PT26_PSU");
    }

    @Test
    public void testTinyTwoSided() throws Exception {
        runCase(4, 4, Long.BYTES);
    }

    @Test
    public void testSmallTwoSided() throws Exception {
        runCase(10, 12, CommonConstants.BLOCK_BYTE_LENGTH);
    }

    @Test
    public void testAsymmetricSizes() throws Exception {
        runCase(4, 16, Long.BYTES);
        runCase(16, 4, Long.BYTES);
    }

    @Test
    public void testCascadeRequiresCorrectOtDirection() throws Exception {
        // Distinct server-only and client-only elements force multi-round peel when shared
        // bins become peelable only after a client-only deletion. Reversed Step-1 OT messages
        // leave residual IBLT state / incomplete union (now aborted via residual check).
        runCase(6, 6, Long.BYTES);
    }

    @Test
    public void oneSidedFactoryRejectsPt26() {
        Pt26PsuConfig config = new Pt26PsuConfig.Builder().build();
        try {
            PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
            Assert.fail("expected rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("two-sided"));
        }
        try {
            PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
            Assert.fail("expected rejection");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("two-sided"));
        }
    }

    @Test
    public void testTable1ExpansionScheduleForTau() {
        Assert.assertEquals(4.5, Pt26IbltParams.chooseExpansionForTau((1 << 16) - 1), 0.000001);
        Assert.assertEquals(3.5, Pt26IbltParams.chooseExpansionForTau(1 << 16), 0.000001);
        Assert.assertEquals(2.0, Pt26IbltParams.chooseExpansionForTau(1 << 18), 0.000001);
        Assert.assertEquals(1.5, Pt26IbltParams.chooseExpansionForTau(1 << 20), 0.000001);
        Assert.assertEquals(4.5, Pt26IbltParams.chooseExpansion(1 << 14), 0.000001);
        Assert.assertEquals(3.5, Pt26IbltParams.chooseExpansion(1 << 16), 0.000001);
    }

    private void runCase(int serverSize, int clientSize, int elementByteLength) throws Exception {
        Pt26PsuConfig config = new Pt26PsuConfig.Builder().build();
        PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(secondRpc, firstRpc.ownParty(), config);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(randomTaskId);
        client.setTaskId(randomTaskId);

        LOGGER.info("EUROCRYPT_PisTri26 two-sided server_size={}, client_size={}", serverSize, clientSize);
        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(serverSize, clientSize, elementByteLength);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = sets.get(1);
        Set<ByteBuffer> expect = new HashSet<>(serverSet);
        expect.addAll(clientSet);

        TwoSidedThread st = new TwoSidedThread(true, server, client, serverSet, clientSet, elementByteLength);
        TwoSidedThread ct = new TwoSidedThread(false, server, client, serverSet, clientSet, elementByteLength);
        st.start();
        ct.start();
        TwoPartyTestJoin.joinFailFast(
            st, st::getFailure, server::destroy,
            ct, ct::getFailure, client::destroy,
            TIMEOUT_MS,
            "PT26"
        );
        assertUnionEqual(expect, st.getOutput().getUnion());
        assertUnionEqual(expect, ct.getOutput().getUnion());
        printAndResetRpc(0);
    }

    private static void assertUnionEqual(Set<ByteBuffer> expected, Set<ByteBuffer> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        Assert.assertTrue(actual.containsAll(expected));
        Assert.assertTrue(expected.containsAll(actual));
    }

    private static final class TwoSidedThread extends Thread {
        private final boolean serverSide;
        private final PsuTwoSidedServer server;
        private final PsuTwoSidedClient client;
        private final Set<ByteBuffer> serverSet;
        private final Set<ByteBuffer> clientSet;
        private final int elementByteLength;
        private PsuTwoSidedOutput output;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        TwoSidedThread(
            boolean serverSide, PsuTwoSidedServer server, PsuTwoSidedClient client,
            Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet, int elementByteLength
        ) {
            this.serverSide = serverSide;
            this.server = server;
            this.client = client;
            this.serverSet = serverSet;
            this.clientSet = clientSet;
            this.elementByteLength = elementByteLength;
        }

        @Override
        public void run() {
            try {
                if (serverSide) {
                    server.init(clientSet.size(), serverSet.size());
                    server.getRpc().synchronize();
                    output = server.psu(serverSet, clientSet.size(), elementByteLength);
                } else {
                    client.init(clientSet.size(), serverSet.size());
                    client.getRpc().synchronize();
                    output = client.psu(clientSet, serverSet.size(), elementByteLength);
                }
            } catch (Throwable e) {
                failure.set(e);
            }
        }

        Throwable getFailure() {
            return failure.get();
        }

        PsuTwoSidedOutput getOutput() {
            return output;
        }
    }
}
