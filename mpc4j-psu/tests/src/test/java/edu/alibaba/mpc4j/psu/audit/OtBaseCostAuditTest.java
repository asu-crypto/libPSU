package edu.alibaba.mpc4j.psu.audit;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.main.MainPtoConfigUtils;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.ks05.Ks05PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfcPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23PkePsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.main.upsu.UpsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuType;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

/**
 * Verifies default runnable configs: OT/COT-bearing protocols perform init-time wire traffic
 * (proxy for base OT + COT setup counted in {@code PsuMain} Init Send Bytes).
 */
public class OtBaseCostAuditTest extends AbstractTwoPartyMemoryRpcPto {

    public OtBaseCostAuditTest() {
        super("OT_BASE_COST_AUDIT");
    }

    private static final int MAX_SERVER = 16;
    private static final int MAX_CLIENT = 16;
    /** NP01 base OT + ALSZ13 seed OT is well above this on memory RPC. */
    private static final long MIN_OT_INIT_SEND_BYTES = 256L;

    @Test
    public void czz24ConfigUsesCoreCot() {
        OtConfigInspector.OtConfigScan scan = OtConfigInspector.scan(new Czz24CwOprfPsuConfig.Builder().build());
        Assert.assertTrue(scan.usesCoreCot);
        Assert.assertEquals(OtConfigInspector.OtKind.CORE_COT, scan.kind);
    }

    @Test
    public void dc17ConfigHasNoOt() {
        OtConfigInspector.OtConfigScan scan = OtConfigInspector.scan(new Dc17PsuConfig.Builder().build());
        Assert.assertEquals(OtConfigInspector.OtKind.NONE, scan.kind);
    }

    @Test
    public void czz24InitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.PKC_CheZhaZha24, new Czz24CwOprfPsuConfig.Builder().build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void gmr21InitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.PKC_GMRSS21, new Gmr21PsuConfig.Builder(false).build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void krtw19InitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.AC_KRTW19, new Krtw19PsuConfig.Builder().build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void zcl23PkeInitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.USENIX_ConYuWeiminDon23_PKE, new Zcl23PkePsuConfig.Builder().build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void zcl23SkeTwoPartyInitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.USENIX_ConYuWeiminDon23_SKE,
            new Zcl23SkePsuConfig.Builder(SecurityModel.SEMI_HONEST, true).build(),
            MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void tbz25RegisteredInPsuType() {
        Assert.assertEquals(PsuType.USENIX_BinYujConYanYu25, PsuType.valueOf("USENIX_BinYujConYanYu25"));
    }

    @Test
    public void tbz25InitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.USENIX_BinYujConYanYu25, new Tbz25PsuConfig.Builder(false).build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void jsz22SfcInitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.USENIX_JSZDG22, new Jsz22SfcPsuConfig.Builder(false).build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void pt26InitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.EUROCRYPT_PisTri26, new Pt26PsuConfig.Builder().build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void jszg24ConfigUsesLnot() {
        OtConfigInspector.OtConfigScan scan = OtConfigInspector.scan(new Jszg24BecrgPsuConfig.Builder(false).build());
        Assert.assertTrue(scan.usesLnot);
    }

    @Test
    public void jszg24InitSendBytesIncludeOtSetup() throws MpcAbortException {
        assertPsuInitSendBytesAtLeast(
            PsuType.USENIX_YanShiHonDaw24, new Jszg24BecrgPsuConfig.Builder(false).build(), MIN_OT_INIT_SEND_BYTES
        );
    }

    @Test
    public void dc17InitSendBytesMinimal() throws MpcAbortException {
        assertPsuInitSendBytesAtMost(PsuType.ACISP_DavCid17, new Dc17PsuConfig.Builder().build(), 64L);
    }

    @Test
    public void ks05PsiConfigHasNoOt() {
        OtConfigInspector.OtConfigScan scan = OtConfigInspector.scan(new Ks05PsiConfig.Builder().build());
        Assert.assertEquals(OtConfigInspector.OtKind.NONE, scan.kind);
    }

    @Test
    public void tcl23ConfigUsesCoreCot() {
        Properties properties = new Properties();
        properties.setProperty(UpsuConfigUtils.UPSU_PTO_NAME_KEY, UpsuType.CCS_TCLZ23.name());
        UpsuConfig config = UpsuConfigUtils.createConfig(properties);
        OtConfigInspector.OtConfigScan scan = OtConfigInspector.scan(config);
        Assert.assertTrue(scan.usesCoreCot);
    }

