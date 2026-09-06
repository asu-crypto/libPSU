package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok;

import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ChaCha12Rng;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Adapted shuffle AoK (reference {@code aok.rs} {@code prove_shuffle_adapted}/{@code verify_shuffle_adapted}).
 */
public final class Pgt26AdaptedShuffleProof {
  public final byte[] cPi;
  public final byte[] cD;
  public final byte[] gD;
  public final byte[] cZ;
  public final byte[] gU;
  public final byte[] cU;
  public final byte[][] x;
  public final byte[] v;
  public final byte[] v2;
  public final KnownContentProof psi;

  public Pgt26AdaptedShuffleProof(
      byte[] cPi, byte[] cD, byte[] gD, byte[] cZ, byte[] gU, byte[] cU,
      byte[][] x, byte[] v, byte[] v2, KnownContentProof psi
  ) {
    this.cPi = cPi;
    this.cD = cD;
    this.gD = gD;
    this.cZ = cZ;
    this.gU = gU;
    this.cU = cU;
    this.x = x;
    this.v = v;
    this.v2 = v2;
    this.psi = psi;
  }

  public static final class KnownContentProof {
    public final byte[] cD;
    public final byte[] cDelta;
    public final byte[] cA;
    public final byte[][] f;
    public final byte[][] fDelta;
    public final byte[] z;
    public final byte[] zDelta;

    public KnownContentProof(
        byte[] cD, byte[] cDelta, byte[] cA, byte[][] f, byte[][] fDelta, byte[] z, byte[] zDelta
    ) {
      this.cD = cD;
      this.cDelta = cDelta;
      this.cA = cA;
      this.f = f;
      this.fDelta = fDelta;
      this.z = z;
      this.zDelta = zDelta;
    }
  }

  public static final class AdaptedShuffleHint {
    public final byte[] cPi;
    public final byte[] cD;
    public final byte[][] d;
    public final byte[] rD;
    public final byte[] rPi;
    public final byte[] rZ;
    public final byte[] u;
    public final byte[] u2;
    public final KnownContentHint hint2;

    public AdaptedShuffleHint(
        byte[] cPi, byte[] cD, byte[][] d, byte[] rD, byte[] rPi, byte[] rZ,
        byte[] u, byte[] u2, KnownContentHint hint2
    ) {
      this.cPi = cPi;
      this.cD = cD;
      this.d = d;
      this.rD = rD;
      this.rPi = rPi;
      this.rZ = rZ;
      this.u = u;
      this.u2 = u2;
      this.hint2 = hint2;
    }
  }

  public static final class KnownContentHint {
    public final byte[][] d;
    public final byte[][] delta;
    public final byte[] rD;
    public final byte[] rDelta;
    public final byte[] rA;
    public final byte[] cD;
    public final byte[] cDelta;

    public KnownContentHint(
        byte[][] d, byte[][] delta, byte[] rD, byte[] rDelta, byte[] rA, byte[] cD, byte[] cDelta
    ) {
      this.d = d;
      this.delta = delta;
      this.rD = rD;
      this.rDelta = rDelta;
      this.rA = rA;
      this.cD = cD;
      this.cDelta = cDelta;
    }
  }

