package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * RPC payload packing for Figure 8 (curve points only; no permutation or plaintext items).
 */
final class SmallEcWireUtils {
    private SmallEcWireUtils() {
        // empty
    }

    static byte[] fixedBytes(ByteBuffer buffer, int expectedLength) {
        ByteBuffer dup = buffer.asReadOnlyBuffer();
        byte[] out = new byte[dup.remaining()];
        dup.get(out);
        Preconditions.checkArgument(out.length == expectedLength);
        return out;
    }

    static List<byte[]> packPoints(byte[][] points) {
        List<byte[]> payload = new ArrayList<>(points.length);
        for (byte[] p : points) {
            Preconditions.checkArgument(p.length == SmallEcConstants.POINT_BYTES);
            payload.add(EcGroupOps.encodePoint(p));
        }
        return payload;
    }

    static byte[][] unpackPoints(List<byte[]> payload, int expected) {
        if (expected >= 0 && payload.size() != expected) {
            throw new IllegalArgumentException("invalid point payload size");
        }
        byte[][] points = new byte[payload.size()][];
        for (int i = 0; i < payload.size(); i++) {
            points[i] = EcGroupOps.decodePoint(payload.get(i));
        }
        return points;
    }

    static List<byte[]> packDiffPoints(List<byte[]> uPrimes) {
        List<byte[]> payload = new ArrayList<>(uPrimes.size());
        for (byte[] p : uPrimes) {
            Preconditions.checkArgument(p.length == SmallEcConstants.POINT_BYTES);
            payload.add(EcGroupOps.encodePoint(p));
        }
        return payload;
    }

    static List<byte[]> packEncodedDiffPoints(List<byte[]> uPrimes) {
        List<byte[]> payload = new ArrayList<>(uPrimes.size());
        for (byte[] p : uPrimes) {
            Preconditions.checkArgument(p.length == SmallEcConstants.POINT_BYTES);
            payload.add(p.clone());
        }
        return payload;
    }

    static List<byte[]> unpackDiffPoints(List<byte[]> payload) {
        List<byte[]> out = new ArrayList<>(payload.size());
        for (byte[] p : payload) {
            out.add(EcGroupOps.decodePoint(p));
        }
        return out;
    }

    static List<byte[]> packFingerprints(List<byte[]> fingerprints, int fingerprintByteLength) {
        List<byte[]> payload = new ArrayList<>(fingerprints.size());
        for (byte[] fp : fingerprints) {
            Preconditions.checkArgument(fp.length == fingerprintByteLength);
            payload.add(fp.clone());
        }
        return payload;
    }

    static List<byte[]> unpackFingerprints(List<byte[]> payload, int expected, int fingerprintByteLength) {
        if (expected >= 0 && payload.size() != expected) {
            throw new IllegalArgumentException("invalid fingerprint payload size");
        }

        List<byte[]> out = new ArrayList<>(payload.size());
        for (byte[] fp : payload) {
            Preconditions.checkArgument(fp.length == fingerprintByteLength);
            out.add(fp.clone());
        }

        return out;
    }
}
