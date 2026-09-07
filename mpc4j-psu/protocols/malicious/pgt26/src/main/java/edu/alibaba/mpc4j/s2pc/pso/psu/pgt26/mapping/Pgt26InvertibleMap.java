package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;

import java.util.Arrays;
import java.util.Optional;

/**
 * H = M2P ∘ Π and H⁻¹ (reference {@code mapping.rs} {@code hash_to_point}, {@code recover_from_point}).
 * <p>
 * Encoding matches reference {@code mapping.rs}: {@code M2P(Π(pad(item)))} with no hash-to-curve fallback.
 * </p>
 */
public final class Pgt26InvertibleMap {
  private Pgt26InvertibleMap() {
    // empty
  }

  /** Encode 128-bit item with padding w || 0^{γ−ℓ}, apply Π, map to Edwards point. */
  public static byte[] hashToPoint(byte[] item, Pgt26FeistelPrp256 permut) {
    return hashToPoint(item, permut, Pgt26Constants.ITEM_BYTE_LENGTH);
  }

  /**
   * Encode {@code itemByteLength}-byte item with padding w || 0^{γ−ℓ}, apply Π, map to Edwards point.
   */
  public static byte[] hashToPoint(byte[] item, Pgt26FeistelPrp256 permut, int itemByteLength) {
    if (item.length != itemByteLength) {
      throw new IllegalArgumentException("item must be " + itemByteLength + " bytes");
    }
    byte[] padded = new byte[Pgt26Constants.DOMAIN_FIELD_BYTES];
    System.arraycopy(item, 0, padded, 0, itemByteLength);
    permut.encryptBlock(padded);
    // Reference mapping.rs hash_to_point: always M2P(field) so recover_from_point can invert.
    return Pgt26Field25519.fieldToEdwards(padded);
  }

  /**
   * Strict H_EC(x) = M2P(Π(enc(x))) without hash-to-curve fallback (semi-honest SMALL_EC PSU).
   *
   * @throws IllegalArgumentException if M2P fails or the point is not invertible to {@code item}
   */
  public static byte[] hashToPointStrict(byte[] item, Pgt26FeistelPrp256 permut) {
    return hashToPointStrict(item, permut, Pgt26Constants.ITEM_BYTE_LENGTH);
  }

  /** Alias for {@link #hashToPointStrict} (Figure 8 strict MapToPoint). */
  public static byte[] mapToPointStrict(byte[] item, Pgt26FeistelPrp256 permut, int itemByteLength) {
    return hashToPointStrict(item, permut, itemByteLength);
  }

  public static byte[] hashToPointStrict(byte[] item, Pgt26FeistelPrp256 permut, int itemByteLength) {
    if (item.length != itemByteLength) {
      throw new IllegalArgumentException("item must be " + itemByteLength + " bytes");
    }
    byte[] padded = new byte[Pgt26Constants.DOMAIN_FIELD_BYTES];
    System.arraycopy(item, 0, padded, 0, itemByteLength);
    permut.encryptBlock(padded);
    byte[] ed;
    try {
      ed = Pgt26Field25519.fieldToEdwards(padded);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("M2P failed", ex);
    }
    if (!Pgt26EdwardsMath.isValidPoint(ed)
        || Arrays.equals(ed, edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils.POINT_INFINITY)
        || Arrays.equals(Pgt26EdwardsMath.cofactorClear(ed),
            edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils.POINT_INFINITY)) {
      throw new IllegalArgumentException("M2P produced invalid Edwards point");
    }
    Optional<byte[]> roundtrip = recoverFromPoint(ed, permut, itemByteLength);
    if (roundtrip.isEmpty() || !java.util.Arrays.equals(item, roundtrip.get())) {
      throw new IllegalArgumentException("item does not map to an invertible EC point");
    }
    return ed;
  }

  /**
   * Decode Edwards point to 128-bit item; returns empty if no valid representative / padding.
   */
  public static Optional<byte[]> recoverFromPoint(byte[] edwardsPoint, Pgt26FeistelPrp256 permut) {
    return recoverFromPoint(edwardsPoint, permut, Pgt26Constants.ITEM_BYTE_LENGTH);
  }