    @Test
    public void runnablePsuConfigsOtClassification() {
        for (PsuType type : new PsuType[]{
            PsuType.AC_KRTW19,
            PsuType.PKC_GMRSS21,
            PsuType.USENIX_ConYuWeiminDon23_PKE,
            PsuType.PKC_CheZhaZha24,
            PsuType.EUROCRYPT_PisTri26,
            PsuType.ACISP_DavCid17,
            PsuType.Ours,
        }) {
            PsuConfig config = defaultPsuConfig(type);
            OtConfigInspector.OtConfigScan scan = OtConfigInspector.scan(config);
            if (type == PsuType.ACISP_DavCid17 || type == PsuType.Ours) {
                Assert.assertEquals("Expected no OT for " + type, OtConfigInspector.OtKind.NONE, scan.kind);
            } else if (type == PsuType.USENIX_YanShiHonDaw24) {
                // handled separately
            } else {
                Assert.assertTrue("Expected OT/COT config for " + type + " but got " + scan.kind, scan.usesCoreCot);
            }
        }
    }

    private static PsuConfig defaultPsuConfig(PsuType type) {
        Properties properties = new Properties();
        properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, type.name());
        try {
            return PsuConfigUtils.createConfig(properties);
        } catch (RuntimeException e) {
            switch (type) {
                case AC_KRTW19:
                    return new Krtw19PsuConfig.Builder().build();
                case PKC_GMRSS21:
                    return new Gmr21PsuConfig.Builder(false).build();
                case USENIX_ConYuWeiminDon23_PKE:
                    return new Zcl23PkePsuConfig.Builder().build();
                case PKC_CheZhaZha24:
                    return new Czz24CwOprfPsuConfig.Builder().build();
                case EUROCRYPT_PisTri26:
                    return new Pt26PsuConfig.Builder().build();
                case ACISP_DavCid17:
                    return new Dc17PsuConfig.Builder().build();
                case Ours:
                    properties.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, PsuType.Ours.name());
                    return PsuConfigUtils.createConfig(properties);
                default:
                    throw e;
            }
        }
    }

    private void assertPsuInitSendBytesAtLeast(PsuType type, PsuConfig config, long minBytes) throws MpcAbortException {
        long bytes = runPsuInitServerSendBytes(type, config);
        Assert.assertTrue(
            type + " init send bytes should be >= " + minBytes + " (base OT + COT setup), was " + bytes,
            bytes >= minBytes
        );
    }

    private void assertPsuInitSendBytesAtMost(PsuType type, PsuConfig config, long maxBytes) throws MpcAbortException {
        long bytes = runPsuInitServerSendBytes(type, config);
        Assert.assertTrue(
            type + " init send bytes should be <= " + maxBytes + ", was " + bytes,
            bytes <= maxBytes
        );
    }

    private long runPsuInitServerSendBytes(PsuType type, PsuConfig config) throws MpcAbortException {
        Rpc serverRpc = firstRpc;
        Rpc clientRpc = secondRpc;
        Party serverParty = serverRpc.ownParty();
        Party clientParty = clientRpc.ownParty();
        if (type == PsuType.EUROCRYPT_PisTri26) {
            PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(serverRpc, clientParty, config);
            PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(clientRpc, serverParty, config);
            server.setTaskId(0);
            client.setTaskId(0);
            server.getRpc().reset();
            client.getRpc().reset();
            Thread serverThread = new Thread(() -> {
                try {
                    server.init(MAX_SERVER, MAX_CLIENT);
                } catch (MpcAbortException e) {
                    throw new RuntimeException(e);
                }
            });
            Thread clientThread = new Thread(() -> {
                try {
                    client.init(MAX_CLIENT, MAX_SERVER);
                } catch (MpcAbortException e) {
                    throw new RuntimeException(e);
                }
            });
            serverThread.start();
            clientThread.start();
            joinUnchecked(serverThread);
            joinUnchecked(clientThread);
            return server.getRpc().getSendByteLength();
        }
        PsuServer server = PsuFactory.createServer(serverRpc, clientParty, config);
        PsuClient client = PsuFactory.createClient(clientRpc, serverParty, config);
        server.setTaskId(0);
        client.setTaskId(0);
        server.getRpc().reset();
        client.getRpc().reset();
        Thread serverThread = new Thread(() -> {
            try {
                server.init(MAX_SERVER, MAX_CLIENT);
            } catch (MpcAbortException e) {
                throw new RuntimeException(e);
            }
        });
        Thread clientThread = new Thread(() -> {
            try {
                client.init(MAX_CLIENT, MAX_SERVER);
            } catch (MpcAbortException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();
        clientThread.start();
        joinUnchecked(serverThread);
        joinUnchecked(clientThread);
        return server.getRpc().getSendByteLength();
    }

    private static void joinUnchecked(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
