package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuServer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
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
        runTwoExecutionsAfterOneInit(first, second, false, false, "HaoWan2026-two-exec");
    }

    @Test
    public void sharedZero_directCot() throws Exception {
        runCustomOnce(sharedZeroSets(), false, false, false);
    }

    @Test
    public void sharedZero_silentCot() throws Exception {
        runCustomOnce(sharedZeroSets(), true, false, false);
    }

    @Test
    public void sharedZero_parallel() throws Exception {
        runCustomOnce(sharedZeroSets(), false, true, false);
    }

    @Test
    public void sharedZero_twoExecutionsAfterOneInit() throws Exception {
        runTwoExecutionsAfterOneInit(
            sharedZeroSets(), sharedZeroSets(), false, false, "HaoWan2026-shared-zero-two-exec"
        );
    }

    @Test
    public void serverOnlyZero_directCot() throws Exception {
        runCustomOnce(serverOnlyZeroSets(), false, false, false);
    }

    @Test
    public void clientOnlyZero_directCot() throws Exception {
        runCustomOnce(clientOnlyZeroSets(), false, false, false);
    }

    @Test
    public void equalWithZero_directCot() throws Exception {
        runCustomOnce(equalWithZeroSets(), false, false, false);
    }

    private void runOnce(
        int serverSize, int clientSize, int intersectionSize,
        boolean silent, boolean parallel, boolean viaFactory
    ) throws Exception {
        DeterministicPsuSets.SetsOf sets =
            DeterministicPsuSets.build(serverSize, clientSize, intersectionSize, ELEMENT_LEN);
        runCustomOnce(sets, silent, parallel, viaFactory);
    }

    private void runCustomOnce(
        DeterministicPsuSets.SetsOf sets,
        boolean silent, boolean parallel, boolean viaFactory
    ) throws Exception {
        HaoWan2026PsuConfig config = new HaoWan2026PsuConfig.Builder(silent).build();
        Assert.assertEquals(
            edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN,
            config.getSsPmtFastConfig().getPublicParamsType()
        );

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

    private void runTwoExecutionsAfterOneInit(
        DeterministicPsuSets.SetsOf first,
        DeterministicPsuSets.SetsOf second,
        boolean silent,
        boolean parallel,
        String label
    ) throws Exception {
        int maxServer = Math.max(first.serverSet.size(), second.serverSet.size());
        int maxClient = Math.max(first.clientSet.size(), second.clientSet.size());

        HaoWan2026PsuConfig config = new HaoWan2026PsuConfig.Builder(silent).build();
        Assert.assertEquals(
            edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN,
            config.getSsPmtFastConfig().getPublicParamsType()
        );
        PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
        PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
        server.setParallel(parallel);
        client.setParallel(parallel);
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
            label
        );

        assertClientOutput(first, clientOut1.get());
        assertClientOutput(second, clientOut2.get());
        printAndResetRpc(0);
    }

    /** server {0,a,b}, client {0,c} → union {0,a,b,c}, psiCa=1 */
    private static DeterministicPsuSets.SetsOf sharedZeroSets() {
        ByteBuffer zero = DeterministicPsuSets.zeroBlock();
        ByteBuffer a = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SERVER_ONLY, 0, ELEMENT_LEN);
        ByteBuffer b = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SERVER_ONLY, 1, ELEMENT_LEN);
        ByteBuffer c = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_CLIENT_ONLY, 0, ELEMENT_LEN);
        Set<ByteBuffer> server = setOf(zero, a, b);
        Set<ByteBuffer> client = setOf(zero, c);
        Set<ByteBuffer> union = setOf(zero, a, b, c);
        return new DeterministicPsuSets.SetsOf(server, client, union, 1, ELEMENT_LEN);
    }

    /** server {0,a}, client {b,c} → union {0,a,b,c}, psiCa=0 */
    private static DeterministicPsuSets.SetsOf serverOnlyZeroSets() {
        ByteBuffer zero = DeterministicPsuSets.zeroBlock();
        ByteBuffer a = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SERVER_ONLY, 0, ELEMENT_LEN);
        ByteBuffer b = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_CLIENT_ONLY, 0, ELEMENT_LEN);
        ByteBuffer c = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_CLIENT_ONLY, 1, ELEMENT_LEN);
        Set<ByteBuffer> server = setOf(zero, a);
        Set<ByteBuffer> client = setOf(b, c);
        Set<ByteBuffer> union = setOf(zero, a, b, c);
        return new DeterministicPsuSets.SetsOf(server, client, union, 0, ELEMENT_LEN);
    }

    /** server {a,b}, client {0,c} → union {0,a,b,c}, psiCa=0 */
    private static DeterministicPsuSets.SetsOf clientOnlyZeroSets() {
        ByteBuffer zero = DeterministicPsuSets.zeroBlock();
        ByteBuffer a = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SERVER_ONLY, 0, ELEMENT_LEN);
        ByteBuffer b = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SERVER_ONLY, 1, ELEMENT_LEN);
        ByteBuffer c = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_CLIENT_ONLY, 0, ELEMENT_LEN);
        Set<ByteBuffer> server = setOf(a, b);
        Set<ByteBuffer> client = setOf(zero, c);
        Set<ByteBuffer> union = setOf(zero, a, b, c);
        return new DeterministicPsuSets.SetsOf(server, client, union, 0, ELEMENT_LEN);
    }

    /** both {0,a,b} → same union, psiCa=3 */
    private static DeterministicPsuSets.SetsOf equalWithZeroSets() {
        ByteBuffer zero = DeterministicPsuSets.zeroBlock();
        ByteBuffer a = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SHARED, 0, ELEMENT_LEN);
        ByteBuffer b = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SHARED, 1, ELEMENT_LEN);
        Set<ByteBuffer> both = setOf(zero, a, b);
        return new DeterministicPsuSets.SetsOf(both, new HashSet<>(both), new HashSet<>(both), 3, ELEMENT_LEN);
    }

    private static Set<ByteBuffer> setOf(ByteBuffer... elements) {
        Set<ByteBuffer> set = new HashSet<>(elements.length);
        for (ByteBuffer e : elements) {
            set.add(ByteBuffer.wrap(DeterministicPsuSets.toBytes(e).clone()));
        }
        return set;
    }

    private static void assertClientOutput(DeterministicPsuSets.SetsOf sets, PsuClientOutput out) {
        Assert.assertNotNull(out);
        DeterministicPsuSets.assertUnionEqual(sets.expectedUnion, out.getUnion(), ELEMENT_LEN);
        Assert.assertEquals(sets.intersectionSize, out.getPsiCa());
        Assert.assertEquals(sets.expectedUnion.size(), out.getUnion().size());
    }
}
