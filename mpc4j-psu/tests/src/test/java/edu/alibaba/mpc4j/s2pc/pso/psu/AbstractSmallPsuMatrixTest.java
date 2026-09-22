package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.factory.ProtocolRegistry;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared deterministic small-set PSU matrix. Each protocol subclass supplies a config and
 * capability flags; every active protocol keeps its own {@code @Test} list via inheritance.
 */
public abstract class AbstractSmallPsuMatrixTest extends AbstractTwoPartyMemoryRpcPto {
    protected static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
    private final String matrixName;

    protected AbstractSmallPsuMatrixTest(String name) {
        super(name);
        this.matrixName = name;
    }

    protected abstract PsuConfig createConfig();

    /** When true, both parties must learn the same union. */
    protected abstract boolean isTwoSided();

    protected int elementByteLength() {
        return CommonConstants.BLOCK_BYTE_LENGTH;
    }

    /** Ours / equal-size-only protocols override to false. */
    protected boolean supportsAsymmetricSizes() {
        return true;
    }

    /** When true, assert PSI-CA on one-sided outputs. Many protocols return approximate CA. */
    protected boolean supportsPsiCa() {
        return false;
    }

    /** mqRPMT-based stacks still reject all-FF as ⊥ until that subsystem is migrated. */
    protected boolean supportsAllFfElement() {
        return true;
    }

    /** Curve/Elligator protocols may reject some deterministic encodings. */
    protected boolean supportsDeterministicDomainEncoding() {
        return true;
    }

    // ---- standard scenario list (every protocol subclass inherits these) ----

    @Test
    public void equal_2_2_2() throws Exception {
        runBuilt(2, 2, 2, false);
    }

    @Test
    public void disjoint_2_2_0() throws Exception {
        runBuilt(2, 2, 0, false);
    }

    @Test
    public void sizes_2_3_1() throws Exception {
        assumeAsymmetric();
        runBuilt(2, 3, 1, false);
    }

    @Test
    public void sizes_3_2_1() throws Exception {
        assumeAsymmetric();
        runBuilt(3, 2, 1, false);
    }

    @Test
    public void equal_3_3_3() throws Exception {
        runBuilt(3, 3, 3, false);
    }

    @Test
    public void equal_4_4_4() throws Exception {
        runBuilt(4, 4, 4, false);
    }

    @Test
    public void oneShared_4_4_1() throws Exception {
        runBuilt(4, 4, 1, false);
    }

    @Test
    public void multipleShared_4_4_2() throws Exception {
        runBuilt(4, 4, 2, false);
    }

    @Test
    public void serverSubsetOfClient_3_7_3() throws Exception {
        assumeAsymmetric();
        runBuilt(3, 7, 3, false);
    }

    @Test
    public void clientSubsetOfServer_7_3_3() throws Exception {
        assumeAsymmetric();
        runBuilt(7, 3, 3, false);
    }

    @Test
    public void asymmetricServerLarger_7_3_1() throws Exception {
        assumeAsymmetric();
        runBuilt(7, 3, 1, false);
    }

    @Test
    public void asymmetricClientLarger_3_7_1() throws Exception {
        assumeAsymmetric();
        runBuilt(3, 7, 1, false);
    }

    @Test
    public void withZeroElement() throws Exception {
        org.junit.Assume.assumeTrue(supportsDeterministicDomainEncoding());
        runCustom(withSpecialElement(DeterministicPsuSets.zeroBlock()), false);
    }

    @Test
    public void withAllFfElement() throws Exception {
        org.junit.Assume.assumeTrue(supportsAllFfElement());
        org.junit.Assume.assumeTrue(supportsDeterministicDomainEncoding());
        runCustom(withSpecialElement(DeterministicPsuSets.allFfBlock(elementByteLength())), false);
    }

    @Test
    public void serial_4_4_2() throws Exception {
        runBuilt(4, 4, 2, false);
    }

    @Test
    public void parallel_4_4_2() throws Exception {
        runBuilt(4, 4, 2, true);
    }

    @Test
    public void twoExecutionsAfterOneInit() throws Exception {
        DeterministicPsuSets.SetsOf first = buildSets(4, 4, 2);
        DeterministicPsuSets.SetsOf second = supportsAsymmetricSizes()
            ? buildSets(2, 2, 0)
            : buildSets(3, 3, 1);
        runTwoAfterOneInit(first, second);
    }

    // ---- helpers ----

    private void assumeAsymmetric() {
        org.junit.Assume.assumeTrue(
            "protocol requires equal set sizes",
            supportsAsymmetricSizes()
        );
    }

    protected DeterministicPsuSets.SetsOf buildSets(int serverSize, int clientSize, int intersection) {
        if (supportsDeterministicDomainEncoding()) {
            return DeterministicPsuSets.build(serverSize, clientSize, intersection, elementByteLength());
        }
        return buildIntTailSets(serverSize, clientSize, intersection, elementByteLength());
    }

