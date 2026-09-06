package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.cafe.Pgt26FieldMap;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.util.Optional;

/**
 * Curve25519 field map M2P (reference {@code mapping.rs}).
 * <p>
 * Delegates to {@link Pgt26FieldMap} in the cafe package (dalek-compatible field arithmetic).
 * </p>
 */
final class Pgt26Field25519 {
  private Pgt26Field25519() {
    // empty
  }

  static byte[] hashToField(byte[] input) {
    Blake2s256 h = new Blake2s256();
    return h.digest(input);
  }

  static byte[] fieldToMontgomeryPublic(byte[] fieldBytes32) {
    return Pgt26FieldMap.fieldToMontgomery(fieldBytes32);
  }

  static byte[] fieldToEdwards(byte[] fieldBytes32) {
    byte[] mont = Pgt26FieldMap.fieldToMontgomery(fieldBytes32);
    // Reference mapping.rs field_to_edwards: field_to_mont(r).to_edwards(0).unwrap()
    return Pgt26FieldMap.montgomeryToEdwardsY(mont, false);
  }

  static Optional<byte[][]> edwardsToFieldPair(byte[] edwardsPoint) {
    return Pgt26FieldMap.edwardsToFieldPair(edwardsPoint);
  }

  static byte[] negateFieldBytes(byte[] fieldBytes32) {
    return Pgt26FieldMap.negateFieldBytes(fieldBytes32);
  }

  /** Test helper: Montgomery u from Edwards compressed point. */
  static byte[] edwardsToMontgomeryForTest(byte[] edwardsPoint) {
    return Pgt26FieldMap.edwardsToMontgomery(edwardsPoint);
  }

  /** Test helper: inverse of {@link #fieldToMontgomeryPublic}. */
  static Optional<byte[][]> montgomeryToFieldForTest(byte[] montgomeryBytes) {
    return Pgt26FieldMap.montgomeryToField(montgomeryBytes);
  }

}
