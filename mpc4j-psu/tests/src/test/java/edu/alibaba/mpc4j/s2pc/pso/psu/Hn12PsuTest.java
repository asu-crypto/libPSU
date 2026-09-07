package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
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
    public void debugModeExactUnionAndPsiCa() throws Exception {
        runOnce(10, 8, 4);
        runOnce(8, 8, 0);
        runOnce(8, 8, 8);
    }

    private void runOnce(int serverSize, int clientSize, int intersectionSize) throws Exception {
        Hn12PsuConfig config = new Hn12PsuConfig.Builder().build();
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
        TwoPartyTestJoin.joinFailFast(
            st, st::getFailure, server::destroy,
            ct, ct::getFailure, client::destroy,
            "HN12"
        );

        Set<ByteBuffer> expectUnion = new HashSet<>(serverSet);
        expectUnion.addAll(clientSet);
        PsuClientOutput out = ct.getClientOutput();
        Assert.assertNotNull(out);
        Assert.assertEquals(expectUnion, out.getUnion());
        Assert.assertEquals(intersectionSize, out.getPsiCa());
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
}
