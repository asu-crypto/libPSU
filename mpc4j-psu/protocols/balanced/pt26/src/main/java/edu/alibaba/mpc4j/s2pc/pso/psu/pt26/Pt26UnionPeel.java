package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory.HashType;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Secure UnionPeel batch on bins {@code Q} (§5, Fig. 4), optimized per §6.
 *
 * <p>Paper Figure 4 Step 1: P0 chooses {@code c = [cnt0 == 0]}; P1 sends {@code m0 = ⊥} and
 * {@code m1 = sum1} iff {@code cnt1 == 1} else ⊥. {@link Pt26OtUtils#receiverDecrypt} returns
 * {@code m1} when the choice bit is {@code true}.
 *
 * <p>OT / peel payloads use tagged {@link Pt26Zm} wire values ({@code BOT} vs canonical {@code Z_M}).
 */
public class Pt26UnionPeel {
    private final boolean serverSide;
    private final Rpc rpc;
    private final int ptoId;
    private final long extraInfo;
    private final long encodeTaskId;
    private final int ownPartyId;
    private final int otherPartyId;
    private final EnvType envType;
    private final SecureRandom secureRandom;
    private final Pt26IbltParams params;
    private final int zmByteLength;
    private final int messageByteLength;
    private final CoreCotSender ot12Sender;
    private final CoreCotReceiver ot12Receiver;
    private final CoreCotSender ot3Sender;
    private final CoreCotReceiver ot3Receiver;
    private final byte[] botMessage;
    private final Hash equalityHash;
    private final MpOprfSenderOutput mpOprfSenderOutput;
    private final Map<ByteBuffer, byte[]> clientPrfCache;
    private final long[] commCounters;
    public static final int CH_OT12 = 0;
    public static final int CH_OT3 = 1;
    public static final int CH_PEEL = 2;
    public static final int CH_OPRF_PER_ROUND = 3;
    public static final int CH_COUNT = 4;

    public Pt26UnionPeel(
        boolean serverSide, Rpc rpc, int ptoId, long extraInfo, long encodeTaskId, int ownPartyId, int otherPartyId,
        EnvType envType, SecureRandom secureRandom, Pt26IbltParams params,
        CoreCotSender ot12Sender, CoreCotReceiver ot12Receiver, CoreCotSender ot3Sender, CoreCotReceiver ot3Receiver,
        MpOprfSenderOutput mpOprfSenderOutput, Map<ByteBuffer, byte[]> clientPrfCache,
        long[] commCounters
    ) {
        this.serverSide = serverSide;
        this.rpc = rpc;
        this.ptoId = ptoId;
        this.extraInfo = extraInfo;
        this.encodeTaskId = encodeTaskId;
        this.ownPartyId = ownPartyId;
        this.otherPartyId = otherPartyId;
        this.envType = envType;
        this.secureRandom = secureRandom;
        this.params = params;
        this.zmByteLength = params.getZmByteLength();
        this.messageByteLength = Pt26Zm.wireByteLength(params);
        this.ot12Sender = ot12Sender;
        this.ot12Receiver = ot12Receiver;
        this.ot3Sender = ot3Sender;
        this.ot3Receiver = ot3Receiver;
        this.mpOprfSenderOutput = mpOprfSenderOutput;
        this.clientPrfCache = clientPrfCache;
        this.equalityHash = HashFactory.createInstance(HashType.JDK_SHA256, zmByteLength);
        this.botMessage = Pt26Zm.encodeWireBot(params);
        this.commCounters = commCounters;
    }

    /**
     * Runs one UnionPeel round; returns peeled Z_M values keyed by bin ({@code null} = ⊥).
     */
    public Map<Pt26BinIndex, byte[]> run(Pt26Iblt localIblt, List<Pt26BinIndex> bins, int round)
        throws MpcAbortException {
        if (bins.isEmpty()) {
            return Map.of();
        }
        if (serverSide) {
            return runServer(localIblt, bins, round);
        } else {
            return runClient(localIblt, bins, round);
        }
    }

    private Map<Pt26BinIndex, byte[]> runServer(Pt26Iblt iblt0, List<Pt26BinIndex> bins, int round)
        throws MpcAbortException {
        int n = bins.size();
        long ot12Baseline = rpcSentBytes();
        boolean[] choices = new boolean[n];
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            choices[t] = iblt0.getCnt(bin.getI(), bin.getJ()) == 0;
        }
        DataPacketHeader ot12Header = otHeader(PtoStep.CLIENT_SEND_OT12_PAYLOAD, otherPartyId, ownPartyId);
        CotReceiverOutput ot12CotOut = ot12Receiver.receive(choices);
        List<byte[]> ot12Payload = rpc.receive(ot12Header).getPayload();
        MpcAbortPreconditions.checkArgument(ot12Payload.size() == n);
        List<byte[]> fWire = Pt26OtUtils.receiverDecrypt(ot12CotOut, ot12Payload, messageByteLength, envType);
        accumulate(CH_OT12, rpcSentBytes() - ot12Baseline);

        List<byte[]> w0 = new ArrayList<>(n);
        List<byte[]> w1 = new ArrayList<>(n);
        List<byte[]> w2 = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int i = bin.getI();
            int j = bin.getJ();
            int c0 = iblt0.getCnt(i, j);
            byte[] sum0 = iblt0.getSum(i, j);
            Optional<byte[]> fOpt = Pt26Zm.decodeWire(fWire.get(t), params);
            byte[] u;
            if (c0 == 1) {
                u = equalityTag(mpOprfSenderOutput.getPrf(sum0), round, i, j);
            } else if (fOpt.isPresent()) {
                u = equalityTag(mpOprfSenderOutput.getPrf(fOpt.get()), round, i, j);
            } else {
                byte[] randomTag = new byte[zmByteLength];
                secureRandom.nextBytes(randomTag);
                u = encodeTagWire(randomTag);
            }
            if (c0 == 1) {
                w0.add(Pt26Zm.encodeWireValue(sum0, params));
            } else {
                w0.add(botBytes());
            }
            w1.add(u);
            w2.add(botBytes());
        }

        long ot3Baseline = rpcSentBytes();
        List<byte[]> ot3Payload = Pt26OtUtils.runSenderOneOfThree(
            ot3Sender, w0, w1, w2, messageByteLength, envType, secureRandom
        );
        DataPacketHeader ot3Header = otHeader(PtoStep.SERVER_SEND_OT3_PAYLOAD, ownPartyId, otherPartyId);
        rpc.send(DataPacket.fromByteArrayList(ot3Header, ot3Payload));
        accumulate(CH_OT3, rpcSentBytes() - ot3Baseline);

        long peelBaseline = rpcSentBytes();
        DataPacketHeader peelHeader = otHeader(PtoStep.CLIENT_SEND_PEEL_VALUES, otherPartyId, ownPartyId);
        List<byte[]> peelPayload = rpc.receive(peelHeader).getPayload();
        MpcAbortPreconditions.checkArgument(peelPayload.size() == n);
        accumulate(CH_PEEL, rpcSentBytes() - peelBaseline);
        return decodePeelMap(bins, peelPayload);
    }

    private Map<Pt26BinIndex, byte[]> runClient(Pt26Iblt iblt1, List<Pt26BinIndex> bins, int round)
        throws MpcAbortException {
        int n = bins.size();

        // Fig. 4 Step 1: m0 = BOT; m1 = sum1 if cnt1 == 1 else BOT.
        long ot12Baseline = rpcSentBytes();
        List<byte[]> m0 = new ArrayList<>(n);
        List<byte[]> m1 = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int i = bin.getI();
            int j = bin.getJ();
            int c1 = iblt1.getCnt(i, j);
            m0.add(botBytes());
            m1.add(c1 == 1 ? Pt26Zm.encodeWireValue(iblt1.getSum(i, j), params) : botBytes());
        }
        List<byte[]> ot12Payload = Pt26OtUtils.runSenderOneOfTwo(ot12Sender, m0, m1, messageByteLength, envType);
        DataPacketHeader ot12Header = otHeader(PtoStep.CLIENT_SEND_OT12_PAYLOAD, ownPartyId, otherPartyId);
        rpc.send(DataPacket.fromByteArrayList(ot12Header, ot12Payload));
        accumulate(CH_OT12, rpcSentBytes() - ot12Baseline);

        byte[][] yValues = new byte[n][];
        int[] dChoices = new int[n];
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int c1 = iblt1.getCnt(bin.getI(), bin.getJ());
            if (c1 == 0) {
                dChoices[t] = 0;
            } else if (c1 == 1) {
                dChoices[t] = 1;
                byte[] sum1 = iblt1.getSum(bin.getI(), bin.getJ());
                byte[] fk = clientPrfCache.get(ByteBuffer.wrap(sum1));
                MpcAbortPreconditions.checkArgument(fk != null,
                    "Missing cached PRF evaluation for singleton bin (round=" + round + ")");
                yValues[t] = equalityTag(fk, round, bin.getI(), bin.getJ());
            } else {
                dChoices[t] = 2;
            }
        }

        long ot3Baseline = rpcSentBytes();
        List<byte[]> gValues = runReceiverOneOfThree(ot3Receiver, dChoices, messageByteLength);
        accumulate(CH_OT3, rpcSentBytes() - ot3Baseline);

        long peelBaseline = rpcSentBytes();
        List<byte[]> peelPayload = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int i = bin.getI();
            int j = bin.getJ();
            byte[] g = gValues.get(t);
            byte[] v;
            if (dChoices[t] == 1) {
                v = BytesUtils.equals(g, yValues[t])
                    ? Pt26Zm.encodeWireValue(iblt1.getSum(i, j), params)
                    : botBytes();
            } else if (dChoices[t] == 0) {
                v = isBot(g) ? botBytes() : g;
            } else {
                v = botBytes();
            }
            peelPayload.add(v);
        }
        DataPacketHeader peelHeader = otHeader(PtoStep.CLIENT_SEND_PEEL_VALUES, ownPartyId, otherPartyId);
        rpc.send(DataPacket.fromByteArrayList(peelHeader, peelPayload));
        accumulate(CH_PEEL, rpcSentBytes() - peelBaseline);
        return decodePeelMap(bins, peelPayload);
    }

    private Map<Pt26BinIndex, byte[]> decodePeelMap(List<Pt26BinIndex> bins, List<byte[]> peelPayload)
        throws MpcAbortException {
        Map<Pt26BinIndex, byte[]> map = new HashMap<>();
        for (int t = 0; t < bins.size(); t++) {
            try {
                Optional<byte[]> decoded = Pt26Zm.decodeWire(peelPayload.get(t), params);
                map.put(bins.get(t), decoded.orElse(null));
            } catch (IllegalArgumentException e) {
                throw new MpcAbortException("malformed peel wire value: " + e.getMessage());
            }
        }
        return map;
    }

    private List<byte[]> runReceiverOneOfThree(CoreCotReceiver receiver, int[] choices, int messageByteLength)
        throws MpcAbortException {
        int n = choices.length;
        int pairLen = messageByteLength * 2;
        DataPacketHeader ot3Header = otHeader(PtoStep.SERVER_SEND_OT3_PAYLOAD, otherPartyId, ownPartyId);
        boolean[] c0 = new boolean[n];
        boolean[] c1 = new boolean[n];
        boolean[] c2 = new boolean[n];
        for (int i = 0; i < n; i++) {
            c0[i] = choices[i] != 0;
            c1[i] = choices[i] != 1;
            c2[i] = choices[i] != 2;
        }
        CotReceiverOutput ot3CotOut0 = receiver.receive(c0);
        CotReceiverOutput ot3CotOut1 = receiver.receive(c1);
        CotReceiverOutput ot3CotOut2 = receiver.receive(c2);
        List<byte[]> mergedPayload = rpc.receive(ot3Header).getPayload();
        MpcAbortPreconditions.checkArgument(mergedPayload.size() == n);
        List<byte[]> payload0 = new ArrayList<>(n);
        List<byte[]> payload1 = new ArrayList<>(n);
        List<byte[]> payload2 = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byte[] merged = mergedPayload.get(i);
            MpcAbortPreconditions.checkArgument(merged.length == pairLen * 3);
            payload0.add(BytesUtils.clone(merged, 0, pairLen));
            payload1.add(BytesUtils.clone(merged, pairLen, pairLen));
            payload2.add(BytesUtils.clone(merged, pairLen * 2, pairLen));
        }
        List<byte[]> dec0 = Pt26OtUtils.receiverDecrypt(ot3CotOut0, payload0, messageByteLength, envType);
        List<byte[]> dec1 = Pt26OtUtils.receiverDecrypt(ot3CotOut1, payload1, messageByteLength, envType);
        List<byte[]> dec2 = Pt26OtUtils.receiverDecrypt(ot3CotOut2, payload2, messageByteLength, envType);
        List<byte[]> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (choices[i] == 0) {
                out.add(dec0.get(i));
            } else if (choices[i] == 1) {
                out.add(dec1.get(i));
            } else {
                out.add(dec2.get(i));
            }
        }
        return out;
    }

    private byte[] equalityTag(byte[] fk, int round, int binI, int binJ) {
        byte[] message = new byte[fk.length + Integer.BYTES * 3];
        System.arraycopy(fk, 0, message, 0, fk.length);
        ByteBuffer.wrap(message, fk.length, Integer.BYTES * 3)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(round)
            .putInt(binI)
            .putInt(binJ);
        return encodeTagWire(equalityHash.digestToBytes(message));
    }

    private byte[] encodeTagWire(byte[] tagZm) {
        return Pt26Zm.encodeWireValue(tagZm, params);
    }

    private byte[] botBytes() {
        return botMessage.clone();
    }

    private boolean isBot(byte[] value) {
        try {
            return Pt26Zm.isWireBot(value, params);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private DataPacketHeader otHeader(PtoStep step, int from, int to) {
        return new DataPacketHeader(encodeTaskId, ptoId, step.ordinal(), extraInfo, from, to);
    }

    private long rpcSentBytes() {
        return rpc.getSendByteLength();
    }

    private void accumulate(int channel, long bytes) {
        if (commCounters != null && bytes > 0) {
            commCounters[channel] += bytes;
        }
    }
}
