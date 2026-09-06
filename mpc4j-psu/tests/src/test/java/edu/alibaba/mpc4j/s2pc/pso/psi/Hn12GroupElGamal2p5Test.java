package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.commit.Hn12Pedersen;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12ElGamal;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * HN12_GROUP_ELGAMAL_2p5: group, ElGamal-in-exponent, Pedersen at set size 2^5 scale params.
 */
public class Hn12GroupElGamal2p5Test {
  private static final SecureRandom RANDOM = new SecureRandom();

  @Test
  public void testEncryptHomomorphicAndCommit() {
    Hn12DdhGroup group = Hn12DdhGroup.createForTests(512, RANDOM);
    Hn12ElGamal eg = Hn12ElGamal.keyGen(group, RANDOM);
    BigInteger m = group.sampleScalar(RANDOM);
    BigInteger gm = group.pow(group.getG(), m);
    Hn12Ciphertext ct = eg.encryptExponent(gm, RANDOM);
    BigInteger dec = eg.decryptToExponent(ct);
    Assert.assertEquals(gm, dec);
    Hn12Ciphertext ct2 = eg.multiply(ct, eg.encryptExponent(group.pow(group.getG(), BigInteger.ONE), RANDOM));
    BigInteger expected = group.mul(gm, group.pow(group.getG(), BigInteger.ONE));
    Assert.assertEquals(expected, eg.decryptToExponent(ct2));
    BigInteger h = group.sampleNonIdentityElement(RANDOM);
    Hn12Pedersen ped = new Hn12Pedersen(group, h);
    BigInteger r = group.sampleScalar(RANDOM);
    BigInteger c = ped.commit(m, r);
    Assert.assertEquals(c, ped.commit(m, r));
  }
}
