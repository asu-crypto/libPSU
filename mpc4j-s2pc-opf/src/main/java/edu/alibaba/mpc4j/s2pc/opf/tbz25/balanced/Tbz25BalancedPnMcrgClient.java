package edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
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
import edu.alibaba.mpc4j.common.tool.crypto.prf.Prf;
import edu.alibaba.mpc4j.common.tool.galoisfield.gf2k.Gf2k;
import edu.alibaba.mpc4j.common.tool.galoisfield.gf2k.Gf2kFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.necrg.Tbz25NecRgClient;
import edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg.Tbz25PecRgDdhClient;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static edu.alibaba.mpc4j.s2pc.opf.tbz25.balanced.Tbz25PmcrgAuxPtoDesc.PtoStep;

/**
 * TBZ25 balanced pnMCRG — client side (paper R).
 */
public class Tbz25BalancedPnMcrgClient extends AbstractTwoPartyPto {
    private static final String PARALLEL_STREAM = "IntStream.parallel";
    private static final String SERIAL = "serial";
    private final Rs21MpOprfSender rs21MpOprfSender;
    private final Tbz25PecRgDdhClient pecRgDdhClient;
    private final Tbz25NecRgClient necrgClient;
    private final Gf2kDokvsType gf2kDokvsType;
    private int maxM;
    private int maxOkvsPairs;
    private Gf2k gf2k;

    public Tbz25BalancedPnMcrgClient(Rpc clientRpc, Party serverParty, Tbz25BalancedPnMcrgConfig config) {
        super(Tbz25BalancedPnMcrgPtoDesc.getInstance(), clientRpc, serverParty, config);
        Rs21MpOprfConfig rs21 = config.getRs21MpOprfConfig();
        rs21MpOprfSender = (Rs21MpOprfSender) OprfFactory.createOprfSender(clientRpc, serverParty, rs21);
        addSubPto(rs21MpOprfSender);
        pecRgDdhClient = new Tbz25PecRgDdhClient(clientRpc, serverParty, config.getPecRgDdhConfig());
        addSubPto(pecRgDdhClient);
        necrgClient = new Tbz25NecRgClient(clientRpc, serverParty, config.getNecRgConfig());
        addSubPto(necrgClient);
        gf2kDokvsType = rs21.getOkvsType();
    }

    public void init(int maxM, int maxOkvsPairs) throws MpcAbortException {
        MathPreconditions.checkPositive("maxM", maxM);
        MathPreconditions.checkPositive("maxOkvsPairs", maxOkvsPairs);
        this.maxM = maxM;
        this.maxOkvsPairs = maxOkvsPairs;
        logPhaseInfo(PtoState.INIT_BEGIN);
        rs21MpOprfSender.init(maxM, maxOkvsPairs);
        gf2k = Gf2kFactory.createInstance(envType);
        int pointBitLen = ByteEccFactory.createFullInstance(envType).pointByteLength() * Byte.SIZE;
        pecRgDdhClient.init(maxM);
        necrgClient.init(maxM, pointBitLen);
        initState();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        rs21MpOprfSender.setParallel(parallel);
        pecRgDdhClient.setParallel(parallel);
        necrgClient.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        rs21MpOprfSender.setTaskId(taskId);
        pecRgDdhClient.setTaskId(taskId);
        necrgClient.setTaskId(taskId);
    }

    /**
     * @param m                 number of cuckoo bins (mc).
     * @param clientElements    client set.
     * @param clientElementSize |Y|.
     * @param binHashes         simple-hash PRFs (same as GMR21 client).
     * @param cuckooHashNum     hash function count.
     * @param binNum            must equal m.
     */
    public byte[][] execute(int m, ArrayList<ByteBuffer> clientElements, int clientElementSize, Prf[] binHashes,
        int cuckooHashNum, int binNum) throws MpcAbortException {
        checkInitialized();
        MathPreconditions.checkEqual("m", "binNum", m, binNum);
        Preconditions.checkArgument(clientElements.size() == clientElementSize);
        logPhaseInfo(PtoState.PTO_BEGIN);

        beginPhaseMetric();
        Rs21MpOprfSenderOutput rs21Out = (Rs21MpOprfSenderOutput) rs21MpOprfSender.oprf(m);
        endPhaseMetric("PNMCRG_RS21_OPRF", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        byte[][] dVector = IntStream.range(0, m)
            .mapToObj(i -> gf2k.createRandom(secureRandom))
            .toArray(byte[][]::new);

        Map<ByteBuffer, byte[]> kvMap = new HashMap<>();
        for (int hashIndex = 0; hashIndex < cuckooHashNum; hashIndex++) {
            for (int j = 0; j < clientElementSize; j++) {
                ByteBuffer y = clientElements.get(j);
                byte[] entryBytes = y.array();
                int binIndex = binHashes[hashIndex].getInteger(entryBytes, binNum);
                byte[] extendBytes = new byte[entryBytes.length + 2 * Integer.BYTES];
                ByteBuffer eb = ByteBuffer.wrap(extendBytes);
                eb.put(entryBytes);
                eb.putInt(hashIndex);
                eb.putInt(binIndex);
                byte[] kb = eb.array();
                byte[] val = gf2k.add(rs21Out.getPrf(kb), dVector[binIndex]);
                kvMap.put(ByteBuffer.wrap(kb), val);
            }
        }
        endPhaseMetric("PNMCRG_OKVS_KV_BUILD", SERIAL);

        beginPhaseMetric();
        byte[][] okvsKeys = BlockUtils.randomBlocks(Gf2kDokvsFactory.getHashKeyNum(gf2kDokvsType), secureRandom);
        Gf2kDokvs<ByteBuffer> dokvs = Gf2kDokvsFactory.createInstance(envType, gf2kDokvsType, maxOkvsPairs, okvsKeys);
        dokvs.setParallelEncode(parallel);
        byte[][] encoded = dokvs.encode(kvMap, true);
        endPhaseMetric("PNMCRG_OKVS_ENCODE", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        List<byte[]> payload = new LinkedList<>();
        for (byte[] okvsKey : okvsKeys) {
            payload.add(okvsKey);
        }
        for (byte[] row : encoded) {
            payload.add(row);
        }
        DataPacketHeader okvsHeader = new DataPacketHeader(
            encodeTaskId, Tbz25PmcrgAuxPtoDesc.getInstance().getPtoId(), PtoStep.CLIENT_SEND_OKVS_FIG13.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(okvsHeader, payload));
        endPhaseMetric("PNMCRG_OKVS_SEND", SERIAL);

        beginPhaseMetric();
        byte[][] tPmcrg = pecRgDdhClient.pecRg(dVector);
        endPhaseMetric("PNMCRG_PECRG", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        byte[][] v = necrgClient.necrg(tPmcrg);
        endPhaseMetric("PNMCRG_NECRG", parallel ? PARALLEL_STREAM : SERIAL);

        logPhaseInfo(PtoState.PTO_END);
        return v;
    }
}
