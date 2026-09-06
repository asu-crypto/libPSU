package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteFullEcc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteMulEcc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Curve25519 helpers for PGT26-1M (Edwards, reference {@code onesided.rs} HashDH2}) and Montgomery HashDH.
 */
public final class Pgt26CurveOps {
  private static final ByteMulEcc MONTGOMERY_ECC =
      ByteEccFactory.createMulInstance(ByteEccFactory.ByteEccType.X25519_BC);
  private static final ByteFullEcc ED25519_ECC =
      ByteEccFactory.createFullInstance(ByteEccFactory.ByteEccType.ED25519_BC);

  private Pgt26CurveOps() {
    // empty
  }

  /**
   * H(item) → Edwards compressed point (1M / Schnorr path).
   */
  public static byte[] hashToCurve(byte[] item) {
    // PGT26 uses 128-bit items, but the hash-to-curve fallback itself is well-defined for any byte string.
    byte[] digest = new Blake2s256().digest(item);
    byte[] scalar = clampScalarDigest(digest);
    byte[] point = new byte[Ed25519ByteEccUtils.POINT_BYTES];
    Ed25519ByteEccUtils.scalarBaseMulEncoded(scalar, point);
    return point;
  }

  public static byte[] randomScalar(SecureRandom random) {
    return ED25519_ECC.randomScalar(random);
  }

  public static byte[] scalarMul(byte[] edwardsPoint, byte[] scalar) {
    byte[] result = new byte[Ed25519ByteEccUtils.POINT_BYTES];
    Ed25519ByteEccUtils.scalarMulEncoded(scalar, edwardsPoint, result);
    return result;
  }

  public static boolean isValidPoint(byte[] point) {
    return point != null
        && point.length == Ed25519ByteEccUtils.POINT_BYTES
        && !Arrays.equals(point, Ed25519ByteEccUtils.POINT_INFINITY)
        && ED25519_ECC.isValidPoint(point);
  }

  public static Set<ByteBuffer> canonicalPointSet(byte[][] points) {
    Set<ByteBuffer> set = new HashSet<>(points.length * 2);
    for (byte[] p : points) {
      set.add(ByteBuffer.wrap(BytesUtils.clone(p)));
    }
    return set;
  }

  public static boolean containsPoint(Set<ByteBuffer> canonical, byte[] point) {
    return canonical.contains(ByteBuffer.wrap(point));
  }

  public static void edwardsPointAdd(byte[] r, byte[] p) {
    Ed25519ByteEccUtils.pointAdd(r, p);
  }

  public static void edwardsScalarMul(byte[] k, byte[] p, byte[] r) {
    Ed25519ByteEccUtils.scalarMulEncoded(k, p, r);
  }

  public static void edwardsScalarBaseMul(byte[] k, byte[] r) {
    Ed25519ByteEccUtils.scalarBaseMulEncoded(k, r);
  }

  public static byte[] hashCommitment(byte[]... parts) {
    Blake2s256 h = new Blake2s256();
    for (byte[] part : parts) {
      h.update(part);
    }
    return h.digest();
  }

  /** Montgomery H for semi-honest-only helpers (not used in 1M). */
  public static byte[] hashToMontgomery(byte[] item16) {
    byte[] digest = new Blake2s256().digest(item16);
    digest[digest.length - 1] &= 0x7F;
    return BytesUtils.clone(digest);
  }

  public static byte[] montgomeryScalarMul(byte[] point, byte[] scalar) {
    return MONTGOMERY_ECC.mul(point, scalar);
  }

  private static byte[] clampScalarDigest(byte[] digest32) {
    byte[] k = BytesUtils.clone(digest32);
    k[0] &= (byte) 0xF8;
    k[31] &= 0x7F;
    k[31] |= 0x40;
    return k;
  }
}
