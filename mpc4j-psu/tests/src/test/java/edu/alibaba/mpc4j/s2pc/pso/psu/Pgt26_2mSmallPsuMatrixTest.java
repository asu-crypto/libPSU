package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic small-set matrix for EUROCRYPT:PuGaoTri26 (PGT26-2M) two-sided PSU (16-byte only).
 */
public class Pgt26_2mSmallPsuMatrixTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int ELEMENT_LEN = Pgt26Constants.ITEM_BYTE_LENGTH;
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    public Pgt26_2mSmallPsuMatrixTest() {
        super("PGT26_2M_SMALL_MATRIX");
    }

    @Test
    public void minimumDisjoint_2_2_0() throws Exception {
        runOnce(2, 2, 0, false);
    }

    @Test
    public void balancedPartial_4_4_2() throws Exception {
        runOnce(4, 4, 2, false);
    }

    @Test
    public void balancedFull_4_4_4() throws Exception {
        runOnce(4, 4, 4, false);
    }

    @Test
    public void serverLarger_7_3_1() throws Exception {
        runOnce(7, 3, 1, false);
    }

    @Test
    public void clientLarger_3_7_1() throws Exception {
        runOnce(3, 7, 1, false);
    }

    @Test
    public void clientSubset_7_3_3() throws Exception {
        runOnce(7, 3, 3, false);
    }

    @Test
    public void serverSubset_3_7_3() throws Exception {
        runOnce(3, 7, 3, false);
    }

    @Test
    public void parallel_4_4_2() throws Exception {
        runOnce(4, 4, 2, true);
    }

    @Test
    public void factoryRouting_4_4_2() throws Exception {
        runOnce(4, 4, 2, false);
    }

    @Test
    public void twoExecutionsAfterOneInit() throws Exception {
        DeterministicPsuSets.SetsOf first = DeterministicPsuSets.build(4, 4, 2, ELEMENT_LEN);
        DeterministicPsuSets.SetsOf second = DeterministicPsuSets.build(2, 2, 0, ELEMENT_LEN);
        int maxServer = Math.max(first.serverSet.size(), second.serverSet.size());
        int maxClient = Math.max(first.clientSet.size(), second.clientSet.size());

        Pgt26_2mPsuConfig config = new Pgt26_2mPsuConfig.Builder().build();
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
                serverOut1.set(server.psu(first.serverSet, first.clientSet.size(), ELEMENT_LEN));
                server.getRpc().synchronize();
                serverOut2.set(server.psu(second.serverSet, second.clientSet.size(), ELEMENT_LEN));
            } catch (Throwable t) {
                serverErr.set(t);
            }
        });
        Thread ct = new Thread(() -> {
            try {
                client.init(maxClient, maxServer);
                client.getRpc().synchronize();
                clientOut1.set(client.psu(first.clientSet, first.serverSet.size(), ELEMENT_LEN));
                client.getRpc().synchronize();
                clientOut2.set(client.psu(second.clientSet, second.serverSet.size(), ELEMENT_LEN));
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
            "PGT26-2M-two-exec"
        );

        DeterministicPsuSets.assertUnionEqual(first.expectedUnion, serverOut1.get().getUnion(), ELEMENT_LEN);
        DeterministicPsuSets.assertUnionEqual(first.expectedUnion, clientOut1.get().getUnion(), ELEMENT_LEN);
        DeterministicPsuSets.assertUnionEqual(second.expectedUnion, serverOut2.get().getUnion(), ELEMENT_LEN);
        DeterministicPsuSets.assertUnionEqual(second.expectedUnion, clientOut2.get().getUnion(), ELEMENT_LEN);
        printAndResetRpc(0);
    }

    private void runOnce(int serverSize, int clientSize, int intersectionSize, boolean parallel) throws Exception {
        DeterministicPsuSets.SetsOf sets =
            DeterministicPsuSets.build(serverSize, clientSize, intersectionSize, ELEMENT_LEN);
        Pgt26_2mPsuConfig config = new Pgt26_2mPsuConfig.Builder().build();
        PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        TwoSidedWorker st = new TwoSidedWorker(true, server, client, sets.serverSet, sets.clientSet);
        TwoSidedWorker ct = new TwoSidedWorker(false, server, client, sets.serverSet, sets.clientSet);
        st.start();
        ct.start();
        TwoPartyTestJoin.joinFailFast(
            st, st::getFailure, server::destroy,
            ct, ct::getFailure, client::destroy,
            TIMEOUT_MS,
            "PGT26-2M"
        );

        DeterministicPsuSets.assertUnionEqual(sets.expectedUnion, st.getOutput().getUnion(), ELEMENT_LEN);
        DeterministicPsuSets.assertUnionEqual(sets.expectedUnion, ct.getOutput().getUnion(), ELEMENT_LEN);
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
        private PsuTwoSidedOutput output;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        TwoSidedWorker(
            boolean serverSide, PsuTwoSidedServer server, PsuTwoSidedClient client,
            Set<ByteBuffer> serverSet, Set<ByteBuffer> clientSet
        ) {
            this.serverSide = serverSide;
            this.server = server;
            this.client = client;
            this.serverSet = serverSet;
            this.clientSet = clientSet;
        }

        @Override
        public void run() {
            try {
                if (serverSide) {
                    server.init(serverSet.size(), clientSet.size());
                    server.getRpc().synchronize();
                    output = server.psu(serverSet, clientSet.size(), ELEMENT_LEN);
                } else {
                    client.init(clientSet.size(), serverSet.size());
                    client.getRpc().synchronize();
                    output = client.psu(clientSet, serverSet.size(), ELEMENT_LEN);
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
