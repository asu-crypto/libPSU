package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.X25519ByteEccUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

public class Pgt26FieldMapDebugTest {
  @Test
  public void montgomeryBytesArePoint() {
    byte[] padded = new byte[32];
    padded[0] = 42;
    byte[] mont = Pgt26Field25519.fieldToMontgomeryPublic(padded);
    Assert.assertTrue(X25519ByteEccUtils.checkPoint(mont));
    boolean any = false;
    for (boolean sign : new boolean[]{false, true}) {
      try {
        byte[] ed = Pgt26Field25519.fieldToEdwards(padded);
        // Raw M2P image may carry a torsion component; wire validation uses
        // isValidNonIdentityPoint after cofactor clearing in the protocol.
        if (Pgt26EdwardsMath.isValidPoint(ed)
            && !java.util.Arrays.equals(
                ed, edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils.POINT_INFINITY)) {
          any = true;
        }
      } catch (Exception ignored) {
        // try next
      }
    }
    Assert.assertTrue("field_to_edwards should succeed for sample", any);
  }

  @Test
  public void fieldMontgomeryEdwardsRoundTrip() {
    byte[] padded = new byte[32];
    padded[0] = 42;
    byte[] mont = Pgt26Field25519.fieldToMontgomeryPublic(padded);
    byte[] ed = Pgt26Field25519.fieldToEdwards(padded);
    Assert.assertTrue(Pgt26EdwardsMath.isValidPoint(ed));
    Assert.assertTrue(
        Pgt26EdwardsMath.isValidNonIdentityPoint(Pgt26EdwardsMath.cofactorClear(ed))
    );
    byte[] montFromEd = Pgt26Field25519.edwardsToMontgomeryForTest(ed);
    Assert.assertNotNull(montFromEd);
    Assert.assertArrayEquals(
        "field_to_mont and edwards_to_montgomery must agree",
        clearHighBit(mont),
        clearHighBit(montFromEd)
    );
    Assert.assertTrue(
        "mont_to_field should succeed on field_to_mont output",
        Pgt26Field25519.montgomeryToFieldForTest(mont).isPresent()
    );
    Optional<byte[][]> back = Pgt26Field25519.edwardsToFieldPair(ed);
    Assert.assertTrue("edwards_to_field should succeed", back.isPresent());
    boolean matches = false;
    for (byte[] f : back.get()) {
      if (Arrays.equals(f, padded) || Arrays.equals(f, Pgt26Field25519.negateFieldBytes(padded))) {
        matches = true;
      }
    }
    Assert.assertTrue("recovered field should match padded PRP block", matches);
  }

  private static byte[] clearHighBit(byte[] mont) {
    byte[] out = BytesUtils.clone(mont);
    out[out.length - 1] &= 0x7F;
    return out;
  }
}
