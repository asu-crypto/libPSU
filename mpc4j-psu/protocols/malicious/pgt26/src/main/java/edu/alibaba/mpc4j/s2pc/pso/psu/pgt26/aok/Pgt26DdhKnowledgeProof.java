package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteEccFactory;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.ByteFullEcc;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.common.tool.utils.BigIntegerUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Schnorr/DDH AoK: prove knowledge of sender exponent α with y = H(w)^α (reference {@code onesided.rs}).
 */
public final class Pgt26DdhKnowledgeProof {
  private static final ByteFullEcc ED25519 =
      ByteEccFactory.createFullInstance(ByteEccFactory.ByteEccType.ED25519_BC);

  public static final int CHALLENGE_BYTES = 16;
  public static final int SCALAR_BYTES = Ed25519ByteEccUtils.SCALAR_BYTES;
  public static final int PROOF_BYTES = CHALLENGE_BYTES + SCALAR_BYTES;

  private static final BigInteger SUBGROUP_ORDER = ED25519.getN();

  private Pgt26DdhKnowledgeProof() {
    // empty
  }

  public static byte[] prove(
      byte[] item16,
      byte[] hxEdwards,
      byte[] yEdwards,
      byte[] senderScalar,
      SecureRandom random,
      byte[] protocolTag
  ) {
    byte[] r = ED25519.randomScalar(random);
    byte[] hr = ED25519.mul(hxEdwards, r);
    byte[] c = challenge(protocolTag, item16, hxEdwards, yEdwards, hr);
    BigInteger cS = scalarLe(c).mod(SUBGROUP_ORDER);
    BigInteger rS = scalarLe(r).mod(SUBGROUP_ORDER);
    BigInteger kS = scalarLe(senderScalar).mod(SUBGROUP_ORDER);
    byte[] z = scalarToLe(rS.subtract(cS.multiply(kS)).mod(SUBGROUP_ORDER));

    byte[] proof = new byte[PROOF_BYTES];
    System.arraycopy(c, 0, proof, 0, CHALLENGE_BYTES);
    System.arraycopy(z, 0, proof, CHALLENGE_BYTES, SCALAR_BYTES);
    return proof;
  }

  public static boolean verify(
      byte[] item16,
      byte[] hxEdwards,
      byte[] yEdwards,
      byte[] proof,
      byte[] protocolTag
  ) {
    if (proof == null || proof.length != PROOF_BYTES) {
      return false;
    }
    try {
      byte[] c = Arrays.copyOfRange(proof, 0, CHALLENGE_BYTES);
      byte[] z = Arrays.copyOfRange(proof, CHALLENGE_BYTES, PROOF_BYTES);
      byte[] cScalar = scalarToLe(scalarLe(c).mod(SUBGROUP_ORDER));
      byte[] lhs = ED25519.add(ED25519.mul(hxEdwards, z), ED25519.mul(yEdwards, cScalar));
      byte[] expectedC = challenge(protocolTag, item16, hxEdwards, yEdwards, lhs);
      return BytesUtils.equals(c, expectedC);
    } catch (Throwable ex) {
      return false;
    }
  }

  private static byte[] challenge(
      byte[] protocolTag,
      byte[] item16,
      byte[] hxEd,
      byte[] yEd,
      byte[] hrEd
  ) {
    Blake2s256 h = new Blake2s256();
    h.update(protocolTag);
    h.update(item16);
    h.update(hxEd);
    h.update(yEd);
    h.update(hrEd);
    byte[] out = h.digest();
    return Arrays.copyOf(out, CHALLENGE_BYTES);
  }

  private static BigInteger scalarLe(byte[] bytes) {
    byte[] le = BytesUtils.clone(bytes);
    BytesUtils.innerReverseByteArray(le);
    return BigIntegerUtils.byteArrayToNonNegBigInteger(le);
  }

  private static byte[] scalarToLe(BigInteger s) {
    byte[] be = BigIntegerUtils.nonNegBigIntegerToByteArray(s.mod(SUBGROUP_ORDER), SCALAR_BYTES);
    BytesUtils.innerReverseByteArray(be);
    return be;
  }
}
