package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

/**
 * Truncated fingerprint helpers for optional {@link SmallEcElligatorPsuConfig.WCompareMode#TRUNCATED_W_PROBABILISTIC}.
 */
final class SmallEcFingerprintUtils {
    private SmallEcFingerprintUtils() {
        // empty
    }

    static int autoFingerprintBitLength(int n, int statisticalSecurityBits) {
        return SmallEcElligatorPsuConfig.autoFingerprintBitLength(n, statisticalSecurityBits);
    }

    static int fingerprintByteLength(int fingerprintBitLength) {
        if (fingerprintBitLength == 64) {
            return 8;
        } else if (fingerprintBitLength == 96) {
            return 12;
        } else if (fingerprintBitLength == 128) {
            return 16;
        }
        throw new IllegalArgumentException("fingerprintBitLength must be 64, 96, or 128");
    }

    static byte[] fingerprint(
        byte[] point,
        int fingerprintBitLength,
        SmallEcElligatorPsuConfig.FingerprintMethod method
    ) {
        byte[] encoded = EcGroupOps.encodePoint(point);
        return fingerprintEncodedPoint(encoded, fingerprintBitLength, method);
    }

    private static byte[] fingerprintEncodedPoint(
        byte[] encoded,
        int fingerprintBitLength,
        SmallEcElligatorPsuConfig.FingerprintMethod method
    ) {
        Preconditions.checkArgument(encoded.length == SmallEcConstants.POINT_BYTES);
        int fpBytes = fingerprintByteLength(fingerprintBitLength);

        if (method == SmallEcElligatorPsuConfig.FingerprintMethod.CANONICAL_POINT_PREFIX) {
            return Arrays.copyOf(encoded, fpBytes);
        }

        if (method == SmallEcElligatorPsuConfig.FingerprintMethod.HASH_THEN_TRUNCATE) {
            Blake2s256 hasher = new Blake2s256();
            hasher.update(encoded);
            return Arrays.copyOf(hasher.digest(), fpBytes);
        }

        throw new IllegalArgumentException("unknown fingerprint method: " + method);
    }

    static List<byte[]> fingerprintPoints(
        byte[][] points,
        int fingerprintBitLength,
        SmallEcElligatorPsuConfig.FingerprintMethod method
    ) {
        List<byte[]> out = new ArrayList<>(points.length);
        for (byte[] p : points) {
            out.add(fingerprint(p, fingerprintBitLength, method));
        }
        return out;
    }

    static long fingerprint64Long(byte[] point, SmallEcElligatorPsuConfig.FingerprintMethod method) {
        byte[] encoded = EcGroupOps.encodePoint(point);
        return fingerprint64LongEncodedPoint(encoded, method);
    }

    private static long fingerprint64LongEncodedPoint(byte[] encoded, SmallEcElligatorPsuConfig.FingerprintMethod method) {
        Preconditions.checkArgument(encoded.length == SmallEcConstants.POINT_BYTES);
        if (method == SmallEcElligatorPsuConfig.FingerprintMethod.CANONICAL_POINT_PREFIX) {
            return ByteBuffer.wrap(encoded, 0, Long.BYTES).getLong();
        }
        if (method == SmallEcElligatorPsuConfig.FingerprintMethod.HASH_THEN_TRUNCATE) {
            Blake2s256 hasher = new Blake2s256();
            hasher.update(encoded);
            byte[] digest = hasher.digest();
            return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        }
        throw new IllegalArgumentException("unknown fingerprint method: " + method);
    }

    static List<byte[]> fingerprintPoints64(byte[][] points, SmallEcElligatorPsuConfig.FingerprintMethod method) {
        List<byte[]> out = new ArrayList<>(points.length);
        for (byte[] p : points) {
            long fp = fingerprint64Long(p, method);
            byte[] bytes = new byte[8];
            ByteBuffer.wrap(bytes).putLong(fp);
            out.add(bytes);
        }
        return out;
    }

    static Set<Long> fingerprintKeySet64(List<byte[]> fingerprints, int expectedByteLength) {
        Preconditions.checkArgument(expectedByteLength == 8);
        Set<Long> set = new HashSet<>(fingerprints.size() * 2);
        for (byte[] fp : fingerprints) {
            Preconditions.checkArgument(fp.length == expectedByteLength);
            set.add(ByteBuffer.wrap(fp).getLong());
        }
        return set;
    }

    static Set<FingerprintKey> fingerprintKeySet(List<byte[]> fingerprints, int expectedByteLength) {
        Set<FingerprintKey> set = new HashSet<>(fingerprints.size() * 2);
        for (byte[] fp : fingerprints) {
            Preconditions.checkArgument(fp.length == expectedByteLength);
            set.add(new FingerprintKey(fp));
        }
        return set;
    }
}
