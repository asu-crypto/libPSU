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

/**
 * Secure UnionPeel batch on bins {@code Q} (§5, Fig. 4), optimized per §6.
 *
 * <p>The per-round OPRF (the old {@code oprfSender.oprf(n)} / {@code oprfReceiver.oprf(perBinSums)})
 * has been removed: the protocol now runs <em>one</em> multi-point OPRF during the online setup on
 * the client's input set {@code X1}; the server holds the PRF key (via
 * {@link MpOprfSenderOutput}) and the client holds a precomputed map
 * {@code encoded sum_{1,i,j} -> F_k(sum_{1,i,j})}. Each round then computes the equality material
 * via {@code H(F_k(z), round, i, j)} with the bin index and round index as domain separation
 * (paper Eq. for {@code u_{i,j}} in §6 Optimization, with {@code H} a random oracle).
 *
 * <p>The equality tag is computed at length {@code messageByteLength = zmByteLength} (e.g., 9 bytes
 * for 8-byte set elements) so it fits the OT slot 1 of the 1-of-3 OT without padding. This is the
 * <em>de facto</em> tag length used by the pre-#1 implementation (which silently truncated the
 * OPRF output to {@code messageByteLength} via {@code BytesUtils.xori} with disabled assertions);
 * the only difference here is that the tag is now an explicit {@code SHA-256 / messageByteLength}
 * digest rather than an arbitrary 9-byte prefix of an OPRF codeword, so the equality check
 * actually returns {@code true} when {@code sum_{0,i,j} = sum_{1,i,j}}. The paper's λ=40 tag
 * truncation is <em>not</em> applied here — see the user-facing change log.
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
    private final int messageByteLength;
    private final CoreCotSender ot12Sender;
    private final CoreCotReceiver ot12Receiver;
    private final CoreCotSender ot3Sender;
    private final CoreCotReceiver ot3Receiver;
    private final byte[] botMessage;
    private final Hash equalityHash;
    /**
     * Server-only: cached MP-OPRF sender output (single PRF key over the client's input set).
     */
    private final MpOprfSenderOutput mpOprfSenderOutput;
    /**
     * Client-only: cached map from a Z_M-encoded element {@code enc(x)} (which is also the value
     * that lands in {@code sum_{1,i,j}} when only {@code x} occupies bin {@code (i,j)}) to its PRF
     * evaluation {@code F_k(enc(x))} as obtained from the online-setup MP-OPRF.
     */
    private final Map<ByteBuffer, byte[]> clientPrfCache;
    /**
     * Per-channel send-byte counters, owned by the caller and updated in place. {@code null} means
     * "do not meter". Index constants are below.
     */
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
        long[] commCounters) {
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
        this.messageByteLength = params.getZmByteLength();
        this.ot12Sender = ot12Sender;
        this.ot12Receiver = ot12Receiver;
        this.ot3Sender = ot3Sender;
        this.ot3Receiver = ot3Receiver;
        this.mpOprfSenderOutput = mpOprfSenderOutput;
        this.clientPrfCache = clientPrfCache;
        this.equalityHash = HashFactory.createInstance(HashType.JDK_SHA256, this.messageByteLength);
        botMessage = new byte[messageByteLength];
        java.util.Arrays.fill(botMessage, (byte) 0xFF);
        this.commCounters = commCounters;
    }

    /**
     * Runs one UnionPeel round; returns peeled Z_M values keyed by bin ({@code null} = ⊥).
     * @param round 0-based round index {@code t} used for the {@code (F_k(·), t, i, j)} domain
     *              separation in the equality hash.
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
        // ---- Step 1: 1-of-2 OT receiver (P0 gets f_{i,j} = sum_{1,i,j} iff cnt_{0,i,j}=0 ∧ cnt_{1,i,j}=1) ----
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
        List<byte[]> fValues = Pt26OtUtils.receiverDecrypt(ot12CotOut, ot12Payload, messageByteLength, envType);
        accumulate(CH_OT12, rpcSentBytes() - ot12Baseline);

        // ---- Step 2: REMOVED (no per-round OPRF). PRF eval is local via cached mpOprfSenderOutput. ----

        // ---- Step 3: Local computation of (w_{i,j,0}, w_{i,j,1}, w_{i,j,2}) per bin. ----
        List<byte[]> w0 = new ArrayList<>(n);
        List<byte[]> w1 = new ArrayList<>(n);
        List<byte[]> w2 = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int i = bin.getI();
            int j = bin.getJ();
            int c0 = iblt0.getCnt(i, j);
            byte[] sum0 = iblt0.getSum(i, j);
            byte[] f = fValues.get(t);
            byte[] u;
            if (c0 == 1) {
                // Paper Eq. for u_{i,j}: H(F_k(sum_{0,i,j}), t, i, j)
                u = equalityTag(mpOprfSenderOutput.getPrf(sum0), round, i, j);
            } else if (!isBot(f)) {
                // c0=0 ∧ f = sum_{1,i,j} known: H(F_k(sum_{1,i,j}), t, i, j)
                u = equalityTag(mpOprfSenderOutput.getPrf(f), round, i, j);
            } else {
                // c0>1 ∨ f=⊥: random tag (paper's "r")
                u = new byte[messageByteLength];
                secureRandom.nextBytes(u);
            }
            if (c0 == 1) {
                w0.add(sum0);
            } else {
                w0.add(botBytes());
            }
            w1.add(u);
            w2.add(botBytes());
        }

        // ---- Step 4: 1-of-3 OT sender (emulated by 3× 1-of-2 OT in Pt26OtUtils) ----
        long ot3Baseline = rpcSentBytes();
        List<byte[]> ot3Payload = Pt26OtUtils.runSenderOneOfThree(
            ot3Sender, w0, w1, w2, messageByteLength, envType);
        DataPacketHeader ot3Header = otHeader(PtoStep.SERVER_SEND_OT3_PAYLOAD, ownPartyId, otherPartyId);
        rpc.send(DataPacket.fromByteArrayList(ot3Header, ot3Payload));
        accumulate(CH_OT3, rpcSentBytes() - ot3Baseline);

        // ---- Step 5: receive peel values from P1 ----
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

        // ---- Step 1: 1-of-2 OT sender (P1 sends m_{i,j}; P0 chooses by [cnt_{0,i,j}=0]) ----
        long ot12Baseline = rpcSentBytes();
        List<byte[]> m0 = new ArrayList<>(n);
        List<byte[]> m1 = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int i = bin.getI();
            int j = bin.getJ();
            int c1 = iblt1.getCnt(i, j);
            if (c1 == 1) {
                m0.add(iblt1.getSum(i, j));
            } else {
                m0.add(botBytes());
            }
            m1.add(botBytes());
        }
        List<byte[]> ot12Payload = Pt26OtUtils.runSenderOneOfTwo(ot12Sender, m0, m1, messageByteLength, envType);
        DataPacketHeader ot12Header = otHeader(PtoStep.CLIENT_SEND_OT12_PAYLOAD, ownPartyId, otherPartyId);
        rpc.send(DataPacket.fromByteArrayList(ot12Header, ot12Payload));
        accumulate(CH_OT12, rpcSentBytes() - ot12Baseline);

        // ---- Step 2: REMOVED (no per-round OPRF). y_{i,j} is computed locally from cache. ----
        // P1 derives y_{i,j} = H(F_k(sum_{1,i,j}), t, i, j) for c1=1 bins (the only ones where it
        // needs to compare against g_{i,j}). For c1 ∈ {0,>1} bins, no equality is performed so
        // y is unused; we leave the slot as null.
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
                // Singleton bin ⇒ sum_{1,i,j} is an element of X_1, which was OPRF'd in setup.
                MpcAbortPreconditions.checkArgument(fk != null,
                    "Missing cached PRF evaluation for singleton bin (round=" + round + ")");
                yValues[t] = equalityTag(fk, round, bin.getI(), bin.getJ());
            } else {
                dChoices[t] = 2;
            }
        }

        // ---- Step 4: 1-of-3 OT receiver ----
        long ot3Baseline = rpcSentBytes();
        List<byte[]> gValues = runReceiverOneOfThree(ot3Receiver, dChoices, messageByteLength);
        accumulate(CH_OT3, rpcSentBytes() - ot3Baseline);

        // ---- Step 5: local v_{i,j} + return peel set V to P0 ----
        long peelBaseline = rpcSentBytes();
        List<byte[]> peelPayload = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = bins.get(t);
            int i = bin.getI();
            int j = bin.getJ();
            byte[] g = gValues.get(t);
            byte[] v;
            if (dChoices[t] == 1) {
                v = BytesUtils.equals(g, yValues[t]) ? iblt1.getSum(i, j) : botBytes();
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

    private Map<Pt26BinIndex, byte[]> decodePeelMap(List<Pt26BinIndex> bins, List<byte[]> peelPayload) {
        Map<Pt26BinIndex, byte[]> map = new HashMap<>();
        for (int t = 0; t < bins.size(); t++) {
            byte[] v = peelPayload.get(t);
            map.put(bins.get(t), isBot(v) ? null : v);
        }
        return map;
    }

    /**
     * 1-of-3 OT receiver step. The 1-of-3 OT is still emulated by three parallel 1-of-2 OT
     * instances (consistent with the previous implementation; this commit does <em>not</em>
     * change the OT arity per the user's instructions). The OT extension and masked-message bytes
     * both flow through the parent {@link Rpc}, so the surrounding {@code rpc.getSendByteLength()}
     * delta in the caller correctly accounts for this channel's bandwidth.
     */
    private List<byte[]> runReceiverOneOfThree(CoreCotReceiver receiver, int[] choices, int messageByteLength)
        throws MpcAbortException {
        int n = choices.length;
        int pairLen = messageByteLength * 2;
        DataPacketHeader ot3Header = otHeader(PtoStep.SERVER_SEND_OT3_PAYLOAD, otherPartyId, ownPartyId);
        // The 1-of-3 OT is built by stacking three 1-of-2 OTs, each carrying (slot0 = w_j,
        // slot1 = random). To learn w_j the inner OT's choice bit must be `false` (slot 0); to
        // discard the j-th instance the choice bit can be `true` (slot 1, random decoy). This
        // direction was inverted in the original Pt26OtUtils emulation (a pre-#1 correctness bug
        // hidden by benchmarks that don't validate output); fixed here.
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

    /**
     * {@code H(F_k(z), round, binI, binJ)} — random-oracle equality tag with explicit bin- and
     * round-index domain separation (paper §6 Optimization). Output length is
     * {@code messageByteLength} (the OT slot 1 byte length); this matches the de facto tag length
     * of the pre-#1 implementation and is well above the {@code λ = 40} statistical floor.
     */
    private byte[] equalityTag(byte[] fk, int round, int binI, int binJ) {
        byte[] message = new byte[fk.length + Integer.BYTES * 3];
        System.arraycopy(fk, 0, message, 0, fk.length);
        ByteBuffer.wrap(message, fk.length, Integer.BYTES * 3)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(round)
            .putInt(binI)
            .putInt(binJ);
        return equalityHash.digestToBytes(message);
    }

    private byte[] botBytes() {
        return botMessage;
    }

    private boolean isBot(byte[] value) {
        return BytesUtils.equals(value, botBytes());
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
