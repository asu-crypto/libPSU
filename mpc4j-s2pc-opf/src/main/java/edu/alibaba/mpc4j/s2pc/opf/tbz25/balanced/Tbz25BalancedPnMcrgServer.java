package edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvs;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvsFactory;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvsFactory.Gf2kDokvsType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.galoisfield.gf2k.Gf2k;
import edu.alibaba.mpc4j.common.tool.galoisfield.gf2k.Gf2kFactory;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg.Tbz25NecRgServer;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg.Tbz25PecRgDdhServer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced.Tbz25PmcrgAuxPtoDesc.PtoStep;

/**
 * TBZ25 balanced pnMCRG — server side (paper S): RS21 receiver, Fig.13 OKVS decode, pECRG, nECRG.
 */
public class Tbz25BalancedPnMcrgServer extends AbstractTwoPartyPto {
    private static final String PARALLEL_STREAM = "IntStream.parallel";
    private static final String SERIAL = "serial";
    private final MpOprfReceiver rs21MpOprfReceiver;
    private final Tbz25PecRgDdhServer pecRgDdhServer;
    private final Tbz25NecRgServer necrgServer;
    private final Gf2kDokvsType gf2kDokvsType;
    private int maxM;
    private int maxOkvsPairs;
    private Gf2k gf2k;

    public Tbz25BalancedPnMcrgServer(Rpc serverRpc, Party clientParty, Tbz25BalancedPnMcrgConfig config) {
        super(Tbz25BalancedPnMcrgPtoDesc.getInstance(), serverRpc, clientParty, config);
        Rs21MpOprfConfig rs21 = config.getRs21MpOprfConfig();
        rs21MpOprfReceiver = (MpOprfReceiver) OprfFactory.createOprfReceiver(serverRpc, clientParty, rs21);
        addSubPto(rs21MpOprfReceiver);
        pecRgDdhServer = new Tbz25PecRgDdhServer(serverRpc, clientParty, config.getPecRgDdhConfig());
        addSubPto(pecRgDdhServer);
        necrgServer = new Tbz25NecRgServer(serverRpc, clientParty, config.getNecRgConfig());
        addSubPto(necrgServer);
        gf2kDokvsType = rs21.getOkvsType();
    }

    public void init(int maxM, int maxOkvsPairs) throws MpcAbortException {
        MathPreconditions.checkPositive("maxM", maxM);
        MathPreconditions.checkPositive("maxOkvsPairs", maxOkvsPairs);
        this.maxM = maxM;
        this.maxOkvsPairs = maxOkvsPairs;
        logPhaseInfo(PtoState.INIT_BEGIN);
        rs21MpOprfReceiver.init(maxM, maxOkvsPairs);
        gf2k = Gf2kFactory.createInstance(envType);
        int pointBitLen = ByteEccFactory.createFullInstance(envType).pointByteLength() * Byte.SIZE;
        pecRgDdhServer.init(maxM);
        necrgServer.init(maxM, pointBitLen);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        rs21MpOprfReceiver.setParallel(parallel);
        pecRgDdhServer.setParallel(parallel);
        necrgServer.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        rs21MpOprfReceiver.setTaskId(taskId);
        pecRgDdhServer.setTaskId(taskId);
        necrgServer.setTaskId(taskId);
    }

    /**
     * Runs RS21 (server as MP-OPRF receiver), Fig.13 OKVS decode, pECRG, and nECRG.
     *
     * @param serverRowKeys cuckoo slot keys (x||γ), length m.
     * @param pi            permutation π over [m].
     * @return pnMCRG output u (length m, OT block-sized rows after nECRG).
     */
    public byte[][] execute(byte[][] serverRowKeys, int[] pi) throws MpcAbortException {
        checkInitialized();
        int m = serverRowKeys.length;
        MathPreconditions.checkEqual("m", "maxM", m, maxM);
        logPhaseInfo(PtoState.PTO_BEGIN);

        beginPhaseMetric();
        MpOprfReceiverOutput rs21Out = rs21MpOprfReceiver.oprf(serverRowKeys);
        endPhaseMetric("PNMCRG_RS21_OPRF", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        DataPacketHeader okvsHeader = new DataPacketHeader(
            encodeTaskId, Tbz25PmcrgAuxPtoDesc.getInstance().getPtoId(), PtoStep.CLIENT_SEND_OKVS_FIG13.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(okvsHeader).getPayload();
        endPhaseMetric("PNMCRG_OKVS_RECV", SERIAL);

        beginPhaseMetric();
        int okvsKeyNum = Gf2kDokvsFactory.getHashKeyNum(gf2kDokvsType);
        int okvsM = Gf2kDokvsFactory.getM(envType, gf2kDokvsType, maxOkvsPairs);
        MpcAbortPreconditions.checkArgument(payload.size() == okvsKeyNum + okvsM);
        byte[][] okvsKeys = IntStream.range(0, okvsKeyNum).mapToObj(payload::get).toArray(byte[][]::new);
        byte[][] storage = IntStream.range(okvsKeyNum, payload.size()).mapToObj(payload::get).toArray(byte[][]::new);

        Gf2kDokvs<ByteBuffer> dokvs = Gf2kDokvsFactory.createInstance(envType, gf2kDokvsType, maxOkvsPairs, okvsKeys);
        dokvs.setParallelEncode(parallel);
        byte[][] eVec = new byte[m][];
        for (int i = 0; i < m; i++) {
            byte[] dec = dokvs.decode(storage, ByteBuffer.wrap(serverRowKeys[i]));
            gf2k.addi(dec, rs21Out.getPrf(i));
            eVec[i] = dec;
        }
        endPhaseMetric("PNMCRG_OKVS_DECODE", SERIAL);

        beginPhaseMetric();
        byte[][] sPmcrg = pecRgDdhServer.pecRg(pi, eVec);
        endPhaseMetric("PNMCRG_PECRG", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        byte[][] u = necrgServer.necrg(sPmcrg);
        endPhaseMetric("PNMCRG_NECRG", parallel ? PARALLEL_STREAM : SERIAL);

        logPhaseInfo(PtoState.PTO_END);
        return u;
    }
}
