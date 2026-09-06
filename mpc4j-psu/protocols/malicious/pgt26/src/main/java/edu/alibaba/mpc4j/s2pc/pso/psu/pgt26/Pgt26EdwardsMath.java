package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteFullEcc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.X25519ByteEccUtils;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Edwards25519 group arithmetic for PGT26-2M (reference {@code aok.rs}, {@code twosided.rs}).
 */
public final class Pgt26EdwardsMath {
  private static final ByteFullEcc ED25519 =
      ByteEccFactory.createFullInstance(ByteEccFactory.ByteEccType.ED25519_BC);
  private static final BigInteger SUBGROUP_ORDER = ED25519.getN();
  private static final BigInteger EIGHT = BigInteger.valueOf(8);
  private static final BigInteger EIGHT_INV = EIGHT.modInverse(SUBGROUP_ORDER);

  /** Edwards cofactor-8 coset representatives (reference {@code mapping.rs} / dalek EIGHT_TORSION). */
  public static final byte[][] EIGHT_TORSION = new byte[][]{
      Hex.decode("0100000000000000000000000000000000000000000000000000000000000000"),
      Hex.decode("c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a"),
      Hex.decode("0000000000000000000000000000000000000000000000000000000000000080"),
      Hex.decode("26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05"),
      Hex.decode("ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
      Hex.decode("26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc85"),
      Hex.decode("0000000000000000000000000000000000000000000000000000000000000000"),
      Hex.decode("c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa"),
  };

  private Pgt26EdwardsMath() {
    // empty
  }

  public static byte[] randomScalar(SecureRandom random) {
    return ED25519.randomScalar(random);
  }

  public static byte[] clampInteger(byte[] raw32) {
    byte[] out = BytesUtils.clone(raw32);
    out[0] &= (byte) 248;
    out[31] &= 0x7F;
    out[31] |= 0x40;
    return out;
  }

  /** sk = from_bytes_mod_order(sk_8) · 8⁻¹ (reference {@code twosided.rs} Party::new}). */
  public static byte[] secretFromClamped(byte[] sk8) {
    BigInteger s8 = scalarLe(sk8).mod(SUBGROUP_ORDER);
    return scalarToLe(s8.multiply(EIGHT_INV).mod(SUBGROUP_ORDER));
  }

  public static byte[] secretInverse(byte[] secret) {
    return scalarToLe(scalarLe(secret).modInverse(SUBGROUP_ORDER));
  }

  /** sk_8inv = sk⁻¹ · 8⁻¹. */
  public static byte[] secret8Inverse(byte[] secret, byte[] secretInv) {
    BigInteger inv = scalarLe(secretInv).mod(SUBGROUP_ORDER);
    return scalarToLe(inv.multiply(EIGHT_INV).mod(SUBGROUP_ORDER));
  }

  public static byte[] publicKey(byte[] secret) {
    return ED25519.baseMul(reduceScalar(secret));
  }

  public static byte[] hashToCurve(byte[]... parts) {
    Blake2s256 h = new Blake2s256();
    for (byte[] p : parts) {
      h.update(p);
    }
    return canonicalizePoint(ED25519.hashToCurve(h.digest()));
  }

  public static byte[] reduceScalar(byte[] scalar) {
    return scalarToLe(scalarLe(scalar).mod(SUBGROUP_ORDER));
  }

  public static byte[] pointMul(byte[] point, byte[] scalar) {
    return ED25519.mul(point, reduceScalar(scalar));
  }

  public static byte[] cofactorClear(byte[] point) {
    return pointMul(point, scalarToLe(EIGHT));
  }

  /** Re-encode via scalar·1 so wire/MSM use canonical compressed Edwards coordinates. */
  public static byte[] canonicalizePoint(byte[] point) {
    return pointMul(point, scalarToLe(BigInteger.ONE));
  }

  /**
   * Decompress wire-format points for group ops (reference {@code verify_sigma} /
   * {@code final_response} in {@code twosided.rs}).
   */
  public static byte[][] decompressPoints(byte[][] compressed) {
    byte[][] out = new byte[compressed.length][];
    for (int i = 0; i < compressed.length; i++) {
      if (!isValidNonIdentityPoint(compressed[i])) {
        throw new IllegalArgumentException("invalid Edwards point at index " + i);
      }
      out[i] = canonicalizePoint(compressed[i]);
    }
    return out;
  }

  public static byte[] pointAdd(byte[] a, byte[] b) {
    return ED25519.add(a, b);
  }

  public static byte[] pointNeg(byte[] point) {
    byte[] neg = BytesUtils.clone(point);
    neg[Ed25519ByteEccUtils.POINT_BYTES - 1] ^= (byte) (1 << 7);
    return neg;
  }

  public static byte[] identityPoint() {
    return BytesUtils.clone(Ed25519ByteEccUtils.POINT_INFINITY);
  }

  public static byte[] multiscalarMul(byte[][] points, byte[][] scalars) {
    if (points.length != scalars.length) {
      throw new IllegalArgumentException("points/scalars length mismatch");
    }
    if (points.length == 0) {
      return identityPoint();
    }
    byte[] acc = pointMul(points[0], scalars[0]);
    for (int i = 1; i < points.length; i++) {
      acc = pointAdd(acc, pointMul(points[i], scalars[i]));
    }
    return acc;
  }

  public static byte[] pedersenCommit(byte[][] generators, byte[][] messageScalars, byte[] randomness) {
    byte[][] pts = Arrays.copyOf(generators, generators.length + 1);
    byte[][] scs = Arrays.copyOf(messageScalars, messageScalars.length + 1);
    pts[pts.length - 1] = Ed25519ByteEccUtils.POINT_B;
    scs[scs.length - 1] = randomness;
    return multiscalarMul(pts, scs);
  }

  public static boolean isValidNonIdentityPoint(byte[] point) {
    if (!isValidPoint(point)) {
      return false;
    }
    if (Arrays.equals(point, Ed25519ByteEccUtils.POINT_INFINITY)) {
      return false;
    }
    // PGT26-2M uses Edwards25519 points encoded as CompressedEdwardsY (32 bytes). We must validate them as ED25519,
    // not as X25519 Montgomery u-coordinates.
    return !isLowOrderPoint(point);
  }

  public static boolean isValidPoint(byte[] point) {
    return point != null
        && point.length == Ed25519ByteEccUtils.POINT_BYTES
        && ED25519.isValidPoint(point);
  }

  public static boolean isLowOrderPoint(byte[] point) {
    if (!isValidPoint(point)) {
      return false;
    }
    return Arrays.equals(cofactorClear(point), Ed25519ByteEccUtils.POINT_INFINITY);
  }

  public static byte[] scalarFromHash(byte[] digest32) {
    return scalarToLe(scalarLe(digest32).mod(SUBGROUP_ORDER));
  }

  public static byte[] blake2sScalar(byte[]... parts) {
    Blake2s256 h = new Blake2s256();
    for (byte[] p : parts) {
      h.update(p);
    }
    return scalarFromHash(h.digest());
  }

  public static BigInteger scalarLe(byte[] bytes) {
    byte[] le = BytesUtils.clone(bytes);
    BytesUtils.innerReverseByteArray(le);
    return BigIntegerUtils.byteArrayToNonNegBigInteger(le);
  }

  public static byte[] scalarToLe(BigInteger s) {
    byte[] be = BigIntegerUtils.nonNegBigIntegerToByteArray(s.mod(SUBGROUP_ORDER), Ed25519ByteEccUtils.SCALAR_BYTES);
    BytesUtils.innerReverseByteArray(be);
    return be;
  }

  /** Curve-point equality (reference {@code EdwardsPoint == EdwardsPoint} in {@code aok.rs}). */
  public static boolean pointEquals(byte[] a, byte[] b) {
    if (a == null || b == null || a.length != Ed25519ByteEccUtils.POINT_BYTES
        || b.length != Ed25519ByteEccUtils.POINT_BYTES) {
      return false;
    }
    return Arrays.equals(canonicalizePoint(a), canonicalizePoint(b));
  }

  public static BigInteger subgroupOrder() {
    return SUBGROUP_ORDER;
  }
}
