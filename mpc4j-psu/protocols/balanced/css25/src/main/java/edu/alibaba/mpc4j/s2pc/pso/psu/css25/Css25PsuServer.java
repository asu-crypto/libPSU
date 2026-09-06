package edu.alibaba.mpc4j.s2pc.pso.psu.css25;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.network.PermutationNetworkUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnReceiver;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiFactory;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * CSSW25 PSU sender (paper S): Cuckoo side, circuit-PSI client, OT sender.
 *
 * <p>Diagnostic: {@link #psu} emits a {@code ASIACCS_CSSW25-BW[server] ...} summary line with the six
 * paper-aligned communication buckets (setup OPRF / online OPRF / setup CCPSI / online CCPSI /
 * ShTr+CnP / final OT) so the breakdown is directly comparable to Tab. 2 of [Chandran+25].
 */
public class Css25PsuServer extends AbstractPsuServer {
    private static final String PARALLEL_STREAM = "IntStream.parallel";
    private static final String SERIAL = "serial";
    private final MpOprfSender mpOprfSender;
    private final CcpsiClient<ByteBuffer> ccpsiClient;
    private final RosnReceiver rosnReceiver;
    private final CoreCotSender coreCotSender;
    /**
     * Init-phase send bytes attributed to the MP-OPRF sub-PTO ({@link MpOprfSender#init}).
     */
    private long setupOprfBytes;
    /**
     * Init-phase send bytes attributed to the circuit-PSI sub-PTO ({@link CcpsiClient#init}).
     */
    private long setupCcpsiBytes;
    /**
     * Init-phase send bytes attributed to the ROSN sub-PTO ({@link RosnReceiver#init}).
     */
    private long setupShtrBytes;
    /**
     * Init-phase send bytes attributed to the core-COT sub-PTO ({@link CoreCotSender#init}).
     */
    private long setupOtBytes;

    public Css25PsuServer(Rpc serverRpc, Party clientParty, Css25PsuConfig config) {
        super(Css25PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        if (config.isPaperExact()) {
            throw new IllegalArgumentException(Css25PsuConfig.paperExactUnsupportedMessage());
        }
        mpOprfSender = OprfFactory.createMpOprfSender(serverRpc, clientParty, config.getMpOprfConfig());
        addSubPto(mpOprfSender);
        ccpsiClient = CcpsiFactory.createClient(serverRpc, clientParty, config.getCcpsiConfig());
        addSubPto(ccpsiClient);
        rosnReceiver = RosnFactory.createReceiver(serverRpc, clientParty, config.getRosnConfig());
        addSubPto(rosnReceiver);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        long base = rpc.getSendByteLength();
        mpOprfSender.init(maxClientElementSize, maxServerElementSize);
        setupOprfBytes = rpc.getSendByteLength() - base;
        base += setupOprfBytes;

        // Ccpsi client on the PSU server holds cuckoo (server) elements; peer count is |client|.
        ccpsiClient.init(maxServerElementSize, maxClientElementSize);
        setupCcpsiBytes = rpc.getSendByteLength() - base;
        base += setupCcpsiBytes;

        rosnReceiver.init();
        setupShtrBytes = rpc.getSendByteLength() - base;
        base += setupShtrBytes;

        byte[] delta = BlockUtils.randomBlock(secureRandom);
        coreCotSender.init(delta);
        setupOtBytes = rpc.getSendByteLength() - base;
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        long psuStart = rpc.getSendByteLength();
        long base = psuStart;

        beginPhaseMetric();
        MpOprfSenderOutput mpOprfOut = (MpOprfSenderOutput) mpOprfSender.oprf(clientElementSize);
        int rawPrfByteLength = mpOprfOut.getPrfByteLength();
        // CSSW25 post-OPRF compression: hash the w-bit raw matrix-row OPRF output down to the
        // paper-target length, keyed by set size (see authors' MP-OPRF-Parameters.h::
        // get_mp_oprf_hash_in_bytes, cuckoo_hash_num=3 branch — the path we actually run via
        // NO_STASH_PSZ18_3_HASH_E04). Both parties apply the identical CRHF so OPRF-equality
        // on shared inputs is preserved. These compressed values are only circuit-PSI labels;
        // the final OT must still carry the sender's original set elements.
        int oprfSetSize = Math.max(serverElementSize, clientElementSize);
        int maskedByteLength = Css25CnPUtils.paperMaskedElementByteLength(oprfSetSize);
        int paperTargetByteLength = Css25CnPUtils.paperMaskedElementByteLength(oprfSetSize);
        Hash maskHash = HashFactory.createInstance(envType, maskedByteLength);
        Set<ByteBuffer> maskedServerSet = new HashSet<>(serverElementSize);
        Map<ByteBuffer, byte[]> maskedToServerElement = new HashMap<>(serverElementSize);
        for (ByteBuffer elem : serverElementArrayList) {
            byte[] elementBytes = BytesUtils.clone(elem.array());
            ByteBuffer maskedElement = ByteBuffer.wrap(maskHash.digestToBytes(mpOprfOut.getPrf(elementBytes)));
            MpcAbortPreconditions.checkArgument(maskedServerSet.add(maskedElement), "ASIACCS_CSSW25 compressed OPRF collision");
            maskedToServerElement.put(maskedElement, elementBytes);
        }
        info("ASIACCS_CSSW25 maskedElementByteLength[server]: rawOprf={}B -> compressed={}B (paperTarget={}B, setSize={}, elementByteLength={}B)",
            rawPrfByteLength, maskedByteLength,
            paperTargetByteLength, oprfSetSize, elementByteLength);
        long onlineOprfBytes = rpc.getSendByteLength() - base;
        base += onlineOprfBytes;
        endPhaseMetric("MP_OPRF", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        CcpsiClientOutput<ByteBuffer> ccOut = ccpsiClient.psi(maskedServerSet, clientElementSize);
        SquareZ2Vector x0 = ccOut.getZ1();
        ArrayList<ByteBuffer> cuckooTable = ccOut.getTable();
        int beta = Math.toIntExact(x0.getNum());
        long onlineCcpsiBytes = rpc.getSendByteLength() - base;
        base += onlineCcpsiBytes;
        endPhaseMetric("CCPSI", SERIAL);

        beginPhaseMetric();
        int[] pi = PermutationNetworkUtils.randomPermutation(beta, secureRandom);
        RosnReceiverOutput shTrOut = rosnReceiver.rosn(pi, Css25CnPUtils.SHTR_BYTE_LENGTH);
        long shtrOnlineBytes = rpc.getSendByteLength() - base;
        base += shtrOnlineBytes;
        endPhaseMetric("ROSN_SHTR", SERIAL);

        beginPhaseMetric();
        Css25CnPUtils.runSenderCnP(
            rpc, getPtoDesc(), encodeTaskId, extraInfo, ownParty(), otherParty(),
            pi, Css25CnPUtils.toBits(x0), shTrOut
        );
        long cnpBytes = rpc.getSendByteLength() - base;
        base += cnpBytes;
        endPhaseMetric("CNP", SERIAL);

        beginPhaseMetric();
        int[] inversePi = new int[beta];
        for (int i = 0; i < beta; i++) {
            inversePi[pi[i]] = i;
        }
        CotSenderOutput cotSenderOutput = coreCotSender.send(beta);
        byte[] payloadBot = Css25CnPUtils.botElementBytes(elementByteLength);
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        List<byte[]> encPayload = new ArrayList<>(beta);
        for (int j = 0; j < beta; j++) {
            int origBin = inversePi[j];
            ByteBuffer elem = cuckooTable.get(origBin);
            byte[] ciphertext = encPrg.extendToBytes(cotSenderOutput.getR0(j));
            if (elem == null) {
                BytesUtils.xori(ciphertext, payloadBot);
            } else {
                byte[] elementBytes = maskedToServerElement.get(elem);
                MpcAbortPreconditions.checkArgument(elementBytes != null, "ASIACCS_CSSW25 missing original sender element");
                BytesUtils.xori(ciphertext, elementBytes);
            }
            encPayload.add(ciphertext);
        }
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(encHeader, encPayload));
        long finalOtBytes = rpc.getSendByteLength() - base;
        endPhaseMetric("OT_ENC_SEND", SERIAL);

        long psuTotal = rpc.getSendByteLength() - psuStart;
        logBandwidthBreakdown(
            beta, onlineOprfBytes, onlineCcpsiBytes,
            shtrOnlineBytes, cnpBytes, finalOtBytes, psuTotal
        );

        logPhaseInfo(PtoState.PTO_END);
    }

    /**
     * Emits the six paper-aligned communication buckets (Tab. 2 of [Chandran+25]):
     * <ol>
     *     <li>setup OPRF — bytes during {@link MpOprfSender#init}</li>
     *     <li>online OPRF — bytes during {@link MpOprfSender#oprf}</li>
     *     <li>setup CCPSI — bytes during {@link CcpsiClient#init}</li>
     *     <li>online CCPSI — bytes during {@link CcpsiClient#psi}</li>
     *     <li>ShTr+CnP — combined: ROSN init + ROSN online + the {@code m / m̃} CnP exchange</li>
     *     <li>final OT — core-COT init + core-COT online + the encrypted-Cuckoo payload</li>
     * </ol>
     * Buckets are accounted via {@link Rpc#getSendByteLength()} deltas at phase boundaries; only
     * {@link #psu}-phase deltas are summed into {@code psuTotal} (init bytes are reported
     * separately as their own counters).
     */
    private void logBandwidthBreakdown(
        int beta, long onlineOprf, long onlineCcpsi,
        long shtrOnline, long cnpExchange, long finalOtOnline, long psuTotal
    ) {
        long shtrCnp = setupShtrBytes + shtrOnline + cnpExchange;
        long finalOt = setupOtBytes + finalOtOnline;
        long unaccounted = psuTotal - onlineOprf - onlineCcpsi - shtrOnline - cnpExchange - finalOtOnline;
        info("ASIACCS_CSSW25-BW[server] beta={} psuSend={}B"
            + "  setupOprf={}B  onlineOprf={}B"
            + "  setupCcpsi={}B  onlineCcpsi={}B"
            + "  shtrCnp(setup+rosn+cnp)={}B  finalOt(setup+online)={}B"
            + "  psuUnaccounted(headers)={}B",
            beta, psuTotal,
            setupOprfBytes, onlineOprf,
            setupCcpsiBytes, onlineCcpsi,
            shtrCnp, finalOt,
            unaccounted);
    }
}
