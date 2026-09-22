package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic small-set matrix for EUROCRYPT:PisTri26 (PT26) two-sided PSU.
 */
public class Pt26SmallPsuMatrixTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int ELEMENT_16 = CommonConstants.BLOCK_BYTE_LENGTH;
    private static final int ELEMENT_8 = Long.BYTES;
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);

    public Pt26SmallPsuMatrixTest() {
        super("PT26_SMALL_MATRIX");
    }

    @Test
    public void minimumDisjoint_2_2_0() throws Exception {
        runOnce(2, 2, 0, ELEMENT_16, false, true);
    }

    @Test
    public void balancedPartial_4_4_2() throws Exception {
        runOnce(4, 4, 2, ELEMENT_16, false, true);
    }

    @Test
    public void balancedFull_4_4_4() throws Exception {
        runOnce(4, 4, 4, ELEMENT_16, false, true);
    }

    @Test
    public void serverLarger_7_3_1() throws Exception {
        runOnce(7, 3, 1, ELEMENT_16, false, true);
    }

    @Test
    public void clientLarger_3_7_1() throws Exception {
        runOnce(3, 7, 1, ELEMENT_16, false, true);
    }

    @Test
    public void clientSubset_7_3_3() throws Exception {
        runOnce(7, 3, 3, ELEMENT_16, false, true);
    }

    @Test
    public void serverSubset_3_7_3() throws Exception {
        runOnce(3, 7, 3, ELEMENT_16, false, true);
    }

    @Test
    public void parallel_4_4_2() throws Exception {
        runOnce(4, 4, 2, ELEMENT_16, true, true);
    }

    @Test
    public void element8Bytes_4_4_2() throws Exception {
        runOnce(4, 4, 2, ELEMENT_8, false, true);
    }

    @Test
    public void factoryRouting_4_4_2() throws Exception {
        // Explicit PsuFactory.createTwoSided* path (same as other cases; named for coverage).
        runOnce(4, 4, 2, ELEMENT_16, false, true);
    }

    @Test
    public void twoExecutionsAfterOneInit() throws Exception {
        DeterministicPsuSets.SetsOf first = DeterministicPsuSets.build(4, 4, 2, ELEMENT_16);
        DeterministicPsuSets.SetsOf second = DeterministicPsuSets.build(2, 2, 0, ELEMENT_16);
        int maxServer = Math.max(first.serverSet.size(), second.serverSet.size());
        int maxClient = Math.max(first.clientSet.size(), second.clientSet.size());

        Pt26PsuConfig config = new Pt26PsuConfig.Builder().build();
        PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        AtomicReference<PsuTwoSidedOutput> serverOut1 = new AtomicReference<>();
        AtomicReference<PsuTwoSidedOutput> serverOut2 = new AtomicReference<>();
        AtomicReference<PsuTwoSidedOutput> clientOut1 = new AtomicReference<>();
        AtomicReference<PsuTwoSidedOutput> clientOut2 = new AtomicReference<>();
        AtomicReference<Throwable> serverErr = new AtomicReference<>();
        AtomicReference<Throwable> clientErr = new AtomicReference<>();

        Thread st = new Thread(() -> {
            try {
                server.init(maxServer, maxClient);
                server.getRpc().synchronize();
                serverOut1.set(server.psu(first.serverSet, first.clientSet.size(), ELEMENT_16));
                server.getRpc().synchronize();
                serverOut2.set(server.psu(second.serverSet, second.clientSet.size(), ELEMENT_16));
            } catch (Throwable t) {
                serverErr.set(t);
            }
        });
        Thread ct = new Thread(() -> {
            try {
                client.init(maxClient, maxServer);
                client.getRpc().synchronize();
                clientOut1.set(client.psu(first.clientSet, first.serverSet.size(), ELEMENT_16));
                client.getRpc().synchronize();
                clientOut2.set(client.psu(second.clientSet, second.serverSet.size(), ELEMENT_16));
            } catch (Throwable t) {
                clientErr.set(t);
            }
        });
        st.start();
        ct.start();
        TwoPartyTestJoin.joinFailFast(
            st, serverErr::get, server::destroy,
            ct, clientErr::get, client::destroy,
            TIMEOUT_MS,
            "PT26-two-exec"
        );

        DeterministicPsuSets.assertUnionEqual(first.expectedUnion, serverOut1.get().getUnion(), ELEMENT_16);
        DeterministicPsuSets.assertUnionEqual(first.expectedUnion, clientOut1.get().getUnion(), ELEMENT_16);
        DeterministicPsuSets.assertUnionEqual(second.expectedUnion, serverOut2.get().getUnion(), ELEMENT_16);
        DeterministicPsuSets.assertUnionEqual(second.expectedUnion, clientOut2.get().getUnion(), ELEMENT_16);
        printAndResetRpc(0);
    }

    private void runOnce(
        int serverSize, int clientSize, int intersectionSize, int elementByteLength,
        boolean parallel, boolean viaFactory
    ) throws Exception {
        Assert.assertTrue(viaFactory);
        DeterministicPsuSets.SetsOf sets =
            DeterministicPsuSets.build(serverSize, clientSize, intersectionSize, elementByteLength);
        Pt26PsuConfig config = new Pt26PsuConfig.Builder().build();
        PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        TwoSidedWorker st = new TwoSidedWorker(true, server, client, sets.serverSet, sets.clientSet, elementByteLength);
        TwoSidedWorker ct = new TwoSidedWorker(false, server, client, sets.serverSet, sets.clientSet, elementByteLength);
        st.start();
        ct.start();
        TwoPartyTestJoin.joinFailFast(
            st, st::getFailure, server::destroy,
            ct, ct::getFailure, client::destroy,
            TIMEOUT_MS,
            "PT26"
        );

        DeterministicPsuSets.assertUnionEqual(sets.expectedUnion, st.getOutput().getUnion(), elementByteLength);
        DeterministicPsuSets.assertUnionEqual(sets.expectedUnion, ct.getOutput().getUnion(), elementByteLength);
        Assert.assertEquals(sets.expectedUnion.size(), st.getOutput().getUnion().size());
        Assert.assertEquals(sets.expectedUnion.size(), ct.getOutput().getUnion().size());
        printAndResetRpc(0);
    }

    private static final class TwoSidedWorker extends Thread {
        private final boolean serverSide;
        private final PsuTwoSidedServer server;
        private final PsuTwoSidedClient client;
        private final Set<ByteBuffer> serverSet;
        private final Set<ByteBuffer> clientSet;
        private final int elementByteLength;
        private PsuTwoSidedOutput output;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        TwoSidedWorker(
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
                    server.init(serverSet.size(), clientSet.size());
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
