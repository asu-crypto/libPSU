package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf;

import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Ideal PRF F(k, s) for experimental HN12 debug/tests only; not a production OPRF.
 */
public final class Hn12IdealPrf {
    /** Fixed-width unsigned big-endian scalar encoding length (256-bit field element). */
    public static final int SCALAR_BYTE_LENGTH = 32;

    private Hn12IdealPrf() {
    }

    public static byte[] eval(byte[] key, BigInteger s, String purpose) {
        byte[] sBytes = encodeScalar(s);
        Hash hash = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, 32);
        byte[] digest = hash.digestToBytes(concat(key, sBytes, purpose.getBytes(StandardCharsets.UTF_8)));
        byte[] out = new byte[128];
        System.arraycopy(digest, 0, out, 0, 32);
        for (int slot = 1; slot < 3; slot++) {
            byte[] slotDigest = hash.digestToBytes(
                concat(digest, new byte[] {(byte) slot}, purpose.getBytes(StandardCharsets.UTF_8))
            );
            System.arraycopy(slotDigest, 0, out, slot * 32, 32);
        }
        System.arraycopy(sBytes, 0, out, 96, SCALAR_BYTE_LENGTH);
        return out;
    }

    /**
     * Validated fixed-width unsigned big-endian encoding of {@code s}.
     * <ul>
     *   <li>rejects negative values and values wider than 256 bits;</li>
     *   <li>copies at most the least-significant 32 bytes;</li>
     *   <li>right-aligns shorter encodings;</li>
     *   <li>strips a leading sign byte from {@link BigInteger#toByteArray()} when present.</li>
     * </ul>
     */
    public static byte[] encodeScalar(BigInteger s) {
        if (s == null) {
            throw new IllegalArgumentException("scalar must not be null");
        }
        if (s.signum() < 0) {
            throw new IllegalArgumentException("scalar must be non-negative");
        }
        if (s.bitLength() > 256) {
            throw new IllegalArgumentException("scalar exceeds 256 bits: bitLength=" + s.bitLength());
        }
        return BigIntegerUtils.nonNegBigIntegerToByteArray(s, SCALAR_BYTE_LENGTH);
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