  /** Strict H⁻¹ on the M2P(Π(pad(·))) image; empty if the point is outside the image. */
  public static Optional<byte[]> recoverFromPointStrict(
      byte[] edwardsPoint, Pgt26FeistelPrp256 permut, int itemByteLength) {
    return recoverFromPoint(edwardsPoint, permut, itemByteLength);
  }

  public static Optional<byte[]> recoverFromPoint(byte[] edwardsPoint, Pgt26FeistelPrp256 permut, int itemByteLength) {
    for (byte[] torsion : Pgt26EdwardsMath.EIGHT_TORSION) {
      byte[] shifted = Pgt26EdwardsMath.pointAdd(edwardsPoint, torsion);
      Optional<byte[]> item = tryRecoverRepresentative(shifted, permut, itemByteLength);
      if (item.isPresent()) {
        return item;
      }
    }
    return Optional.empty();
  }

  public static boolean hasValidPadding(byte[] item) {
    return hasValidPadding(item, Pgt26Constants.ITEM_BYTE_LENGTH);
  }

  public static boolean hasValidPadding(byte[] item, int itemByteLength) {
    if (item.length != itemByteLength) {
      return false;
    }
    byte[] padded = new byte[Pgt26Constants.DOMAIN_FIELD_BYTES];
    System.arraycopy(item, 0, padded, 0, itemByteLength);
    for (int i = itemByteLength; i < padded.length; i++) {
      if (padded[i] != 0) {
        return false;
      }
    }
    return true;
  }

  public static byte[] padItem(byte[] item) {
    return padItem(item, Pgt26Constants.ITEM_BYTE_LENGTH);
  }

  public static byte[] padItem(byte[] item, int itemByteLength) {
    if (item.length != itemByteLength) {
      throw new IllegalArgumentException("item must be " + itemByteLength + " bytes");
    }
    byte[] padded = new byte[Pgt26Constants.DOMAIN_FIELD_BYTES];
    System.arraycopy(item, 0, padded, 0, itemByteLength);
    return padded;
  }

  private static Optional<byte[]> tryRecoverRepresentative(
      byte[] edwardsPoint, Pgt26FeistelPrp256 permut, int itemByteLength) {
    Optional<byte[][]> pair = Pgt26Field25519.edwardsToFieldPair(edwardsPoint);
    if (pair.isEmpty()) {
      return Optional.empty();
    }
    byte[] result = new byte[itemByteLength];
    for (byte[] field : pair.get()) {
      if (checkFieldBytes(field, permut, result, itemByteLength)) {
        return Optional.of(Arrays.copyOf(result, itemByteLength));
      }
      byte[] neg = Pgt26Field25519.negateFieldBytes(field);
      if (checkFieldBytes(neg, permut, result, itemByteLength)) {
        return Optional.of(Arrays.copyOf(result, itemByteLength));
      }
    }
    return Optional.empty();
  }

  private static boolean checkFieldBytes(
      byte[] field32, Pgt26FeistelPrp256 permut, byte[] resultOut, int itemByteLength) {
    byte[] bytes = Arrays.copyOf(field32, 32);
    byte[] bytesNc = Arrays.copyOf(bytes, 32);
    bytesNc[31] |= (byte) 0x80;
    permut.decryptBlock(bytesNc);
    permut.decryptBlock(bytes);
    if (trailingZeros(bytes, itemByteLength)) {
      System.arraycopy(bytes, 0, resultOut, 0, itemByteLength);
      return true;
    }
    if (trailingZeros(bytesNc, itemByteLength)) {
      System.arraycopy(bytesNc, 0, resultOut, 0, itemByteLength);
      return true;
    }
    return false;
  }

  private static boolean trailingZeros(byte[] block32, int itemByteLength) {
    for (int i = itemByteLength; i < block32.length; i++) {
      if (block32[i] != 0) {
        return false;
      }
    }
    return true;
  }
}
