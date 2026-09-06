package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * EUROCRYPT_PisTri26 PSU server (paper P0).
 *
 * <p>Per §6 the OPRF is now run <em>once</em> at the start of {@link #psu} on the client's input
 * set, after which all per-round PRF evaluations are local (via {@link MpOprfSenderOutput#getPrf}).
 * The per-round OPRF call has been removed.
 */
public class Pt26PsuServer extends AbstractPsuServer {
    private final CoreCotReceiver ot12Receiver;
    private final CoreCotSender ot3Sender;
    private final MpOprfSender mpOprfSender;
    private byte[][] hashKeys;
    private Pt26IbltParams ibltParams;

    public Pt26PsuServer(Rpc serverRpc, Party clientParty, Pt26PsuConfig config) {
        super(Pt26PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        ot12Receiver = CoreCotFactory.createReceiver(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(ot12Receiver);
        ot3Sender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(ot3Sender);
        mpOprfSender = OprfFactory.createMpOprfSender(serverRpc, clientParty, config.getMpOprfConfig());
        addSubPto(mpOprfSender);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int k = Pt26IbltParams.DEFAULT_K;
        hashKeys = new byte[k][];
        for (int i = 0; i < k; i++) {
            hashKeys[i] = BlockUtils.randomBlock(secureRandom);
        }
        // Send hash keys before sub-PTO init so the client can unblock its receive.
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_HASH_KEYS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        ArrayList<byte[]> keyPayload = new ArrayList<>(hashKeys.length);
        Collections.addAll(keyPayload, hashKeys);
        rpc.send(DataPacket.fromByteArrayList(header, keyPayload));
        ot12Receiver.init();
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        ot3Sender.init(delta);
        // The §6 optimization needs MP-OPRF capacity sized to the client's input set (one query
        // per X_1 element); per-round bin counts are no longer relevant.
        mpOprfSender.init(maxClientElementSize);
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        ibltParams = Pt26IbltParams.createDefault(maxServerElementSize, maxClientElementSize, elementByteLength, hashKeys);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        Pt26Iblt iblt0 = Pt26Iblt.encode(serverElementSet, ibltParams);

        // ---- Online-setup MP-OPRF: P0 holds the PRF key for all of X_1's evaluations. ----
        long pretotalBytes = rpc.getSendByteLength();
        long setupOprfBytesBaseline = pretotalBytes;
        MpOprfSenderOutput mpOprfSenderOutput = mpOprfSender.oprf(clientElementSize);
        long setupOprfBytes = rpc.getSendByteLength() - setupOprfBytesBaseline;

        // The first secure peel round probes the full IBLT table. Exchanging only local singleton
        // bins is tempting, but it leaks data-dependent local table structure and can drop bins when
        // the queue is larger than one OT batch.
        Set<Pt26BinIndex> queue = Pt26Iblt.allBins(ibltParams);
        int maxRounds = serverElementSize + clientElementSize + 2;
        long[] counters = new long[Pt26UnionPeel.CH_COUNT];
        Pt26UnionPeel unionPeel = new Pt26UnionPeel(
            true, rpc, getPtoDesc().getPtoId(), extraInfo, encodeTaskId,
            ownParty().getPartyId(), otherParty().getPartyId(), envType, secureRandom, ibltParams,
            null, ot12Receiver, ot3Sender, null,
            mpOprfSenderOutput, null,
            counters
        );
        int roundsRun = 0;
        for (int round = 0; round < maxRounds && !queue.isEmpty(); round++) {
            roundsRun++;
            Set<ByteBuffer> vRound = new HashSet<>();
            while (!queue.isEmpty()) {
                List<Pt26BinIndex> bins = Pt26PeelQueueUtils.takePeelBatch(queue);
                if (bins.isEmpty()) {
                    break;
                }
                Map<Pt26BinIndex, byte[]> peeled = unionPeel.run(iblt0, bins, round);
                for (byte[] encoded : peeled.values()) {
                    if (encoded == null) {
                        continue;
                    }
                    ByteBuffer decoded = Pt26Zm.decodeElement(encoded, elementByteLength, ibltParams);
                    if (Pt26Zm.isValidEncoding(encoded, decoded, ibltParams)) {
                        vRound.add(decoded);
                    }
                }
            }
            if (vRound.isEmpty()) {
                break;
            }
            // Paper Π_PSU: each party deletes only V ∩ X_b from their own IBLT (the pre-#1 impl
            // deleted V unconditionally, corrupting iblt_b's count/sum bins for elements the
            // party never owned). The corruption was harmless when equality was broken (no
            // client-only peels reached either side), but with the §6 fix it propagates into
            // round 2+ as bins with cnt=1 whose sum is no longer an X_b element.
            //
            // The bin-queue for the next round is still derived from the *full* V, so both
            // parties revisit the same set of bins (a mismatched queue would desynchronize the
            // round-2 OT/peel batch sizes).
            Set<ByteBuffer> myOwned = new HashSet<>(vRound);
            myOwned.retainAll(serverElementSet);
            iblt0.deleteSet(myOwned);
            queue = Pt26Iblt.nextQueue(ibltParams, vRound, iblt0);
        }
        long total = rpc.getSendByteLength() - pretotalBytes;
        logBandwidthBreakdown(setupOprfBytes, counters, total, roundsRun);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.PTO_END);
    }

    /**
     * Emits the six EUROCRYPT_PisTri26 communication counters per the post-#1 contract:
     * <ol>
     *     <li>setup MP-OPRF bytes sent by this party,</li>
     *     <li>per-round OPRF bytes (expected zero after #1),</li>
     *     <li>1-of-2 OT bytes,</li>
     *     <li>1-of-3 (emulated) OT bytes,</li>
     *     <li>peel-value return bytes (this side: 0 — server only receives peel values),</li>
     *     <li>total bytes sent by this party in {@code psu()}.</li>
     * </ol>
     * Bytes are accounted via {@link Rpc#getSendByteLength()} deltas at the boundary of each
     * channel; sub-PTO traffic (CoreCOT, MP-OPRF) is included in the surrounding channel.
     */
    private void logBandwidthBreakdown(long setupOprfBytes, long[] counters, long totalBytes, int rounds) {
        long ot12 = counters[Pt26UnionPeel.CH_OT12];
        long ot3 = counters[Pt26UnionPeel.CH_OT3];
        long peel = counters[Pt26UnionPeel.CH_PEEL];
        long perRoundOprf = counters[Pt26UnionPeel.CH_OPRF_PER_ROUND];
        long unaccounted = totalBytes - setupOprfBytes - ot12 - ot3 - peel - perRoundOprf;
        long bins = (long) ibltParams.totalBins();
        // Inherited from AbstractMultiPartyPto; renders the slf4j format with the supplied args.
        info("EUROCRYPT_PisTri26-BW[server] rounds={} bins0={} totalSend={}B  setupOprf={}B  perRoundOprf={}B"
            + "  ot12={}B  ot3={}B  peelReturn={}B  unaccounted(headers+hashKeys+initIO)={}B",
            rounds, bins, totalBytes, setupOprfBytes, perRoundOprf, ot12, ot3, peel, unaccounted);
    }
}
