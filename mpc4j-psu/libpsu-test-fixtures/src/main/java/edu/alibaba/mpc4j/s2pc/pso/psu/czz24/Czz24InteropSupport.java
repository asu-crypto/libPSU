package edu.alibaba.mpc4j.s2pc.pso.psu.czz24;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic vectors and wire-dump helpers for CZZ24 Kunlun interop tests.
 */
public final class Czz24InteropSupport {
    /** Documented server RNG seed for reproducible α and server shuffle. */
    static final long SERVER_RNG_SEED = 0x435A5A3234534C01L;
    /** Documented client RNG seed for reproducible β and COT randomness. */
    static final long CLIENT_RNG_SEED = 0x435A5A3234434C01L;
    /** Fixed task id so sub-protocol headers are stable across runs (must be non-negative). */
    static final int FIXED_TASK_ID = 0x4224C0DE;
    /** 8-byte big-endian item encoding (mpc4j requires elementByteLength ≥ 5). */
    static final int ELEMENT_BYTE_LENGTH = 8;
    /** Server set X = {0, 1, 2, 3}. */
    static final int[] SERVER_VALUES = {0, 1, 2, 3};
    /** Client set Y = {1, 2, 5, 7}. */
    static final int[] CLIENT_VALUES = {1, 2, 5, 7};
    /** Expected |X ∩ Y|. */
    static final int EXPECTED_INTERSECTION_SIZE = 2;
    /** Expected |X ∪ Y|. */
    static final int EXPECTED_UNION_SIZE = 6;

    private Czz24InteropSupport() {
        // empty
    }

    static SecureRandom deterministicRandom(long seed) {
        try {
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(seed).array());
            return random;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Set<ByteBuffer> serverSet() {
        return valuesToSet(SERVER_VALUES);
    }

    static Set<ByteBuffer> clientSet() {
        return valuesToSet(CLIENT_VALUES);
    }

    static Set<ByteBuffer> expectedUnion() {
        Set<ByteBuffer> union = new LinkedHashSet<>();
        union.addAll(serverSet());
        union.addAll(clientSet());
        return union;
    }

    static Set<ByteBuffer> valuesToSet(int[] values) {
        return Arrays.stream(values)
            .mapToObj(Czz24InteropSupport::element)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static ByteBuffer element(int value) {
        byte[] bytes = new byte[ELEMENT_BYTE_LENGTH];
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putLong(value);
        return ByteBuffer.wrap(bytes);
    }

    static String bytesToHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static String summarizeWireCaptures(List<RecordingRpc.CapturedPacket> packets) {
        StringBuilder sb = new StringBuilder();
        for (RecordingRpc.CapturedPacket packet : packets) {
            sb.append(packet.partyName)
                .append(" ptoId=").append(packet.ptoId)
                .append(" step=").append(packet.stepId)
                .append(" payloadCount=").append(packet.payload.size());
            for (int i = 0; i < packet.payload.size(); i++) {
                sb.append("\n  [").append(i).append("] ").append(bytesToHex(packet.payload.get(i)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
