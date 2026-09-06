package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * EUROCRYPT_PisTri26 PSU client (paper P1, learns union).
 *
 * <p>Per §6 the OPRF is now run <em>once</em> at the start of {@link #psu} on the client's input
 * set {@code X1}, producing a {@code Map<encoded X1 element, F_k(·)>} cache. Each peel round
 * then performs only local {@code H(F_k(sum_{1,i,j}), t, i, j)} hashing — no further OPRF traffic.
 */
public class Pt26PsuClient extends AbstractPsuClient {
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
        // §6 MP-OPRF capacity = client input set size; per-round bin-count sizing is no longer
        // relevant.
        mpOprfReceiver.init(maxClientElementSize);
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        ibltParams = Pt26IbltParams.createDefault(maxServerElementSize, maxClientElementSize, elementByteLength, hashKeys);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        Pt26Iblt iblt1 = Pt26Iblt.encode(clientElementSet, ibltParams);

        // ---- Online-setup MP-OPRF: query F_k on the Z_M-encoded form of every X_1 element. ----
        long pretotalBytes = rpc.getSendByteLength();
        long setupOprfBaseline = pretotalBytes;
        ByteBuffer[] x1List = new ByteBuffer[clientElementSet.size()];
        int x1Idx = 0;
        for (ByteBuffer element : clientElementSet) {
            x1List[x1Idx++] = element;
        }
        byte[][] oprfInputs = new byte[x1List.length][];
        for (int idx = 0; idx < x1List.length; idx++) {
            oprfInputs[idx] = Pt26Zm.encodeElement(x1List[idx], ibltParams);
        }
        MpOprfReceiverOutput mpOprfReceiverOutput = mpOprfReceiver.oprf(oprfInputs);
        long setupOprfBytes = rpc.getSendByteLength() - setupOprfBaseline;

        // Cache: enc(x) -> F_k(enc(x)). Singleton bins (cnt_{1,i,j} = 1) hold sum = enc(x) for
        // some x ∈ X_1, so this lookup table is enough.
        Map<ByteBuffer, byte[]> prfCache = new HashMap<ByteBuffer, byte[]>(x1List.length * 2);
        for (int idx = 0; idx < oprfInputs.length; idx++) {
            prfCache.put(ByteBuffer.wrap(oprfInputs[idx]), mpOprfReceiverOutput.getPrf(idx));
        }

        // Match the EUROCRYPT_PisTri26/reference-code first round: probe every IBLT bin under UnionPeel. Later
        // rounds only revisit hash locations touched by the elements peeled in the previous round.
        Set<Pt26BinIndex> queue = Pt26Iblt.allBins(ibltParams);
        Set<ByteBuffer> recovered = new HashSet<ByteBuffer>();
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
            Set<ByteBuffer> vRound = new HashSet<ByteBuffer>();
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
            // Paper Π_PSU: each party deletes only V ∩ X_b from their own IBLT (see server-side
            // comment for the corruption story); the bin queue for the next round is still
            // derived from the *full* V so both sides revisit the same bins. The union output
            // still uses {clientSet} ∪ recovered, which is unchanged.
            Set<ByteBuffer> myOwned = new HashSet<ByteBuffer>(vRound);
            myOwned.retainAll(clientElementSet);
            iblt1.deleteSet(myOwned);
            queue = Pt26Iblt.nextQueue(ibltParams, vRound, iblt1);
        }
        long total = rpc.getSendByteLength() - pretotalBytes;
        logBandwidthBreakdown(setupOprfBytes, counters, total, roundsRun);

        Set<ByteBuffer> union = new HashSet<ByteBuffer>(clientElementSet);
        union.addAll(recovered);
        union.remove(botElementByteBuffer);
        int psica = serverElementSize + clientElementSize - union.size();
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }

    /**
     * Emits the six EUROCRYPT_PisTri26 communication counters from the client's perspective.
     * @see Pt26PsuServer for the channel definitions; the only difference here is that
     * {@code peel-return} is the client <em>sending</em> direction (whereas for the server it is
     * receive-only and therefore zero-bytes-sent).
     */
    private void logBandwidthBreakdown(long setupOprfBytes, long[] counters, long totalBytes, int rounds) {
        long ot12 = counters[Pt26UnionPeel.CH_OT12];
        long ot3 = counters[Pt26UnionPeel.CH_OT3];
        long peel = counters[Pt26UnionPeel.CH_PEEL];
        long perRoundOprf = counters[Pt26UnionPeel.CH_OPRF_PER_ROUND];
        long unaccounted = totalBytes - setupOprfBytes - ot12 - ot3 - peel - perRoundOprf;
        long bins = (long) ibltParams.totalBins();
        // Inherited from AbstractMultiPartyPto; renders the slf4j format with the supplied args.
        info("EUROCRYPT_PisTri26-BW[client] rounds={} bins1={} totalSend={}B  setupOprf={}B  perRoundOprf={}B"
            + "  ot12={}B  ot3={}B  peelReturn={}B  unaccounted(headers+initIO)={}B",
            rounds, bins, totalBytes, setupOprfBytes, perRoundOprf, ot12, ot3, peel, unaccounted);
    }
}
