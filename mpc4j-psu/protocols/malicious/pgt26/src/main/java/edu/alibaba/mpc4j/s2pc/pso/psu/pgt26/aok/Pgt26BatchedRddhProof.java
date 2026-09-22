package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ChaCha12Rng;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Batched RDDH AoK (reference {@code aok.rs} {@code batched_ddh_prove}/{@code batched_ddh_verify}).
 */
public final class Pgt26BatchedRddhProof {
  public final byte[] commitment;
  public final byte[] z;

  public Pgt26BatchedRddhProof(byte[] commitment, byte[] z) {
    this.commitment = commitment;
    this.z = z;
  }

  public static Pgt26BatchedRddhProof prove(
      byte[] proverPk,
      byte[][] g,
      byte[][] hCompressed,
      byte[] secret,
      SecureRandom random
  ) {
    int n = g.length;
    byte[] r = Pgt26EdwardsMath.randomScalar(random);
    Blake2s256 hasher = new Blake2s256();
    hasher.update(proverPk);
    for (byte[] gi : g) {
      hasher.update(gi);
    }
    for (byte[] hi : hCompressed) {
      hasher.update(hi);
    }
    byte[] ha = hasher.digest();
    byte[][] ar = new byte[n][];
    Pgt26ChaCha12Rng rngA = new Pgt26ChaCha12Rng(ha);
    BigInteger rBi = Pgt26EdwardsMath.scalarLe(r);
    for (int i = 0; i < n; i++) {
      ar[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(rngA.nextScalar()).multiply(rBi).mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    byte[] c = Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.multiscalarMul(g, ar),
        Pgt26EdwardsMath.publicKey(r)
    );
    hasher.update(c);
    hasher.update(ha);
    byte[] delta = Pgt26EdwardsMath.scalarFromHash(hasher.digest());
    byte[] z = Pgt26EdwardsMath.scalarToLe(
        Pgt26EdwardsMath.scalarLe(delta).multiply(Pgt26EdwardsMath.scalarLe(secret)).add(rBi)
            .mod(Pgt26EdwardsMath.subgroupOrder())
    );
    return new Pgt26BatchedRddhProof(c, z);
  }

  public static boolean verify(
      byte[] proverPk,
      byte[][] g,
      byte[][] h,
      byte[][] gCompressed,
      byte[][] hCompressed,
      Pgt26BatchedRddhProof proof
  ) {
    if (!isWellFormed(proverPk, g, h, gCompressed, hCompressed, proof)) {
      return false;
    }
    try {
      return verifyUnchecked(proverPk, g, h, gCompressed, hCompressed, proof);
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private static boolean verifyUnchecked(
      byte[] proverPk,
      byte[][] g,
      byte[][] h,
      byte[][] gCompressed,
      byte[][] hCompressed,
      Pgt26BatchedRddhProof proof
  ) {
    int n = g.length;
    Blake2s256 hasher = new Blake2s256();
    hasher.update(proverPk);
    for (byte[] gi : gCompressed) {
      hasher.update(gi);
    }
    for (byte[] hi : hCompressed) {
      hasher.update(hi);
    }
    byte[] ha = hasher.digest();
    byte[][] a = Pgt26ChaCha12Rng.scalarsFromSeed(ha, n);
    hasher.update(proof.commitment);
    hasher.update(ha);
    byte[] delta = Pgt26EdwardsMath.scalarFromHash(hasher.digest());
    byte[] lhs = Pgt26EdwardsMath.pointAdd(
        proof.commitment,
        Pgt26EdwardsMath.pointMul(proverPk, delta)
    );
    lhs = Pgt26EdwardsMath.pointAdd(lhs, Pgt26EdwardsMath.pointNeg(Pgt26EdwardsMath.publicKey(proof.z)));
    byte[][] points = concat(g, h);
    byte[][] scalars = new byte[2 * n][];
    BigInteger zBi = Pgt26EdwardsMath.scalarLe(proof.z);
    BigInteger dBi = Pgt26EdwardsMath.scalarLe(delta);
    for (int i = 0; i < n; i++) {
      scalars[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(a[i]).multiply(zBi).mod(Pgt26EdwardsMath.subgroupOrder())
      );
      scalars[n + i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(a[i]).multiply(dBi).negate().mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    byte[] rhs = Pgt26EdwardsMath.multiscalarMul(points, scalars);
    return Arrays.equals(lhs, rhs);
  }

  private static boolean isWellFormed(
      byte[] proverPk,
      byte[][] g,
      byte[][] h,
      byte[][] gCompressed,
      byte[][] hCompressed,
      Pgt26BatchedRddhProof proof
  ) {
    if (!Pgt26EdwardsMath.isValidNonIdentityPoint(proverPk)
        || g == null || h == null || gCompressed == null || hCompressed == null
        || g.length != h.length || g.length != gCompressed.length || h.length != hCompressed.length
        || proof == null
        || !Pgt26EdwardsMath.isValidPrimeSubgroupPoint(proof.commitment)
        || !Pgt26EdwardsMath.isCanonicalScalar(proof.z)) {
      return false;
    }
    for (int i = 0; i < g.length; i++) {
      if (!Pgt26EdwardsMath.isValidNonIdentityPoint(g[i])
          || !Pgt26EdwardsMath.isValidNonIdentityPoint(h[i])
          || !Arrays.equals(g[i], gCompressed[i])
          || !Arrays.equals(h[i], hCompressed[i])) {
        return false;
      }
    }
    return true;
  }

  private static byte[][] concat(byte[][] a, byte[][] b) {
    byte[][] out = new byte[a.length + b.length][];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