  public static AdaptedShuffleHint preprocess(
      Pgt26PublicParams pp, int[] pi, int n, SecureRandom random
  ) {
    byte[][] d = new byte[n][];
    for (int i = 0; i < n; i++) {
      d[i] = Pgt26EdwardsMath.randomScalar(random);
    }
    byte[] rD = Pgt26EdwardsMath.randomScalar(random);
    byte[] rPi = Pgt26EdwardsMath.randomScalar(random);
    byte[] rZ = Pgt26EdwardsMath.randomScalar(random);
    byte[] u = Pgt26EdwardsMath.randomScalar(random);
    byte[] u2 = Pgt26EdwardsMath.randomScalar(random);
    byte[][] piScalars = new byte[n][];
    for (int i = 0; i < n; i++) {
      piScalars[i] = indexScalar(pi[i]);
    }
    byte[] cPi = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), piScalars, rPi);
    byte[] cD = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), d, rD);
    KnownContentHint hint2 = knownPreprocess(pp, n, random);
    return new AdaptedShuffleHint(cPi, cD, d, rD, rPi, rZ, u, u2, hint2);
  }

  public static Pgt26AdaptedShuffleProof prove(
      Pgt26PublicParams pp,
      byte[] proverPk,
      AdaptedShuffleHint hint,
      byte[][] peerPoints,
      byte[][] peerCompressed,
      byte[][] shuffledCompressed,
      byte[] secret,
      int[] pi
  ) {
    int n = peerPoints.length;
    byte[] cPi = Pgt26EdwardsMath.canonicalizePoint(hint.cPi);
    byte[] cD = Pgt26EdwardsMath.canonicalizePoint(hint.cD);
    byte[] gD = Pgt26EdwardsMath.canonicalizePoint(Pgt26EdwardsMath.multiscalarMul(peerPoints, hint.d));
    Blake2s256 hasher = new Blake2s256();
    hasher.update(Pgt26EdwardsMath.canonicalizePoint(proverPk));
    for (byte[] g : peerCompressed) {
      hasher.update(g);
    }
    for (byte[] h : shuffledCompressed) {
      hasher.update(h);
    }
    hasher.update(cPi);
    hasher.update(cD);
    hasher.update(gD);
    byte[] hz = hasher.digest();
    byte[][] z = Pgt26ChaCha12Rng.scalarsFromSeed(hz, n);
    byte[][] x = new byte[n][];
    BigInteger s = Pgt26EdwardsMath.scalarLe(secret);
    for (int i = 0; i < n; i++) {
      BigInteger zi = Pgt26EdwardsMath.scalarLe(z[pi[i]]);
      BigInteger di = Pgt26EdwardsMath.scalarLe(hint.d[i]);
      x[i] = Pgt26EdwardsMath.scalarToLe(zi.multiply(s).add(di).mod(Pgt26EdwardsMath.subgroupOrder()));
    }
    byte[][] zPerm = new byte[n][];
    for (int i = 0; i < n; i++) {
      zPerm[i] = z[pi[i]];
    }
    byte[] cZ = Pgt26EdwardsMath.canonicalizePoint(Pgt26EdwardsMath.pedersenCommit(pp.generators(n), zPerm, hint.rZ));
    byte[] gU = Pgt26EdwardsMath.canonicalizePoint(Pgt26EdwardsMath.publicKey(hint.u));
    byte[] cU = Pgt26EdwardsMath.canonicalizePoint(Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.pointNeg(Pgt26EdwardsMath.pointMul(cZ, hint.u)),
        Pgt26EdwardsMath.publicKey(hint.u2)
    ));
    hasher = new Blake2s256();
    for (byte[] xi : x) {
      hasher.update(xi);
    }
    hasher.update(cZ);
    hasher.update(gU);
    hasher.update(cU);
    hasher.update(hz);
    byte[] delta = Pgt26EdwardsMath.scalarFromHash(hasher.digest());
    BigInteger dBi = Pgt26EdwardsMath.scalarLe(delta);
    byte[] v = Pgt26EdwardsMath.scalarToLe(s.multiply(dBi).add(Pgt26EdwardsMath.scalarLe(hint.u)).mod(Pgt26EdwardsMath.subgroupOrder()));
    byte[] v2 = Pgt26EdwardsMath.scalarToLe(
        s.multiply(Pgt26EdwardsMath.scalarLe(hint.rZ)).add(Pgt26EdwardsMath.scalarLe(hint.rD))
            .multiply(dBi).add(Pgt26EdwardsMath.scalarLe(hint.u2)).mod(Pgt26EdwardsMath.subgroupOrder())
    );
    byte[] cRho = Pgt26EdwardsMath.pointAdd(cZ, Pgt26EdwardsMath.pointMul(cPi, delta));
    byte[] rRho = Pgt26EdwardsMath.scalarToLe(
        Pgt26EdwardsMath.scalarLe(hint.rZ).add(Pgt26EdwardsMath.scalarLe(hint.rPi).multiply(dBi))
            .mod(Pgt26EdwardsMath.subgroupOrder())
    );
    byte[][] zRho = new byte[n][];
    for (int i = 0; i < n; i++) {
      zRho[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(z[i]).add(dBi.multiply(BigInteger.valueOf(i))).mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    KnownContentProof psi = proveKnown(pp, hint.hint2, zRho, cRho, pi, rRho);
    return new Pgt26AdaptedShuffleProof(
        cPi, cD, gD, cZ, gU, cU, x, v, v2, psi
    );
  }

  public static boolean verify(
      Pgt26PublicParams pp,
      byte[] proverPk,
      byte[][] ownPoints,
      byte[][] shuffledPoints,
      byte[][] ownCompressed,
      byte[][] shuffledCompressed,
      Pgt26AdaptedShuffleProof proof,
      SecureRandom random
  ) {
    return verifyFailureStage(
        pp, proverPk, ownPoints, shuffledPoints, ownCompressed, shuffledCompressed, proof, random
    ) == null;
  }

  /** Returns null on success, or a short stage label for diagnostics/tests. */
  public static String verifyFailureStage(
      Pgt26PublicParams pp,
      byte[] proverPk,
      byte[][] ownPoints,
      byte[][] shuffledPoints,
      byte[][] ownCompressed,
      byte[][] shuffledCompressed,
      Pgt26AdaptedShuffleProof proof,
      SecureRandom random
  ) {
    if (!isWellFormed(pp, proverPk, ownPoints, shuffledPoints, ownCompressed, shuffledCompressed, proof)) {
      return "malformed";
    }
    try {
    int n = ownPoints.length;
    byte[] cPi = Pgt26EdwardsMath.canonicalizePoint(proof.cPi);
    byte[] cD = Pgt26EdwardsMath.canonicalizePoint(proof.cD);
    byte[] gD = Pgt26EdwardsMath.canonicalizePoint(proof.gD);
    byte[] cZ = Pgt26EdwardsMath.canonicalizePoint(proof.cZ);
    byte[] gU = Pgt26EdwardsMath.canonicalizePoint(proof.gU);
    byte[] cU = Pgt26EdwardsMath.canonicalizePoint(proof.cU);
    Blake2s256 hasher = new Blake2s256();
    hasher.update(Pgt26EdwardsMath.canonicalizePoint(proverPk));
    for (byte[] g : ownCompressed) {
      hasher.update(g);
    }
    for (byte[] h : shuffledCompressed) {
      hasher.update(h);
    }
    hasher.update(cPi);
    hasher.update(cD);
    hasher.update(gD);
    byte[] hz = hasher.digest();
    byte[][] z = Pgt26ChaCha12Rng.scalarsFromSeed(hz, n);
    hasher = new Blake2s256();
    for (byte[] xi : proof.x) {
      hasher.update(xi);
    }
    hasher.update(cZ);
    hasher.update(gU);
    hasher.update(cU);
    hasher.update(hz);
    byte[] delta = Pgt26EdwardsMath.scalarFromHash(hasher.digest());
    BigInteger dBi = Pgt26EdwardsMath.scalarLe(delta);
    byte[] cRho = Pgt26EdwardsMath.pointAdd(cZ, Pgt26EdwardsMath.pointMul(cPi, delta));
    byte[][] zRho = new byte[n][];
    for (int i = 0; i < n; i++) {
      zRho[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(z[i]).add(dBi.multiply(BigInteger.valueOf(i))).mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    Blake2s256 psiHasher = new Blake2s256();
    psiHasher.update(cRho);
    for (byte[] mi : zRho) {
      psiHasher.update(mi);
    }
    byte[] psiHx = psiHasher.digest();
    BigInteger psiX = Pgt26EdwardsMath.scalarLe(Pgt26EdwardsMath.scalarFromHash(psiHx));
    psiHasher = new Blake2s256();
    psiHasher.update(proof.psi.cD);
    psiHasher.update(proof.psi.cDelta);
    psiHasher.update(proof.psi.cA);
    psiHasher.update(psiHx);
    BigInteger psiE = Pgt26EdwardsMath.scalarLe(Pgt26EdwardsMath.scalarFromHash(psiHasher.digest()));
    BigInteger fAcc = Pgt26EdwardsMath.scalarLe(proof.psi.f[0]).subtract(psiE.multiply(psiX))
        .mod(Pgt26EdwardsMath.subgroupOrder());
    BigInteger eInv = psiE.modInverse(Pgt26EdwardsMath.subgroupOrder());
    for (int i = 1; i < n; i++) {
      BigInteger exp = Pgt26EdwardsMath.scalarLe(proof.psi.f[i]).subtract(psiE.multiply(psiX))
          .mod(Pgt26EdwardsMath.subgroupOrder());
      fAcc = fAcc.multiply(exp).add(Pgt26EdwardsMath.scalarLe(proof.psi.fDelta[i - 1]))
          .multiply(eInv).mod(Pgt26EdwardsMath.subgroupOrder());
    }
    BigInteger prod = BigInteger.ONE;
    for (byte[] mi : zRho) {
      prod = prod.multiply(Pgt26EdwardsMath.scalarLe(mi).subtract(psiX))
          .mod(Pgt26EdwardsMath.subgroupOrder());
    }
    if (!fAcc.equals(psiE.multiply(prod).mod(Pgt26EdwardsMath.subgroupOrder()))) {
      return "known_content_product";
    }
    byte[] lhsV = Pgt26EdwardsMath.publicKey(proof.v);
    byte[] rhsV = Pgt26EdwardsMath.pointAdd(gU, Pgt26EdwardsMath.pointMul(Pgt26EdwardsMath.canonicalizePoint(proverPk), delta));
    if (!Pgt26EdwardsMath.pointEquals(lhsV, rhsV)) {
      return "v_check";
    }
    byte[] alpha1 = Pgt26EdwardsMath.randomScalar(random);
    byte[] alpha2 = Pgt26EdwardsMath.randomScalar(random);
    byte[] psiCD = Pgt26EdwardsMath.canonicalizePoint(proof.psi.cD);
    byte[] psiCA = Pgt26EdwardsMath.canonicalizePoint(proof.psi.cA);
    byte[] psiCDelta = Pgt26EdwardsMath.canonicalizePoint(proof.psi.cDelta);
    byte[] com1 = Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.pointMul(cRho, Pgt26EdwardsMath.scalarToLe(psiE)), psiCD
    );
    byte[] com2 = Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.pointMul(psiCA, Pgt26EdwardsMath.scalarToLe(psiE)), psiCDelta
    );
    byte[] com3 = Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.pointAdd(
            Pgt26EdwardsMath.pointMul(cZ, proof.v),
            Pgt26EdwardsMath.pointMul(cD, delta)
        ),
        Pgt26EdwardsMath.pointAdd(cU, Pgt26EdwardsMath.pointNeg(Pgt26EdwardsMath.publicKey(proof.v2)))
    );
    byte[] lhs = Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.pointAdd(Pgt26EdwardsMath.pointMul(com1, alpha1), Pgt26EdwardsMath.pointMul(com2, alpha2)),
        com3
    );
    byte[][] fSum = new byte[n][];
    for (int i = 0; i < n; i++) {
      BigInteger a1 = Pgt26EdwardsMath.scalarLe(alpha1);
      BigInteger a2 = Pgt26EdwardsMath.scalarLe(alpha2);
      fSum[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(proof.psi.f[i]).multiply(a1)
              .add(Pgt26EdwardsMath.scalarLe(proof.psi.fDelta[i]).multiply(a2))
              .add(Pgt26EdwardsMath.scalarLe(proof.x[i]).multiply(dBi))
              .mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    byte[] rSum = Pgt26EdwardsMath.scalarToLe(
        Pgt26EdwardsMath.scalarLe(proof.psi.z).multiply(Pgt26EdwardsMath.scalarLe(alpha1))
            .add(Pgt26EdwardsMath.scalarLe(proof.psi.zDelta).multiply(Pgt26EdwardsMath.scalarLe(alpha2)))
            .mod(Pgt26EdwardsMath.subgroupOrder())
    );
    byte[] rhs = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), fSum, rSum);
    if (!Pgt26EdwardsMath.pointEquals(lhs, rhs)) {
      return "batch_pedersen";
    }
    byte[][] points = concat(ownPoints, shuffledPoints);
    byte[][] scalars = new byte[2 * n][];
    for (int i = 0; i < n; i++) {
      scalars[i] = proof.x[i];
      scalars[n + i] = Pgt26EdwardsMath.scalarToLe(Pgt26EdwardsMath.scalarLe(z[i]).negate().mod(Pgt26EdwardsMath.subgroupOrder()));
    }
    byte[] rhs2 = Pgt26EdwardsMath.multiscalarMul(points, scalars);
    if (!Pgt26EdwardsMath.pointEquals(gD, rhs2)) {
      return "multiscalar";
    }
    return null;
    } catch (RuntimeException ex) {
      return "malformed";
    }
  }

  private static boolean isWellFormed(
      Pgt26PublicParams pp,
      byte[] proverPk,
      byte[][] ownPoints,
      byte[][] shuffledPoints,
      byte[][] ownCompressed,
      byte[][] shuffledCompressed,
      Pgt26AdaptedShuffleProof proof
  ) {
    if (pp == null || pp.generators() == null || ownPoints == null || shuffledPoints == null
        || ownCompressed == null || shuffledCompressed == null || proof == null || proof.psi == null
        || !Pgt26EdwardsMath.isValidNonIdentityPoint(proverPk)) {
      return false;
    }
    int n = ownPoints.length;
    if (shuffledPoints.length != n || ownCompressed.length != n || shuffledCompressed.length != n
        || pp.generators().length < n || proof.x == null || proof.x.length != n) {
      return false;
    }
    if (!isPoint(proof.cPi) || !isPoint(proof.cD) || !isPoint(proof.gD) || !isPoint(proof.cZ)
        || !isPoint(proof.gU) || !isPoint(proof.cU) || !isScalar(proof.v) || !isScalar(proof.v2)) {
      return false;
    }
    for (int i = 0; i < n; i++) {
      if (!Pgt26EdwardsMath.isValidNonIdentityPoint(ownPoints[i])
          || !Pgt26EdwardsMath.isValidNonIdentityPoint(shuffledPoints[i])
          || !Pgt26EdwardsMath.isValidNonIdentityPoint(ownCompressed[i])
          || !Pgt26EdwardsMath.isValidNonIdentityPoint(shuffledCompressed[i])
          || !isScalar(proof.x[i])) {
        return false;
      }
    }
    KnownContentProof psi = proof.psi;
    if (!isPoint(psi.cD) || !isPoint(psi.cDelta) || !isPoint(psi.cA)
        || psi.f == null || psi.f.length != n || psi.fDelta == null || psi.fDelta.length != n
        || !isScalar(psi.z) || !isScalar(psi.zDelta)) {
      return false;
    }
    for (int i = 0; i < n; i++) {
      if (!isScalar(psi.f[i]) || !isScalar(psi.fDelta[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean isPoint(byte[] point) {
    return Pgt26EdwardsMath.isValidPoint(point);
  }

  private static boolean isScalar(byte[] scalar) {
    return scalar != null && scalar.length == Ed25519ByteEccUtils.SCALAR_BYTES;
  }

  private static KnownContentHint knownPreprocess(Pgt26PublicParams pp, int n, SecureRandom random) {
    byte[][] d = new byte[n][];
    for (int i = 0; i < n; i++) {
      d[i] = Pgt26EdwardsMath.randomScalar(random);
    }
    byte[] rD = Pgt26EdwardsMath.randomScalar(random);
    byte[] rDelta = Pgt26EdwardsMath.randomScalar(random);
    byte[][] delta = new byte[n][];
    delta[0] = d[0];
    for (int i = 1; i < n - 1; i++) {
      delta[i] = Pgt26EdwardsMath.randomScalar(random);
    }
    delta[n - 1] = Pgt26EdwardsMath.scalarToLe(BigInteger.ZERO);
    byte[] rA = Pgt26EdwardsMath.randomScalar(random);
    byte[] cD = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), d, rD);
    byte[][] d2 = new byte[n][];
    for (int i = 0; i < n - 1; i++) {
      d2[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(delta[i]).negate().multiply(Pgt26EdwardsMath.scalarLe(d[i + 1]))
              .mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    d2[n - 1] = Pgt26EdwardsMath.scalarToLe(BigInteger.ZERO);
    byte[] cDelta = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), d2, rDelta);
    return new KnownContentHint(d, delta, rD, rDelta, rA, cD, cDelta);
  }

  private static KnownContentProof proveKnown(
      Pgt26PublicParams pp,
      KnownContentHint hint,
      byte[][] m,
      byte[] c,
      int[] pi,
      byte[] r
  ) {
    int n = m.length;
    Blake2s256 hasher = new Blake2s256();
    hasher.update(c);
    for (byte[] mi : m) {
      hasher.update(mi);
    }
    byte[] hx = hasher.digest();
    BigInteger xBi = Pgt26EdwardsMath.scalarLe(Pgt26EdwardsMath.scalarFromHash(hx));
    byte[][] a = new byte[n][];
    BigInteger acc = BigInteger.ONE;
    for (int i = 0; i < n; i++) {
      acc = acc.multiply(Pgt26EdwardsMath.scalarLe(m[pi[i]]).subtract(xBi)).mod(Pgt26EdwardsMath.subgroupOrder());
      a[i] = Pgt26EdwardsMath.scalarToLe(acc);
    }
    byte[][] a2 = new byte[n][];
    for (int i = 0; i < n - 1; i++) {
      a2[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(hint.delta[i + 1])
              .subtract(Pgt26EdwardsMath.scalarLe(m[pi[i + 1]]).subtract(xBi).multiply(Pgt26EdwardsMath.scalarLe(hint.delta[i])))
              .subtract(Pgt26EdwardsMath.scalarLe(a[i]).multiply(Pgt26EdwardsMath.scalarLe(hint.d[i + 1])))
              .mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    a2[n - 1] = Pgt26EdwardsMath.scalarToLe(BigInteger.ZERO);
    byte[] cA = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), a2, hint.rA);
    hasher = new Blake2s256();
    hasher.update(hint.cD);
    hasher.update(hint.cDelta);
    hasher.update(cA);
    hasher.update(hx);
    byte[] e = Pgt26EdwardsMath.scalarFromHash(hasher.digest());
    BigInteger eBi = Pgt26EdwardsMath.scalarLe(e);
    byte[][] f = new byte[n][];
    for (int i = 0; i < n; i++) {
      f[i] = Pgt26EdwardsMath.scalarToLe(
          eBi.multiply(Pgt26EdwardsMath.scalarLe(m[pi[i]])).add(Pgt26EdwardsMath.scalarLe(hint.d[i])).mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    byte[] z = Pgt26EdwardsMath.scalarToLe(
        eBi.multiply(Pgt26EdwardsMath.scalarLe(r)).add(Pgt26EdwardsMath.scalarLe(hint.rD)).mod(Pgt26EdwardsMath.subgroupOrder())
    );
    byte[][] fDelta = new byte[n][];
    for (int i = 0; i < n - 1; i++) {
      fDelta[i] = Pgt26EdwardsMath.scalarToLe(
          eBi.multiply(Pgt26EdwardsMath.scalarLe(a2[i]))
              .subtract(Pgt26EdwardsMath.scalarLe(hint.delta[i]).multiply(Pgt26EdwardsMath.scalarLe(hint.d[i + 1])))
              .mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    fDelta[n - 1] = Pgt26EdwardsMath.scalarToLe(BigInteger.ZERO);
    byte[] zDelta = Pgt26EdwardsMath.scalarToLe(
        eBi.multiply(Pgt26EdwardsMath.scalarLe(hint.rA)).add(Pgt26EdwardsMath.scalarLe(hint.rDelta)).mod(Pgt26EdwardsMath.subgroupOrder())
    );
    return new KnownContentProof(hint.cD, hint.cDelta, cA, f, fDelta, z, zDelta);
  }

  static boolean verifyKnown(Pgt26PublicParams pp, byte[][] m, byte[] c, KnownContentProof proof) {
    int n = m.length;
    Blake2s256 hasher = new Blake2s256();
    hasher.update(c);
    for (byte[] mi : m) {
      hasher.update(mi);
    }
    byte[] hx = hasher.digest();
    BigInteger xBi = Pgt26EdwardsMath.scalarLe(Pgt26EdwardsMath.scalarFromHash(hx));
    hasher = new Blake2s256();
    hasher.update(proof.cD);
    hasher.update(proof.cDelta);
    hasher.update(proof.cA);
    hasher.update(hx);
    byte[] e = Pgt26EdwardsMath.scalarFromHash(hasher.digest());
    BigInteger eBi = Pgt26EdwardsMath.scalarLe(e);
    byte[] alpha = Pgt26EdwardsMath.randomScalar(new SecureRandom());
    byte[] lhs = Pgt26EdwardsMath.pointAdd(
        Pgt26EdwardsMath.pointAdd(
            Pgt26EdwardsMath.pointMul(
                Pgt26EdwardsMath.pointAdd(Pgt26EdwardsMath.pointMul(c, e), proof.cD),
                alpha
            ),
            Pgt26EdwardsMath.pointMul(proof.cA, e)
        ),
        proof.cDelta
    );
    byte[][] fSum = new byte[n][];
    for (int i = 0; i < n; i++) {
      fSum[i] = Pgt26EdwardsMath.scalarToLe(
          Pgt26EdwardsMath.scalarLe(alpha).multiply(Pgt26EdwardsMath.scalarLe(proof.f[i]))
              .add(Pgt26EdwardsMath.scalarLe(proof.fDelta[i])).mod(Pgt26EdwardsMath.subgroupOrder())
      );
    }
    byte[] zSum = Pgt26EdwardsMath.scalarToLe(
        Pgt26EdwardsMath.scalarLe(proof.z).multiply(Pgt26EdwardsMath.scalarLe(alpha))
            .add(Pgt26EdwardsMath.scalarLe(proof.zDelta)).mod(Pgt26EdwardsMath.subgroupOrder())
    );
    byte[] rhs = Pgt26EdwardsMath.pedersenCommit(pp.generators(n), fSum, zSum);
    if (!Arrays.equals(lhs, rhs)) {
      return false;
    }
    BigInteger fAcc = Pgt26EdwardsMath.scalarLe(proof.f[0]).subtract(eBi.multiply(xBi)).mod(Pgt26EdwardsMath.subgroupOrder());
    BigInteger eInv = eBi.modInverse(Pgt26EdwardsMath.subgroupOrder());
    for (int i = 1; i < n; i++) {
      BigInteger exp = Pgt26EdwardsMath.scalarLe(proof.f[i]).subtract(eBi.multiply(xBi)).mod(Pgt26EdwardsMath.subgroupOrder());
      fAcc = fAcc.multiply(exp).add(Pgt26EdwardsMath.scalarLe(proof.fDelta[i - 1])).multiply(eInv)
          .mod(Pgt26EdwardsMath.subgroupOrder());
    }
    BigInteger prod = BigInteger.ONE;
    for (byte[] mi : m) {
      prod = prod.multiply(Pgt26EdwardsMath.scalarLe(mi).subtract(xBi)).mod(Pgt26EdwardsMath.subgroupOrder());
    }
    return fAcc.equals(eBi.multiply(prod).mod(Pgt26EdwardsMath.subgroupOrder()));
  }

  private static byte[] indexScalar(int index) {
    byte[] le = new byte[32];
    long v = index;
    for (int i = 0; i < 8; i++) {
      le[i] = (byte) (v & 0xFFL);
      v >>>= 8;
    }
    return Pgt26EdwardsMath.scalarToLe(Pgt26EdwardsMath.scalarLe(le));
  }

  private static byte[][] concat(byte[][] a, byte[][] b) {
    byte[][] out = new byte[a.length + b.length][];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
