package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * JOC:HazNis12 PSU debug-mode correctness and fail-closed malicious config.
 */
public class Hn12PsuTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;

    public Hn12PsuTest() {
        super("JOC_HazNis12_PSU");
    }

    @Test
    public void defaultConfigIsSemiHonestDebug() {
        Hn12PsuConfig config = new Hn12PsuConfig.Builder().build();
        Assert.assertTrue(config.isEnableSemiHonestDebug());
        Assert.assertTrue(config.isUseIdealPrfForTesting());
        Assert.assertEquals(SecurityModel.SEMI_HONEST, config.getSecurityModel());
        Assert.assertEquals(PsuType.JOC_HazNis12, config.getPtoType());
    }

    @Test
    public void maliciousConfigIsUnsupported() {
        Assert.assertThrows(
            UnsupportedOperationException.class,
            () -> new Hn12PsuConfig.Builder().setEnableSemiHonestDebug(false).build()
        );
    }

    @Test
    public void nonIdealPrfConfigIsUnsupported() {
        UnsupportedOperationException ex = Assert.assertThrows(
            UnsupportedOperationException.class,
            () -> new Hn12PsuConfig.Builder().setUseIdealPrfForTesting(false).build()
        );
        Assert.assertTrue(ex.getMessage().contains("ideal-PRF"));
    }

    @Test
    public void propertiesRejectNonIdealPrf() {
        java.util.Properties p = new java.util.Properties();
        p.setProperty(edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils.PSU_PTO_NAME_KEY, "JOC:HazNis12");
        p.setProperty("hn12_use_ideal_prf", "false");
        Assert.assertThrows(
            UnsupportedOperationException.class,
            () -> edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils.createConfig(p)
        );
    }

    @Test
    public void debugModeExactUnionBothParties() throws Exception {
        runOnce(10, 8, 4);
        runOnce(8, 8, 0);
        runOnce(8, 8, 8);
    }

    private void runOnce(int serverSize, int clientSize, int intersectionSize) throws Exception {
        Hn12PsuConfig config = new Hn12PsuConfig.Builder().build();
        PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(firstRpc, secondRpc.ownParty(), config);
        PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(secondRpc, firstRpc.ownParty(), config);
        int tid = Math.abs(SECURE_RANDOM.nextInt());
        server.setTaskId(tid);
        client.setTaskId(tid);

        ArrayList<Set<ByteBuffer>> sets = generateBytesSets(serverSize, clientSize, intersectionSize, ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> serverSet = sets.get(0);
        Set<ByteBuffer> clientSet = sets.get(1);
        TwoSidedServerThread st = new TwoSidedServerThread(server, serverSet, clientSet.size(), ELEMENT_BYTE_LENGTH);
        TwoSidedClientThread ct = new TwoSidedClientThread(client, clientSet, serverSet.size(), ELEMENT_BYTE_LENGTH);
        st.start();
        ct.start();
        TwoPartyTestJoin.joinFailFast(
            st, st::getFailure, server::destroy,
            ct, ct::getFailure, client::destroy,
            "HN12"
        );

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        Assert.assertEquals(expectUnion, st.getOutput().getUnion());
        Assert.assertEquals(expectUnion, ct.getOutput().getUnion());
    }

    private static ArrayList<Set<ByteBuffer>> generateBytesSets(
        int serverSize, int clientSize, int intersectionSize, int elementByteLength
    ) {
        Set<ByteBuffer> serverSet = new HashSet<>();
        Set<ByteBuffer> clientSet = new HashSet<>();
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

    private static final class TwoSidedServerThread extends Thread {
        private final PsuTwoSidedServer server;
        private final Set<ByteBuffer> set;
        private final int otherSize;
        private final int elementLen;
        private PsuTwoSidedOutput output;
        private volatile Throwable failure;

        TwoSidedServerThread(PsuTwoSidedServer server, Set<ByteBuffer> set, int otherSize, int elementLen) {
            this.server = server;
            this.set = set;
            this.otherSize = otherSize;
            this.elementLen = elementLen;
        }

        @Override
        public void run() {
            try {
                server.init(set.size(), otherSize);
                output = server.psu(set, otherSize, elementLen);
            } catch (Throwable t) {
                failure = t;
            }
        }

        PsuTwoSidedOutput getOutput() {
            return output;
        }

        Throwable getFailure() {
            return failure;
        }
    }

    private static final class TwoSidedClientThread extends Thread {
        private final PsuTwoSidedClient client;
        private final Set<ByteBuffer> set;
        private final int otherSize;
        private final int elementLen;
        private PsuTwoSidedOutput output;
        private volatile Throwable failure;

        TwoSidedClientThread(PsuTwoSidedClient client, Set<ByteBuffer> set, int otherSize, int elementLen) {
            this.client = client;
            this.set = set;
            this.otherSize = otherSize;
            this.elementLen = elementLen;
        }

        @Override
        public void run() {
            try {
                client.init(set.size(), otherSize);
                output = client.psu(set, otherSize, elementLen);
            } catch (Throwable t) {
                failure = t;
            }
        }

        PsuTwoSidedOutput getOutput() {
            return output;
        }

        Throwable getFailure() {
            return failure;
        }
    }
}
