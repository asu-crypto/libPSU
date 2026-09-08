package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteFullEcc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Prime-order Edwards curve (Ed25519) scalar/point operations for HashDH blinding.
 */
public final class EcGroupOps {
    private static final ByteFullEcc ED25519 =
        ByteEccFactory.createFullInstance(ByteEccFactory.ByteEccType.ED25519_BC);
    private static final BigInteger GROUP_ORDER = Ed25519ByteEccUtils.N;
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final byte[] COFACTOR_INVERSE = Pgt26EdwardsMath.secretInverse(Ed25519ByteEccUtils.SCALAR_COFACTOR);

    private EcGroupOps() {
        // empty
    }

    public static byte[] randomNonZeroScalar(SecureRandom random) {
        for (int attempt = 0; attempt < 64; attempt++) {
            byte[] k = ED25519.randomScalar(random);
            if (!scalarIsZero(k)) {
                return k;
            }
        }
        throw new IllegalStateException("failed to sample non-zero scalar");
    }

    public static byte[] invertScalar(byte[] scalar) {
        Preconditions.checkArgument(!scalarIsZero(scalar));
        return Pgt26EdwardsMath.secretInverse(scalar);
    }

    public static byte[] scalarMul(byte[] point, byte[] scalar) {
        validatePoint(point);
        byte[] canonical = Pgt26EdwardsMath.canonicalizePoint(point);
        byte[] result = Pgt26EdwardsMath.pointMul(canonical, scalar);
        validatePoint(result);
        return result;
    }

    public static byte[] encodePoint(byte[] point) {
        validatePoint(point);
        return BytesUtils.clone(Pgt26EdwardsMath.canonicalizePoint(point));
    }

    public static byte[] decodePoint(byte[] bytes) {
        validatePoint(bytes);
        return BytesUtils.clone(Pgt26EdwardsMath.canonicalizePoint(bytes));
    }

    public static boolean isValidPoint(byte[] point) {
        return Pgt26EdwardsMath.isValidNonIdentityPoint(point);
    }

    public static boolean pointEqual(byte[] a, byte[] b) {
        return Pgt26EdwardsMath.pointEquals(a, b);
    }

    public static void validatePoint(byte[] point) {
        if (!isValidPoint(point)) {
            throw new IllegalArgumentException("invalid or identity curve point");
        }
    }

    public static Set<CanonicalPoint> canonicalPointKeySet(byte[][] points) {
        Set<CanonicalPoint> keys = new HashSet<>(points.length * 2);
        for (byte[] p : points) {
            keys.add(new CanonicalPoint(encodePoint(p)));
        }
        return keys;
    }

    public static boolean containsCanonical(Set<CanonicalPoint> keys, byte[] point) {
        return keys.contains(new CanonicalPoint(encodePoint(point)));
    }

    public static CanonicalPoint canonicalPoint(byte[] point) {
        return new CanonicalPoint(encodePoint(point));
    }

    /** Undo {@link #clearCofactor} on points in the strict HashDH image. */
    public static byte[] removeCofactor(byte[] point) {
        validatePoint(point);
        return scalarMul(point, COFACTOR_INVERSE);
    }

    /** Map M2P outputs into the prime-order subgroup (cofactor 8) for HashDH blinding. */
    public static byte[] clearCofactor(byte[] point) {
        validatePoint(point);
        byte[] cleared = Pgt26EdwardsMath.pointMul(
            Pgt26EdwardsMath.canonicalizePoint(point), Ed25519ByteEccUtils.SCALAR_COFACTOR
        );
        validatePoint(cleared);
        return cleared;
    }

    public static final class CanonicalPoint {
        private final byte[] bytes;

        CanonicalPoint(byte[] canonicalBytes) {
            bytes = BytesUtils.clone(canonicalBytes);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CanonicalPoint that && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }
    }

    private static boolean scalarIsZero(byte[] scalar) {
        byte[] le = BytesUtils.clone(scalar);
        BytesUtils.innerReverseByteArray(le);
        return new BigInteger(1, le).mod(GROUP_ORDER).equals(ZERO);
    }
}
