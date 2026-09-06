package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12ElGamal;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly.Hn12HomomorphicPolyEval;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.poly.Hn12Polynomial;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class Hn12Poly2p5Test {
  @Test
  public void testMulAndPow() {
    SecureRandom random = new SecureRandom();
    Hn12DdhGroup group = Hn12DdhGroup.createForTests(256, random);
    Hn12ElGamal eg = Hn12ElGamal.keyGen(group, random);
    BigInteger g = group.getG();
    Hn12Ciphertext a = eg.encryptExponent(group.pow(g, BigInteger.valueOf(2)), random);
    Hn12Ciphertext b = eg.encryptExponent(group.pow(g, BigInteger.valueOf(3)), random);
    Assert.assertEquals(group.pow(g, BigInteger.valueOf(5)), eg.decryptToExponent(eg.multiply(a, b)));
    Hn12Ciphertext p = eg.powScalar(a, BigInteger.valueOf(3));
    Assert.assertEquals(group.pow(g, BigInteger.valueOf(6)), eg.decryptToExponent(p));
  }

  @Test
  public void testLinearZero() {
    SecureRandom random = new SecureRandom();
    Hn12DdhGroup group = Hn12DdhGroup.createForTests(256, random);
    Hn12ElGamal eg = Hn12ElGamal.keyGen(group, random);
    BigInteger r1 = group.sampleScalar(random);
    BigInteger c1 = BigInteger.ONE;
    BigInteger c0 = r1.negate().mod(group.getQ());
    List<BigInteger> coeffs = List.of(c0, c1);
    List<Hn12Ciphertext> enc = new ArrayList<>();
    for (BigInteger c : coeffs) {
      enc.add(eg.encryptExponent(group.pow(group.getG(), c), random));
    }
    Assert.assertEquals(group.identity(), eg.decryptToExponent(Hn12HomomorphicPolyEval.evaluate(group, eg, enc, r1)));
  }

  @Test
  public void testRootEvalZero() {
    SecureRandom random = new SecureRandom();
    Hn12DdhGroup group = Hn12DdhGroup.createForTests(256, random);
    Hn12ElGamal eg = Hn12ElGamal.keyGen(group, random);
    BigInteger r1 = group.sampleScalar(random);
    BigInteger r2 = group.sampleScalar(random);
    while (r2.equals(r1)) {
      r2 = group.sampleScalar(random);
    }
    List<BigInteger> roots = List.of(r1, r2);
    List<BigInteger> coeffs = Hn12Polynomial.trimTrailingZeros(
        Hn12Polynomial.buildRootPolynomial(roots, group.getQ(), 4));
    Assert.assertEquals(BigInteger.ZERO, Hn12Polynomial.evaluate(coeffs, r1, group.getQ()));
    List<Hn12Ciphertext> enc = new ArrayList<>();
    for (BigInteger c : coeffs) {
      BigInteger gm = c.signum() == 0 ? group.identity() : group.pow(group.getG(), c);
      enc.add(eg.encryptExponent(gm, random));
    }
    Hn12Ciphertext eval = Hn12HomomorphicPolyEval.evaluate(group, eg, enc, r1);
    Assert.assertEquals(group.identity(), eg.decryptToExponent(eval));
  }
}
