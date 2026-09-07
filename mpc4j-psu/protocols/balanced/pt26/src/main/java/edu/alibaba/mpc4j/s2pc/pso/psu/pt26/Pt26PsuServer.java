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
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuTwoSidedServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * EUROCRYPT_PisTri26 PSU server (paper P0). Both parties learn {@code X0 ∪ X1}.
 *
 * <p>Per §6 the OPRF runs once at the start of {@link #psu} on the client's input set; peel rounds
 * evaluate equality tags locally via {@link MpOprfSenderOutput#getPrf}.
 */
public class Pt26PsuServer extends AbstractPsuTwoSidedServer {
    private static final byte STATUS_OK = 1;
    private static final byte STATUS_FAIL = 0;

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
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        int k = Pt26IbltParams.DEFAULT_K;
        hashKeys = new byte[k][];
        for (int i = 0; i < k; i++) {
            hashKeys[i] = BlockUtils.randomBlock(secureRandom);
        }
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
        mpOprfSender.init(maxClientElementSize);
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuTwoSidedOutput psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        ibltParams = Pt26IbltParams.createDefault(maxServerElementSize, maxClientElementSize, elementByteLength, hashKeys);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        Pt26Iblt iblt0 = Pt26Iblt.encode(serverElementArrayList, ibltParams);

        long pretotalBytes = rpc.getSendByteLength();
        long setupOprfBytesBaseline = pretotalBytes;
        MpOprfSenderOutput mpOprfSenderOutput = mpOprfSender.oprf(clientElementSize);
        long setupOprfBytes = rpc.getSendByteLength() - setupOprfBytesBaseline;

        Set<Pt26BinIndex> queue = Pt26Iblt.allBins(ibltParams);
        Set<ByteBuffer> recovered = new HashSet<>();
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
            recovered.addAll(vRound);
            Set<ByteBuffer> myOwned = new HashSet<>(vRound);
            myOwned.retainAll(serverElementSet);
            iblt0.deleteSet(myOwned);
            queue = Pt26Iblt.nextQueue(ibltParams, vRound, iblt0);
        }

        boolean localOk = iblt0.isFullyPeeled();
        exchangePeelStatus(localOk);

        long total = rpc.getSendByteLength() - pretotalBytes;
        logBandwidthBreakdown(setupOprfBytes, counters, total, roundsRun);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.PTO_END);

        Set<ByteBuffer> union = new HashSet<>(serverElementSet);
        union.addAll(recovered);
        union.remove(botElementByteBuffer);
        return new PsuTwoSidedOutput(union);
    }

    private void exchangePeelStatus(boolean localOk) throws MpcAbortException {
        DataPacketHeader clientHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PEEL_STATUS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> clientPayload = rpc.receive(clientHeader).getPayload();
        MpcAbortPreconditions.checkArgument(clientPayload.size() == 1 && clientPayload.get(0).length == 1);
        boolean peerOk = clientPayload.get(0)[0] == STATUS_OK;

        DataPacketHeader serverHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PEEL_STATUS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(
            serverHeader, Collections.singletonList(new byte[]{localOk ? STATUS_OK : STATUS_FAIL})
        ));

        if (!localOk || !peerOk) {
            throw new MpcAbortException("PT26 IBLT residual non-empty or peer peel failure");
        }
    }

    private void logBandwidthBreakdown(long setupOprfBytes, long[] counters, long totalBytes, int rounds) {
        long ot12 = counters[Pt26UnionPeel.CH_OT12];
        long ot3 = counters[Pt26UnionPeel.CH_OT3];
        long peel = counters[Pt26UnionPeel.CH_PEEL];
        long perRoundOprf = counters[Pt26UnionPeel.CH_OPRF_PER_ROUND];
        long unaccounted = totalBytes - setupOprfBytes - ot12 - ot3 - peel - perRoundOprf;
        long bins = ibltParams.totalBins();
        info("EUROCRYPT_PisTri26-BW[server] rounds={} bins0={} totalSend={}B  setupOprf={}B  perRoundOprf={}B"
                + "  ot12={}B  ot3={}B  peelReturn={}B  unaccounted(headers+hashKeys+initIO)={}B",
            rounds, bins, totalBytes, setupOprfBytes, perRoundOprf, ot12, ot3, peel, unaccounted);
    }
}
