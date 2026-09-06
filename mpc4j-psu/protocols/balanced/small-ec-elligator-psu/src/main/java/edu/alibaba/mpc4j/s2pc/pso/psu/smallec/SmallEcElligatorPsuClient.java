package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P_R receiver (Figure 8): inputs Y, outputs Y ∪ Y' via H^{-1} only.
 */
public class SmallEcElligatorPsuClient extends AbstractPsuClient implements PsuClient {
    private static final Logger LOG = LoggerFactory.getLogger(SmallEcElligatorPsuClient.class);

    private final SmallEcElligatorPsuConfig config;
    private final ElligatorCodec codec;

    public SmallEcElligatorPsuClient(Rpc clientRpc, Party serverParty, SmallEcElligatorPsuConfig config) {
        super(SmallEcElligatorPsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
        codec = ElligatorCodec.protocolInstance();
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        applyDeduplicatedClientInput(clientElementSet, elementByteLength);
        config.validateElementByteLength(elementByteLength);
        config.validateWCompareForSetSize(serverElementSize);
        SmallEcHashDhCore.assertItemLength(elementByteLength);

        MpcAbortPreconditions.checkArgument(clientElementSize == serverElementSize);
        SmallEcRuntimeConfigLog.logRuntimeConfig(LOG, config, serverElementSize);

        logPhaseInfo(PtoState.PTO_BEGIN);

        long t0 = System.nanoTime();
        byte[] k1 = EcGroupOps.randomNonZeroScalar(secureRandom);
        byte[][] clientItems = itemsFromList();
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "itemsFromList", System.nanoTime() - t0);

        t0 = System.nanoTime();
        CompletableFuture<byte[][]> wFuture = maybeStartWPrecompute(clientItems, k1);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "startWPrecompute", System.nanoTime() - t0);

        stopWatch.start();
        t0 = System.nanoTime();
        byte[][] v = receiveV(serverElementSize);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "receiveV", System.nanoTime() - t0);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv V");
        stopWatch.reset();

        stopWatch.start();
        t0 = System.nanoTime();
        byte[] shuffleSeed = SmallEcPermUtils.sampleSeed(secureRandom);
        int[] permutation = SmallEcPermUtils.generatePermutation(serverElementSize, shuffleSeed);
        byte[][] u = SmallEcHashDhCore.shuffleBlindOptimized(v, k1, permutation, config);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "computeU", System.nanoTime() - t0);
        t0 = System.nanoTime();
        sendU(u);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "sendU", System.nanoTime() - t0);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "send U");
        stopWatch.reset();

        stopWatch.start();
        t0 = System.nanoTime();
        byte[][] w = wFuture.join();
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "waitWFuture", System.nanoTime() - t0);
        sendWAccordingToMode(w, serverElementSize);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 3, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "send W");
        stopWatch.reset();

        stopWatch.start();
        t0 = System.nanoTime();
        List<byte[]> uPrimeDiff = receiveUPrime();
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "receiveUPrime", System.nanoTime() - t0);
        byte[] invK1 = EcGroupOps.invertScalar(k1);
        Set<ByteBuffer> union = new HashSet<>(clientElementArrayList);

        int recovered = 0;
        long decodeStart = System.nanoTime();
        for (byte[] uPrime : uPrimeDiff) {
            byte[] h = EcGroupOps.scalarMul(uPrime, invK1);
            byte[] x = codec.inversePointToItemStrict(h);
            union.add(ByteBuffer.wrap(Arrays.copyOf(x, elementByteLength)));
            recovered++;
        }
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "decodeUPrime", System.nanoTime() - decodeStart);

        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 4, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "decode union");
        stopWatch.reset();

        if (config.isLogStats()) {
            LOG.info("Ours client: |X \\ Y|={}, unionSize={}", recovered, union.size());
        }
        logPhaseInfo(PtoState.PTO_END);

        int psiCa = serverElementSize - recovered;
        return new PsuClientOutput(union, psiCa);
    }

    private CompletableFuture<byte[][]> maybeStartWPrecompute(byte[][] clientItems, byte[] k1) {
        if (!config.useAsyncPrecomputeW(clientItems.length)) {
            return CompletableFuture.completedFuture(
                SmallEcHashDhCore.blindItemsOptimized(clientItems, k1, config, codec)
            );
        }

        return CompletableFuture.supplyAsync(() ->
            SmallEcHashDhCore.blindItemsOptimized(clientItems, k1, config)
        );
    }

    private void sendWAccordingToMode(byte[][] w, int n) {
        if (config.getWCompareMode() == SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT) {
            LOG.info(
                "CLIENT_SEND_W exact: entries={}, entryBytes={}, totalPayloadBytes={}",
                w.length,
                SmallEcConstants.POINT_BYTES,
                w.length * SmallEcConstants.POINT_BYTES
            );
            long t0 = System.nanoTime();
            sendWPoints(w);
            SmallEcRuntimeConfigLog.logTiming(LOG, config, "sendW", System.nanoTime() - t0);
            return;
        }

        int lambda = config.getResolvedFingerprintBitLength(n);
        int fpBytes = SmallEcFingerprintUtils.fingerprintByteLength(lambda);

        long fpStart = System.nanoTime();
        List<byte[]> fingerprints;
        if (lambda == 64) {
            fingerprints = SmallEcFingerprintUtils.fingerprintPoints64(w, config.getFingerprintMethod());
        } else {
            fingerprints = SmallEcFingerprintUtils.fingerprintPoints(
                w,
                lambda,
                config.getFingerprintMethod()
            );
        }
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "fingerprintW", System.nanoTime() - fpStart);

        LOG.info(
            "CLIENT_SEND_W fingerprint: entries={}, entryBytes={}, totalPayloadBytes={}, lambda={}, method={}",
            fingerprints.size(),
            fpBytes,
            fingerprints.size() * fpBytes,
            lambda,
            config.getFingerprintMethod()
        );
        long t0 = System.nanoTime();
        sendWFingerprints(fingerprints, fpBytes);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "sendW", System.nanoTime() - t0);
    }

    private void applyDeduplicatedClientInput(Set<ByteBuffer> clientElementSet, int elementByteLength) {
        clientElementArrayList = SmallEcPermUtils.deduplicateElements(clientElementSet, elementByteLength);
        MathPreconditions.checkGreater("clientElementSize", clientElementArrayList.size(), 1);
        clientElementSize = clientElementArrayList.size();
    }

    private byte[][] receiveV(int n) throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_V.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == n);
        return SmallEcWireUtils.unpackPoints(payload, n);
    }

    private void sendU(byte[][] u) {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_U.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(header, SmallEcWireUtils.packPoints(u)));
    }

    private void sendWPoints(byte[][] w) {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_W.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(header, SmallEcWireUtils.packPoints(w)));
    }

    private void sendWFingerprints(List<byte[]> fingerprints, int fingerprintByteLength) {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_W.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(
            header,
            SmallEcWireUtils.packFingerprints(fingerprints, fingerprintByteLength)
        ));
    }

    private List<byte[]> receiveUPrime() throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_U_PRIME.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        return SmallEcWireUtils.unpackDiffPoints(rpc.receive(header).getPayload());
    }

    private byte[][] itemsFromList() {
        byte[][] items = new byte[clientElementSize][];
        for (int i = 0; i < clientElementSize; i++) {
            items[i] = SmallEcWireUtils.fixedBytes(
                clientElementArrayList.get(i), SmallEcConstants.ITEM_BYTE_LENGTH
            );
        }
        return items;
    }
}