    /** Compact int-in-tail encoding used by Ours / legacy small-EC tests. */
    protected static DeterministicPsuSets.SetsOf buildIntTailSets(
        int serverSize, int clientSize, int intersectionSize, int elementByteLength
    ) {
        Set<ByteBuffer> serverSet = new HashSet<>();
        Set<ByteBuffer> clientSet = new HashSet<>();
        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer bb = ByteBuffer.allocate(elementByteLength);
            bb.putInt(elementByteLength - Integer.BYTES, i);
            byte[] v = bb.array().clone();
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
        Set<ByteBuffer> union = new HashSet<>(serverSet);
        union.addAll(clientSet);
        return new DeterministicPsuSets.SetsOf(serverSet, clientSet, union, intersectionSize, elementByteLength);
    }

    protected void runBuilt(int serverSize, int clientSize, int intersection, boolean parallel)
        throws Exception {
        runCustom(buildSets(serverSize, clientSize, intersection), parallel);
    }

    protected DeterministicPsuSets.SetsOf withSpecialElement(ByteBuffer special) {
        int len = elementByteLength();
        ByteBuffer a = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_SERVER_ONLY, 0, len);
        ByteBuffer b = DeterministicPsuSets.encode(DeterministicPsuSets.DOMAIN_CLIENT_ONLY, 0, len);
        Set<ByteBuffer> server = new HashSet<>();
        Set<ByteBuffer> client = new HashSet<>();
        server.add(copy(special));
        server.add(copy(a));
        client.add(copy(special));
        client.add(copy(b));
        Set<ByteBuffer> union = new HashSet<>(server);
        union.addAll(client);
        return new DeterministicPsuSets.SetsOf(server, client, union, 1, len);
    }

    private static ByteBuffer copy(ByteBuffer src) {
        return ByteBuffer.wrap(DeterministicPsuSets.toBytes(src).clone());
    }

    protected void runCustom(DeterministicPsuSets.SetsOf sets, boolean parallel) throws Exception {
        PsuConfig config = createConfig();
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        if (isTwoSided()) {
            PsuTwoSidedServer server = ProtocolRegistry.createTwoSidedPsuServer(
                firstRpc, secondRpc.ownParty(), config
            );
            PsuTwoSidedClient client = ProtocolRegistry.createTwoSidedPsuClient(
                secondRpc, firstRpc.ownParty(), config
            );
            server.setParallel(parallel);
            client.setParallel(parallel);
            server.setTaskId(taskId);
            client.setTaskId(taskId);

            AtomicReference<PsuTwoSidedOutput> serverOut = new AtomicReference<>();
            AtomicReference<PsuTwoSidedOutput> clientOut = new AtomicReference<>();
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            AtomicReference<Throwable> clientErr = new AtomicReference<>();

            Thread st = new Thread(() -> {
                try {
                    server.init(sets.serverSet.size(), sets.clientSet.size());
                    serverOut.set(server.psu(sets.serverSet, sets.clientSet.size(), sets.elementByteLength));
                } catch (Throwable t) {
                    serverErr.set(t);
                }
            });
            Thread ct = new Thread(() -> {
                try {
                    client.init(sets.clientSet.size(), sets.serverSet.size());
                    clientOut.set(client.psu(sets.clientSet, sets.serverSet.size(), sets.elementByteLength));
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
                matrixName
            );
            Assert.assertNotNull(serverOut.get());
            Assert.assertNotNull(clientOut.get());
            DeterministicPsuSets.assertUnionEqual(
                sets.expectedUnion, serverOut.get().getUnion(), sets.elementByteLength
            );
            DeterministicPsuSets.assertUnionEqual(
                sets.expectedUnion, clientOut.get().getUnion(), sets.elementByteLength
            );
            DeterministicPsuSets.assertUnionEqual(
                clientOut.get().getUnion(), serverOut.get().getUnion(), sets.elementByteLength
            );
        } else {
            PsuServer server = ProtocolRegistry.createPsuServer(firstRpc, secondRpc.ownParty(), config);
            PsuClient client = ProtocolRegistry.createPsuClient(secondRpc, firstRpc.ownParty(), config);
            server.setParallel(parallel);
            client.setParallel(parallel);
            server.setTaskId(taskId);
            client.setTaskId(taskId);

            AtomicReference<PsuClientOutput> clientOut = new AtomicReference<>();
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            AtomicReference<Throwable> clientErr = new AtomicReference<>();

            Thread st = new Thread(() -> {
                try {
                    server.init(sets.serverSet.size(), sets.clientSet.size());
                    server.psu(sets.serverSet, sets.clientSet.size(), sets.elementByteLength);
                } catch (Throwable t) {
                    serverErr.set(t);
                }
            });
            Thread ct = new Thread(() -> {
                try {
                    client.init(sets.clientSet.size(), sets.serverSet.size());
                    clientOut.set(client.psu(sets.clientSet, sets.serverSet.size(), sets.elementByteLength));
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
                matrixName
            );
            Assert.assertNotNull(clientOut.get());
            DeterministicPsuSets.assertUnionEqual(
                sets.expectedUnion, clientOut.get().getUnion(), sets.elementByteLength
            );
            if (supportsPsiCa()) {
                Assert.assertEquals(sets.intersectionSize, clientOut.get().getPsiCa());
            }
        }
    }

    protected void runTwoAfterOneInit(DeterministicPsuSets.SetsOf first, DeterministicPsuSets.SetsOf second)
        throws Exception {
        PsuConfig config = createConfig();
        int maxServer = Math.max(first.serverSet.size(), second.serverSet.size());
        int maxClient = Math.max(first.clientSet.size(), second.clientSet.size());
        int taskId = Math.abs(SECURE_RANDOM.nextInt());

        if (isTwoSided()) {
            PsuTwoSidedServer server = ProtocolRegistry.createTwoSidedPsuServer(
                firstRpc, secondRpc.ownParty(), config
            );
            PsuTwoSidedClient client = ProtocolRegistry.createTwoSidedPsuClient(
                secondRpc, firstRpc.ownParty(), config
            );
            server.setTaskId(taskId);
            client.setTaskId(taskId);
            AtomicReference<PsuTwoSidedOutput> s1 = new AtomicReference<>();
            AtomicReference<PsuTwoSidedOutput> s2 = new AtomicReference<>();
            AtomicReference<PsuTwoSidedOutput> c1 = new AtomicReference<>();
            AtomicReference<PsuTwoSidedOutput> c2 = new AtomicReference<>();
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            AtomicReference<Throwable> clientErr = new AtomicReference<>();
            Thread st = new Thread(() -> {
                try {
                    server.init(maxServer, maxClient);
                    s1.set(server.psu(first.serverSet, first.clientSet.size(), first.elementByteLength));
                    s2.set(server.psu(second.serverSet, second.clientSet.size(), second.elementByteLength));
                } catch (Throwable t) {
                    serverErr.set(t);
                }
            });
            Thread ct = new Thread(() -> {
                try {
                    client.init(maxClient, maxServer);
                    c1.set(client.psu(first.clientSet, first.serverSet.size(), first.elementByteLength));
                    c2.set(client.psu(second.clientSet, second.serverSet.size(), second.elementByteLength));
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
                matrixName + "-two-exec"
            );
            DeterministicPsuSets.assertUnionEqual(first.expectedUnion, s1.get().getUnion(), first.elementByteLength);
            DeterministicPsuSets.assertUnionEqual(first.expectedUnion, c1.get().getUnion(), first.elementByteLength);
            DeterministicPsuSets.assertUnionEqual(second.expectedUnion, s2.get().getUnion(), second.elementByteLength);
            DeterministicPsuSets.assertUnionEqual(second.expectedUnion, c2.get().getUnion(), second.elementByteLength);
        } else {
            PsuServer server = ProtocolRegistry.createPsuServer(firstRpc, secondRpc.ownParty(), config);
            PsuClient client = ProtocolRegistry.createPsuClient(secondRpc, firstRpc.ownParty(), config);
            server.setTaskId(taskId);
            client.setTaskId(taskId);
            AtomicReference<PsuClientOutput> c1 = new AtomicReference<>();
            AtomicReference<PsuClientOutput> c2 = new AtomicReference<>();
            AtomicReference<Throwable> serverErr = new AtomicReference<>();
            AtomicReference<Throwable> clientErr = new AtomicReference<>();
            Thread st = new Thread(() -> {
                try {
                    server.init(maxServer, maxClient);
                    server.psu(first.serverSet, first.clientSet.size(), first.elementByteLength);
                    server.psu(second.serverSet, second.clientSet.size(), second.elementByteLength);
                } catch (Throwable t) {
                    serverErr.set(t);
                }
            });
            Thread ct = new Thread(() -> {
                try {
                    client.init(maxClient, maxServer);
                    c1.set(client.psu(first.clientSet, first.serverSet.size(), first.elementByteLength));
                    c2.set(client.psu(second.clientSet, second.serverSet.size(), second.elementByteLength));
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
                matrixName + "-two-exec"
            );
            DeterministicPsuSets.assertUnionEqual(first.expectedUnion, c1.get().getUnion(), first.elementByteLength);
            DeterministicPsuSets.assertUnionEqual(second.expectedUnion, c2.get().getUnion(), second.elementByteLength);
        }
    }
}
