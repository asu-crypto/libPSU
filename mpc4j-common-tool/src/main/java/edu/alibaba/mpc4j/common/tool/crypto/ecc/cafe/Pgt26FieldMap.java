package edu.alibaba.mpc4j.common.tool.crypto.ecc.cafe;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Curve25519FieldUtils;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;

import java.util.Arrays;
import java.util.Optional;

/**
 * PGT26 {@code mapping.rs} field ↔ Montgomery map (M2P / M2P⁻¹).
 */
public final class Pgt26FieldMap {
  private static final CafeFieldElement FE_Z = feSmall(2);
  private static final CafeFieldElement FE_A = new CafeFieldElement(Curve25519FieldUtils.A_INTS);
  private static final CafeFieldElement FE_A_NEG = FE_A.neg();
  private static final CafeFieldElement FE_Z_NEG = FE_Z.neg();
  private static final CafeFieldElement FE_A_SQ = FE_A.sqr();
  private static final CafeFieldElement FE_ZU_CONST = FE_Z_NEG.mul(CafeConstants.SQRT_M1);

  private Pgt26FieldMap() {
    // empty
  }

  public static byte[] fieldToMontgomery(byte[] fieldBytes32) {
    CafeFieldElement r = CafeFieldElement.decode(fieldBytes32);
    CafeFieldElement u2 = r.sqr();
    CafeFieldElement zu = FE_Z.mul(u2);
    CafeFieldElement v = zu.add(CafeFieldElement.ONE);
    CafeFieldElement v2 = v.sqr();
    CafeFieldElement t3 = FE_A_SQ.mul(zu).sub(v2).mul(FE_A);
    CafeFieldElement t1 = v2.mul(v);
    CafeFieldElement.SqrtRatioM1Result sr = CafeFieldElement.sqrtRatioM1(CafeFieldElement.ONE, t3.mul(t1));
    CafeFieldElement uOut = u2.mul(FE_ZU_CONST);
    if (sr.wasSquare != 0) {
      uOut = CafeFieldElement.ONE;
    }
    CafeFieldElement x = uOut.neg().mul(FE_A).mul(t3).mul(v2).mul(sr.result.sqr());
    return x.encode();
  }

  public static Optional<byte[][]> montgomeryToField(byte[] montgomeryBytes) {
    CafeFieldElement u = CafeFieldElement.decode(montgomeryBytes);
    if (u.cequals(FE_A_NEG) == 1) {
      return Optional.empty();
    }
    CafeFieldElement t = u.add(FE_A);
    CafeFieldElement zu = FE_Z_NEG.mul(u);
    CafeFieldElement.SqrtRatioM1Result sr = CafeFieldElement.sqrtRatioM1(CafeFieldElement.ONE, zu.mul(t));
    if (sr.wasSquare == 0) {
      return Optional.empty();
    }
    CafeFieldElement tr = t.mul(sr.result);
    CafeFieldElement ur = u.mul(sr.result);
    CafeFieldElement r0 = tr.isNeg() == 1 ? tr.neg() : tr;
    CafeFieldElement r1 = ur.isNeg() == 1 ? ur.neg() : ur;
    return Optional.of(new byte[][]{r0.encode(), r1.encode()});
  }

  public static byte[] fieldToEdwardsY(byte[] fieldBytes32) {
    byte[] mont = fieldToMontgomery(fieldBytes32);
    return montgomeryToEdwardsY(mont, false);
  }

  public static byte[] montgomeryToEdwardsY(byte[] montgomeryBytes, boolean sign) {
    CafeFieldElement u = CafeFieldElement.decode(montgomeryBytes);
    if (u.cequals(CafeFieldElement.MINUS_ONE) == 1) {
      throw new ArithmeticException("montgomery u = -1 (twist point)");
    }
    CafeFieldElement y = u.sub(CafeFieldElement.ONE).mul(u.add(CafeFieldElement.ONE).inv());
    byte[] ed = y.encode();
    ed[Ed25519ByteEccUtils.POINT_BYTES - 1] &= 0x7F;
    if (sign) {
      ed[Ed25519ByteEccUtils.POINT_BYTES - 1] |= 0x80;
    }
    return ed;
  }

  public static byte[] edwardsToMontgomery(byte[] edwardsCompressed) {
    byte[] copy = Arrays.copyOf(edwardsCompressed, edwardsCompressed.length);
    copy[Ed25519ByteEccUtils.POINT_BYTES - 1] &= 0x7F;
    CafeFieldElement y = CafeFieldElement.decode(copy);
    CafeFieldElement denom = CafeFieldElement.ONE.sub(y);
    if (denom.isZero() == 1) {
      return null;
    }
    CafeFieldElement u = CafeFieldElement.ONE.add(y).mul(denom.inv());
    byte[] mont = u.encode();
    mont[mont.length - 1] &= 0x7F;
    return mont;
  }

  public static Optional<byte[][]> edwardsToFieldPair(byte[] edwardsCompressed) {
    byte[] mont = edwardsToMontgomery(edwardsCompressed);
    if (mont == null) {
      return Optional.empty();
    }
    return montgomeryToField(mont);
  }

  public static byte[] negateFieldBytes(byte[] fieldBytes32) {
    return CafeFieldElement.decode(fieldBytes32).neg().encode();
  }

  private static CafeFieldElement feSmall(int x) {
    int[] t = Curve25519FieldUtils.createZero();
    t[0] = x;
    return new CafeFieldElement(t);
  }
}
