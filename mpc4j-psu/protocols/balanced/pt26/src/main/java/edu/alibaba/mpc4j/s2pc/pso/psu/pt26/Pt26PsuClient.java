package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuTwoSidedClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * EUROCRYPT_PisTri26 PSU client (paper P1). Both parties learn {@code X0 ∪ X1}.
 */
public class Pt26PsuClient extends AbstractPsuTwoSidedClient {
    private static final byte STATUS_OK = 1;
    private static final byte STATUS_FAIL = 0;

    private final CoreCotSender ot12Sender;
    private final CoreCotReceiver ot3Receiver;
    private final MpOprfReceiver mpOprfReceiver;
    private byte[][] hashKeys;
    private Pt26IbltParams ibltParams;

    public Pt26PsuClient(Rpc clientRpc, Party serverParty, Pt26PsuConfig config) {
        super(Pt26PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        ot12Sender = CoreCotFactory.createSender(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(ot12Sender);
        ot3Receiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(ot3Receiver);
        mpOprfReceiver = OprfFactory.createMpOprfReceiver(clientRpc, serverParty, config.getMpOprfConfig());
        addSubPto(mpOprfReceiver);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_HASH_KEYS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        hashKeys = new byte[payload.size()][];
        for (int i = 0; i < payload.size(); i++) {
            hashKeys[i] = payload.get(i);
        }
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        ot12Sender.init(delta);
        ot3Receiver.init();
        mpOprfReceiver.init(maxClientElementSize);
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuTwoSidedOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        ibltParams = Pt26IbltParams.createDefault(maxServerElementSize, maxClientElementSize, elementByteLength, hashKeys);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        Pt26Iblt iblt1 = Pt26Iblt.encode(clientElementArrayList, ibltParams);

        long pretotalBytes = rpc.getSendByteLength();
        long setupOprfBaseline = pretotalBytes;
        ByteBuffer[] x1List = clientElementArrayList.toArray(new ByteBuffer[0]);
        byte[][] oprfInputs = new byte[x1List.length][];
        for (int idx = 0; idx < x1List.length; idx++) {
            oprfInputs[idx] = Pt26Zm.encodeElement(x1List[idx], ibltParams);
        }
        MpOprfReceiverOutput mpOprfReceiverOutput = mpOprfReceiver.oprf(oprfInputs);
        long setupOprfBytes = rpc.getSendByteLength() - setupOprfBaseline;

        Map<ByteBuffer, byte[]> prfCache = new HashMap<>(x1List.length * 2);
        for (int idx = 0; idx < oprfInputs.length; idx++) {
            prfCache.put(ByteBuffer.wrap(oprfInputs[idx]), mpOprfReceiverOutput.getPrf(idx));
        }

        Set<Pt26BinIndex> queue = Pt26Iblt.allBins(ibltParams);
        Set<ByteBuffer> recovered = new HashSet<>();
        int maxRounds = clientElementSize + serverElementSize + 2;
        long[] counters = new long[Pt26UnionPeel.CH_COUNT];
        Pt26UnionPeel unionPeel = new Pt26UnionPeel(
            false, rpc, getPtoDesc().getPtoId(), extraInfo, encodeTaskId,
            ownParty().getPartyId(), otherParty().getPartyId(), envType, secureRandom, ibltParams,
            ot12Sender, null, null, ot3Receiver,
            null, prfCache,
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
                Map<Pt26BinIndex, byte[]> peeled = unionPeel.run(iblt1, bins, round);
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
            myOwned.retainAll(clientElementSet);
            iblt1.deleteSet(myOwned);
            queue = Pt26Iblt.nextQueue(ibltParams, vRound, iblt1);
        }

        boolean localOk = iblt1.isFullyPeeled();
        exchangePeelStatus(localOk);

        long total = rpc.getSendByteLength() - pretotalBytes;
        logBandwidthBreakdown(setupOprfBytes, counters, total, roundsRun);

        Set<ByteBuffer> union = new HashSet<>(clientElementSet);
        union.addAll(recovered);
        union.remove(botElementByteBuffer);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.PTO_END);
        return new PsuTwoSidedOutput(union);
    }

    private void exchangePeelStatus(boolean localOk) throws MpcAbortException {
        DataPacketHeader clientHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PEEL_STATUS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(
            clientHeader, Collections.singletonList(new byte[]{localOk ? STATUS_OK : STATUS_FAIL})
        ));

        DataPacketHeader serverHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PEEL_STATUS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> serverPayload = rpc.receive(serverHeader).getPayload();
        MpcAbortPreconditions.checkArgument(serverPayload.size() == 1 && serverPayload.get(0).length == 1);
        boolean peerOk = serverPayload.get(0)[0] == STATUS_OK;

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
        info("EUROCRYPT_PisTri26-BW[client] rounds={} bins1={} totalSend={}B  setupOprf={}B  perRoundOprf={}B"
                + "  ot12={}B  ot3={}B  peelReturn={}B  unaccounted(headers+initIO)={}B",
            rounds, bins, totalBytes, setupOprfBytes, perRoundOprf, ot12, ot3, peel, unaccounted);
    }
}
