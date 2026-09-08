package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P_S sender (Figure 8): inputs X, no output.
 */
public class SmallEcElligatorPsuServer extends AbstractPsuServer implements PsuServer {
    private static final Logger LOG = LoggerFactory.getLogger(SmallEcElligatorPsuServer.class);

    private final SmallEcElligatorPsuConfig config;
    private final ElligatorCodec codec;

    public SmallEcElligatorPsuServer(Rpc serverRpc, Party clientParty, SmallEcElligatorPsuConfig config) {
        super(SmallEcElligatorPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        this.config = config;
        codec = ElligatorCodec.protocolInstance();
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        stopWatch.stop();
        logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
        stopWatch.reset();
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        applyDeduplicatedServerInput(serverElementSet, elementByteLength);
        config.validateElementByteLength(elementByteLength);
        config.validateWCompareForSetSize(serverElementSize);
        SmallEcHashDhCore.assertItemLength(elementByteLength);

        MpcAbortPreconditions.checkArgument(serverElementSize == clientElementSize);
        SmallEcRuntimeConfigLog.logRuntimeConfig(LOG, config, serverElementSize);

        logPhaseInfo(PtoState.PTO_BEGIN);

        long t0 = System.nanoTime();
        byte[] k0 = EcGroupOps.randomNonZeroScalar(secureRandom);
        byte[][] serverItems = itemsFromList();
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "itemsFromList", System.nanoTime() - t0);

        stopWatch.start();
        t0 = System.nanoTime();
        byte[][] v = SmallEcHashDhCore.blindItemsOptimized(serverItems, k0, config, codec);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "computeV", System.nanoTime() - t0);
        t0 = System.nanoTime();
        sendV(v);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "sendV", System.nanoTime() - t0);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 1, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "send V");
        stopWatch.reset();

        stopWatch.start();
        t0 = System.nanoTime();
        byte[][] u = receiveU(serverElementSize);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "receiveU", System.nanoTime() - t0);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 2, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv U");
        stopWatch.reset();

        stopWatch.start();
        byte[] invK0 = EcGroupOps.invertScalar(k0);
        List<byte[]> uPrimeDiff;

        if (config.getWCompareMode() == SmallEcElligatorPsuConfig.WCompareMode.FULL_POINT_EXACT) {
            t0 = System.nanoTime();
            byte[][] w = receiveWPoints(serverElementSize);
            SmallEcRuntimeConfigLog.logTiming(LOG, config, "receiveW", System.nanoTime() - t0);
            long keyStart = System.nanoTime();
            Set<EcGroupOps.CanonicalPoint> wKeys = EcGroupOps.canonicalPointKeySet(w);
            SmallEcRuntimeConfigLog.logTiming(LOG, config, "buildWKeySet", System.nanoTime() - keyStart);
            long filterStart = System.nanoTime();
            uPrimeDiff = SmallEcHashDhCore.filterDifferenceOptimized(u, invK0, wKeys, config);
            SmallEcRuntimeConfigLog.logTiming(LOG, config, "filterUPrime", System.nanoTime() - filterStart);
        } else {
            int lambda = config.getResolvedFingerprintBitLength(serverElementSize);
            int fpBytes = SmallEcFingerprintUtils.fingerprintByteLength(lambda);

            t0 = System.nanoTime();
            List<byte[]> wFingerprints = receiveWFingerprints(serverElementSize, fpBytes);
            SmallEcRuntimeConfigLog.logTiming(LOG, config, "receiveW", System.nanoTime() - t0);

            long keyStart = System.nanoTime();
            long filterStart;
            if (lambda == 64) {
                Set<Long> wFpKeys = SmallEcFingerprintUtils.fingerprintKeySet64(wFingerprints, fpBytes);
                SmallEcRuntimeConfigLog.logTiming(LOG, config, "buildWKeySet", System.nanoTime() - keyStart);
                filterStart = System.nanoTime();
                uPrimeDiff = SmallEcHashDhCore.filterDifferenceByFingerprint64Optimized(
                    u, invK0, wFpKeys, config.getFingerprintMethod(), config
                );
            } else {
                Set<FingerprintKey> wFpKeys = SmallEcFingerprintUtils.fingerprintKeySet(wFingerprints, fpBytes);
                SmallEcRuntimeConfigLog.logTiming(LOG, config, "buildWKeySet", System.nanoTime() - keyStart);
                filterStart = System.nanoTime();
                uPrimeDiff = SmallEcHashDhCore.filterDifferenceByFingerprintOptimized(
                    u,
                    invK0,
                    wFpKeys,
                    lambda,
                    config.getFingerprintMethod(),
                    config
                );
            }
            SmallEcRuntimeConfigLog.logTiming(LOG, config, "filterUPrime", System.nanoTime() - filterStart);
        }

        t0 = System.nanoTime();
        sendUPrime(uPrimeDiff);
        SmallEcRuntimeConfigLog.logTiming(LOG, config, "sendUPrime", System.nanoTime() - t0);
        stopWatch.stop();
        logStepInfo(PtoState.PTO_STEP, 3, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "filter and send U'");
        stopWatch.reset();

        if (config.isLogStats()) {
            LOG.info("Ours server: |U'|={}", uPrimeDiff.size());
        }
        logPhaseInfo(PtoState.PTO_END);
    }

    private void applyDeduplicatedServerInput(Set<ByteBuffer> serverElementSet, int elementByteLength) {
        serverElementArrayList = SmallEcPermUtils.deduplicateElements(serverElementSet, elementByteLength);
        MathPreconditions.checkGreater("serverElementSize", serverElementArrayList.size(), 1);
        serverElementSize = serverElementArrayList.size();
    }

    private void sendV(byte[][] v) {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_V.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(header, SmallEcWireUtils.packPoints(v)));
    }

    private byte[][] receiveU(int n) throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_U.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == n);
        return SmallEcWireUtils.unpackPoints(payload, n);
    }

    private byte[][] receiveWPoints(int n) throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_W.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == n);
        LOG.info(
            "SERVER_RECV_W exact: entries={}, expectedEntryBytes={}",
            payload.size(),
            SmallEcConstants.POINT_BYTES
        );
        return SmallEcWireUtils.unpackPoints(payload, n);
    }

    private List<byte[]> receiveWFingerprints(int n, int fingerprintByteLength) throws MpcAbortException {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_W.ordinal(), extraInfo,
            otherParty().getPartyId(), rpc.ownParty().getPartyId()
        );
        List<byte[]> payload = rpc.receive(header).getPayload();
        MpcAbortPreconditions.checkArgument(payload.size() == n);
        LOG.info(
            "SERVER_RECV_W fingerprint: entries={}, entryBytes={}",
            payload.size(),
            fingerprintByteLength
        );
        for (byte[] fp : payload) {
            MpcAbortPreconditions.checkArgument(fp.length == fingerprintByteLength);
        }
        return SmallEcWireUtils.unpackFingerprints(payload, n, fingerprintByteLength);
    }

    private void sendUPrime(List<byte[]> uPrimeDiff) {
        DataPacketHeader header = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_U_PRIME.ordinal(), extraInfo,
            rpc.ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(header, SmallEcWireUtils.packEncodedDiffPoints(uPrimeDiff)));
    }

    private byte[][] itemsFromList() {
        byte[][] items = new byte[serverElementSize][];
        for (int i = 0; i < serverElementSize; i++) {
            items[i] = SmallEcWireUtils.fixedBytes(
                serverElementArrayList.get(i), SmallEcConstants.ITEM_BYTE_LENGTH
            );
        }
        return items;
    }
}
