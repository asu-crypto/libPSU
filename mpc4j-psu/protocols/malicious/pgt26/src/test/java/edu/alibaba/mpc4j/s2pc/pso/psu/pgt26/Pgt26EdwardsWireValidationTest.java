package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.cafe.CafeConstants;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.cafe.CafeEdwardsPoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Peer wire points must be torsion-free prime-subgroup Edwards points with canonical encoding.
 */
public class Pgt26EdwardsWireValidationTest {
  @Test
  public void rejectPureTorsionPoints() {
    for (byte[] torsion : Pgt26EdwardsMath.EIGHT_TORSION) {
      Assert.assertFalse(
          "pure torsion must be rejected: " + Arrays.toString(torsion),
          Pgt26EdwardsMath.isValidNonIdentityPoint(torsion)
      );
    }
  }

  @Test
  public void rejectBasePlusTorsion() {
    byte[] base = CafeConstants.ED25519_BASE_POINT.compress().encode();
    Assert.assertTrue(Pgt26EdwardsMath.isValidNonIdentityPoint(base));
    for (int i = 1; i < Pgt26EdwardsMath.EIGHT_TORSION.length; i++) {
      byte[] mixed = Pgt26EdwardsMath.pointAdd(base, Pgt26EdwardsMath.EIGHT_TORSION[i]);
      Assert.assertFalse(
          "mixed torsion must be rejected",
          Pgt26EdwardsMath.isValidNonIdentityPoint(mixed)
      );
    }
  }

  @Test
  public void rejectIdentity() {
    Assert.assertFalse(Pgt26EdwardsMath.isValidNonIdentityPoint(Pgt26EdwardsMath.EIGHT_TORSION[0]));
  }

  @Test
  public void acceptPrimeSubgroupBase() {
    byte[] base = CafeConstants.ED25519_BASE_POINT.compress().encode();
    Assert.assertTrue(Pgt26EdwardsMath.isValidNonIdentityPoint(base));
    Assert.assertTrue(CafeEdwardsPoint.IDENTITY.isIdentity());
  }

  @Test
  public void rejectNoncanonicalScalar() {
    byte[] s = Pgt26EdwardsMath.randomScalar(new java.security.SecureRandom());
    Assert.assertTrue(Pgt26EdwardsMath.isCanonicalScalar(s));
    java.math.BigInteger inflated = Pgt26EdwardsMath.scalarLe(s).add(Pgt26EdwardsMath.subgroupOrder());
    byte[] nonCanonical = Pgt26EdwardsMath.scalarToLe(inflated); // reduces mod order — need raw LE
    // Build s + ℓ without reduction.
    byte[] raw = Arrays.copyOf(s, 32);
    java.math.BigInteger v = Pgt26EdwardsMath.scalarLe(raw).add(Pgt26EdwardsMath.subgroupOrder());
    byte[] be = edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils.nonNegBigIntegerToByteArray(v, 32);
    edu.alibaba.mpc4j.common.tool.utils.BytesUtils.innerReverseByteArray(be);
    Assert.assertFalse(Pgt26EdwardsMath.isCanonicalScalar(be));
  }
}
