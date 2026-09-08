package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuServer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Deterministic small-set matrix for USENIX:HaoWan26 one-sided PSU (direct / silent COT).
 */
public class HaoWan2026SmallPsuMatrixTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int ELEMENT_LEN = CommonConstants.BLOCK_BYTE_LENGTH;
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    public HaoWan2026SmallPsuMatrixTest() {
        super("HAOWAN2026_SMALL_MATRIX");
    }

    @Test
    public void minimumDisjoint_2_2_0() throws Exception {
        runOnce(2, 2, 0, false, false, false);
    }

    @Test
    public void balancedPartial_4_4_2() throws Exception {
        runOnce(4, 4, 2, false, false, false);
    }

    @Test
    public void balancedFull_4_4_4() throws Exception {
        runOnce(4, 4, 4, false, false, false);
    }

    @Test
    public void serverLarger_7_3_1() throws Exception {
        runOnce(7, 3, 1, false, false, false);
    }

    @Test
    public void clientLarger_3_7_1() throws Exception {
        runOnce(3, 7, 1, false, false, false);
    }

    @Test
    public void clientSubset_7_3_3() throws Exception {
        runOnce(7, 3, 3, false, false, false);
    }

    @Test
    public void serverSubset_3_7_3() throws Exception {
        runOnce(3, 7, 3, false, false, false);
    }

    @Test
    public void silentCotSmoke_4_4_2() throws Exception {
        runOnce(4, 4, 2, true, false, false);
    }

    @Test
    public void parallel_4_4_2() throws Exception {
        runOnce(4, 4, 2, false, true, false);
    }

    @Test
    public void factoryRouting_4_4_2() throws Exception {
        runOnce(4, 4, 2, false, false, true);
    }

    @Test
    public void twoExecutionsAfterOneInit() throws Exception {
        DeterministicPsuSets.SetsOf first = DeterministicPsuSets.build(4, 4, 2, ELEMENT_LEN);
        DeterministicPsuSets.SetsOf second = DeterministicPsuSets.build(2, 2, 0, ELEMENT_LEN);
        int maxServer = Math.max(first.serverSet.size(), second.serverSet.size());
        int maxClient = Math.max(first.clientSet.size(), second.clientSet.size());

        HaoWan2026PsuConfig config = new HaoWan2026PsuConfig.Builder(false).build();
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        AtomicReference<PsuClientOutput> clientOut1 = new AtomicReference<>();
        AtomicReference<PsuClientOutput> clientOut2 = new AtomicReference<>();
        AtomicReference<Throwable> serverErr = new AtomicReference<>();
        AtomicReference<Throwable> clientErr = new AtomicReference<>();

        Thread st = new Thread(() -> {
            try {
                server.init(maxServer, maxClient);
                server.getRpc().synchronize();
                server.psu(first.serverSet, first.clientSet.size(), ELEMENT_LEN);
                server.getRpc().synchronize();
                server.psu(second.serverSet, second.clientSet.size(), ELEMENT_LEN);
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
            "HaoWan2026-two-exec"
        );

        assertClientOutput(first, clientOut1.get());
        assertClientOutput(second, clientOut2.get());
        printAndResetRpc(0);
    }

    private void runOnce(
        int serverSize, int clientSize, int intersectionSize,
        boolean silent, boolean parallel, boolean viaFactory
    ) throws Exception {
        DeterministicPsuSets.SetsOf sets =
            DeterministicPsuSets.build(serverSize, clientSize, intersectionSize, ELEMENT_LEN);
        HaoWan2026PsuConfig config = new HaoWan2026PsuConfig.Builder(silent).build();

        PsuServer server;
        PsuClient client;
        if (viaFactory) {
            server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
            client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        } else {
            server = new HaoWan2026PsuServer(firstRpc, secondRpc.ownParty(), config);
            client = new HaoWan2026PsuClient(secondRpc, firstRpc.ownParty(), config);
        }
        server.setParallel(parallel);
        client.setParallel(parallel);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(taskId);
        client.setTaskId(taskId);

        AtomicReference<PsuClientOutput> clientOut = new AtomicReference<>();
        AtomicReference<Throwable> serverErr = new AtomicReference<>();
        AtomicReference<Throwable> clientErr = new AtomicReference<>();

        Thread st = new Thread(() -> {
            try {
                server.init(sets.serverSet.size(), sets.clientSet.size());
                server.psu(sets.serverSet, sets.clientSet.size(), ELEMENT_LEN);
            } catch (Throwable t) {
                serverErr.set(t);
            }
        });
        Thread ct = new Thread(() -> {
            try {
                client.init(sets.clientSet.size(), sets.serverSet.size());
                clientOut.set(client.psu(sets.clientSet, sets.serverSet.size(), ELEMENT_LEN));
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
            "HaoWan2026"
        );

        assertClientOutput(sets, clientOut.get());
        printAndResetRpc(0);
    }

    private static void assertClientOutput(DeterministicPsuSets.SetsOf sets, PsuClientOutput out) {
        Assert.assertNotNull(out);
        DeterministicPsuSets.assertUnionEqual(sets.expectedUnion, out.getUnion(), ELEMENT_LEN);
        Assert.assertEquals(sets.intersectionSize, out.getPsiCa());
        Assert.assertEquals(sets.expectedUnion.size(), out.getUnion().size());
    }
}
