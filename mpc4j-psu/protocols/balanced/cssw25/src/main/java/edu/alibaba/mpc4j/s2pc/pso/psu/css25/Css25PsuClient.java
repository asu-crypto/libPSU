package edu.alibaba.mpc4j.s2pc.pso.psu.css25;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnSender;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.rosn.RosnSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiFactory;
import edu.alibaba.mpc4j.s2pc.pso.cpsi.ccpsi.CcpsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * CSSW25 PSU receiver (paper R): simple-hash side, circuit-PSI server, OT receiver.
 *
 * <p>Diagnostic: {@link #psu} emits a {@code ASIACCS_CSSW25-BW[client] ...} summary line with the six
 * paper-aligned communication buckets (setup OPRF / online OPRF / setup CCPSI / online CCPSI /
 * ShTr+CnP / final OT) so the breakdown is directly comparable to Tab. 2 of [Chandran+25].
 */
public class Css25PsuClient extends AbstractPsuClient {
    private static final String PARALLEL_STREAM = "IntStream.parallel";
    private static final String SERIAL = "serial";
    private final MpOprfReceiver mpOprfReceiver;
    private final CcpsiServer<ByteBuffer> ccpsiServer;
    private final RosnSender rosnSender;
    private final CoreCotReceiver coreCotReceiver;
    /**
     * Init-phase send bytes attributed to the MP-OPRF sub-PTO ({@link MpOprfReceiver#init}).
     */
    private long setupOprfBytes;
    /**
     * Init-phase send bytes attributed to the circuit-PSI sub-PTO ({@link CcpsiServer#init}).
     */
    private long setupCcpsiBytes;
    /**
     * Init-phase send bytes attributed to the ROSN sub-PTO ({@link RosnSender#init}).
     */
    private long setupShtrBytes;
    /**
     * Init-phase send bytes attributed to the core-COT sub-PTO ({@link CoreCotReceiver#init}).
     */
    private long setupOtBytes;

    public Css25PsuClient(Rpc clientRpc, Party serverParty, Css25PsuConfig config) {
        super(Css25PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        if (config.isPaperExact()) {
            throw new IllegalArgumentException(Css25PsuConfig.paperExactUnsupportedMessage());
        }
        mpOprfReceiver = OprfFactory.createMpOprfReceiver(clientRpc, serverParty, config.getMpOprfConfig());
        addSubPto(mpOprfReceiver);
        ccpsiServer = CcpsiFactory.<ByteBuffer>createServer(clientRpc, serverParty, config.getCcpsiConfig());
        addSubPto(ccpsiServer);
        rosnSender = RosnFactory.createSender(clientRpc, serverParty, config.getRosnConfig());
        addSubPto(rosnSender);
        coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        long base = rpc.getSendByteLength();
        mpOprfReceiver.init(maxClientElementSize, maxServerElementSize);
        setupOprfBytes = rpc.getSendByteLength() - base;
        base += setupOprfBytes;

        // Ccpsi server on the PSU client holds receiver (client) elements; peer count is |server|.
        ccpsiServer.init(maxClientElementSize, maxServerElementSize);
        setupCcpsiBytes = rpc.getSendByteLength() - base;
        base += setupCcpsiBytes;

        rosnSender.init();
        setupShtrBytes = rpc.getSendByteLength() - base;
        base += setupShtrBytes;

        coreCotReceiver.init();
        setupOtBytes = rpc.getSendByteLength() - base;
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        long psuStart = rpc.getSendByteLength();
        long base = psuStart;

        beginPhaseMetric();
        byte[][] oprfInputs = new byte[clientElementSize][];
        for (int i = 0; i < clientElementSize; i++) {
            oprfInputs[i] = BytesUtils.clone(clientElementArrayList.get(i).array());
        }
        MpOprfReceiverOutput mpOprfOut = (MpOprfReceiverOutput) mpOprfReceiver.oprf(oprfInputs);
        int rawPrfByteLength = mpOprfOut.getPrfByteLength();
        // CSSW25 post-OPRF compression: hash the w-bit raw matrix-row OPRF output down to the
        // paper-target length, keyed by set size (see authors' MP-OPRF-Parameters.h::
        // get_mp_oprf_hash_in_bytes, cuckoo_hash_num=3 branch — the path we actually run via
        // NO_STASH_PSZ18_3_HASH_E04). Both parties apply the identical CRHF so OPRF-equality
        // on shared inputs is preserved. These compressed values are only circuit-PSI labels;
        // the final OT carries original sender elements with length elementByteLength.
        int oprfSetSize = Math.max(clientElementSize, serverElementSize);
        int maskedByteLength = Css25CnPUtils.paperMaskedElementByteLength(oprfSetSize);
        int paperTargetByteLength = Css25CnPUtils.paperMaskedElementByteLength(oprfSetSize);
        Hash maskHash = HashFactory.createInstance(envType, maskedByteLength);
        Set<ByteBuffer> maskedClientSet = new HashSet<ByteBuffer>(clientElementSize);
        for (int i = 0; i < clientElementSize; i++) {
            maskedClientSet.add(ByteBuffer.wrap(maskHash.digestToBytes(mpOprfOut.getPrf(i))));
        }
        info("ASIACCS_CSSW25 maskedElementByteLength[client]: rawOprf={}B -> compressed={}B (paperTarget={}B, setSize={}, elementByteLength={}B)",
            rawPrfByteLength, maskedByteLength,
            paperTargetByteLength, oprfSetSize, elementByteLength);
        long onlineOprfBytes = rpc.getSendByteLength() - base;
        base += onlineOprfBytes;
        endPhaseMetric("MP_OPRF", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        SquareZ2Vector x1 = ccpsiServer.psi(maskedClientSet, serverElementSize);
        int beta = Math.toIntExact(x1.getNum());
        long onlineCcpsiBytes = rpc.getSendByteLength() - base;
        base += onlineCcpsiBytes;
        endPhaseMetric("CCPSI", SERIAL);

        beginPhaseMetric();
        RosnSenderOutput shTrOut = rosnSender.rosn(beta, Css25CnPUtils.SHTR_BYTE_LENGTH);
        long shtrOnlineBytes = rpc.getSendByteLength() - base;
        base += shtrOnlineBytes;
        endPhaseMetric("ROSN_SHTR", SERIAL);

        beginPhaseMetric();
        Css25CnPUtils.CnPOutput cnpOut = Css25CnPUtils.runReceiverCnP(
            rpc, getPtoDesc(), encodeTaskId, extraInfo, ownParty(), otherParty(),
            Css25CnPUtils.toBits(x1), shTrOut
        );
        boolean[] zTilde = cnpOut.zTilde;
        long cnpBytes = rpc.getSendByteLength() - base;
        base += cnpBytes;
        endPhaseMetric("CNP", SERIAL);

        beginPhaseMetric();
        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(zTilde);
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> encPayload = rpc.receive(encHeader).getPayload();
        MpcAbortPreconditions.checkArgument(encPayload.size() == beta);
        byte[] payloadBot = Css25CnPUtils.botElementBytes(elementByteLength);
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        Set<ByteBuffer> union = new HashSet<ByteBuffer>(beta);
        for (int j = 0; j < beta; j++) {
            if (zTilde[j]) {
                continue;
            }
            byte[] message = encPrg.extendToBytes(cotReceiverOutput.getRb(j));
            BytesUtils.xori(message, encPayload.get(j));
            if (!Arrays.equals(message, payloadBot)) {
                union.add(ByteBuffer.wrap(message));
            }
        }
        union.addAll(clientElementArrayList);
        union.remove(botElementByteBuffer);
        // PSI-CA is |X ∩ Y|; circuit bins (z̃) can exceed that when β > n at small set sizes.
        int psica = clientElementSize + serverElementSize - union.size();
        long finalOtBytes = rpc.getSendByteLength() - base;
        endPhaseMetric("OT_UNION", SERIAL);

        long psuTotal = rpc.getSendByteLength() - psuStart;
        logBandwidthBreakdown(
            beta, onlineOprfBytes, onlineCcpsiBytes,
            shtrOnlineBytes, cnpBytes, finalOtBytes, psuTotal
        );

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }

    /**
     * Emits the six paper-aligned communication buckets (Tab. 2 of [Chandran+25]):
     * <ol>
     *     <li>setup OPRF — bytes during {@link MpOprfReceiver#init}</li>
     *     <li>online OPRF — bytes during {@link MpOprfReceiver#oprf}</li>
     *     <li>setup CCPSI — bytes during {@link CcpsiServer#init}</li>
     *     <li>online CCPSI — bytes during {@link CcpsiServer#psi}</li>
     *     <li>ShTr+CnP — combined: ROSN init + ROSN online + the {@code m / m̃} CnP exchange</li>
     *     <li>final OT — core-COT init + core-COT online; receiver only sends choice-bit
     *         corrections (the encrypted-Cuckoo payload is server→client, not counted here)</li>
     * </ol>
     */
    private void logBandwidthBreakdown(
        int beta, long onlineOprf, long onlineCcpsi,
        long shtrOnline, long cnpExchange, long finalOtOnline, long psuTotal
    ) {
        long shtrCnp = setupShtrBytes + shtrOnline + cnpExchange;
        long finalOt = setupOtBytes + finalOtOnline;
        long unaccounted = psuTotal - onlineOprf - onlineCcpsi - shtrOnline - cnpExchange - finalOtOnline;
        info("ASIACCS_CSSW25-BW[client] beta={} psuSend={}B"
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
