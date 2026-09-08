package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Permutation;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26AdaptedShuffleProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26BatchedRddhProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26PublicParams;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Proof commitments must lie in the prime-order subgroup (identity allowed).
 * Mixed-torsion injections must fail as {@code malformed}, not be cofactor-cleared.
 */
public class Pgt26ProofPrimeSubgroupTest {
  private static final SecureRandom RANDOM = new SecureRandom();

  @Test
  public void identityAcceptedByPrimeSubgroupRejectedByNonIdentity() {
    byte[] identity = Pgt26EdwardsMath.EIGHT_TORSION[0];
    Assert.assertTrue(Pgt26EdwardsMath.isValidPrimeSubgroupPoint(identity));
    Assert.assertFalse(Pgt26EdwardsMath.isValidNonIdentityPoint(identity));
  }

  @Test
  public void pureTorsionAndBasePlusTorsionRejected() {
    byte[] base = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(RANDOM));
    for (int i = 1; i < Pgt26EdwardsMath.EIGHT_TORSION.length; i++) {
      byte[] torsion = Pgt26EdwardsMath.EIGHT_TORSION[i];
      Assert.assertFalse(Pgt26EdwardsMath.isValidPrimeSubgroupPoint(torsion));
      Assert.assertFalse(Pgt26EdwardsMath.isValidNonIdentityPoint(torsion));
      byte[] mixed = Pgt26EdwardsMath.pointAdd(base, torsion);
      Assert.assertFalse(Pgt26EdwardsMath.isValidPrimeSubgroupPoint(mixed));
      Assert.assertFalse(Pgt26EdwardsMath.isValidNonIdentityPoint(mixed));
    }
  }

  @Test
  public void adaptedShuffleProofCommitmentsRejectMixedTorsion() throws Exception {
    int n = 4;
    Pgt26PublicParams pp = Pgt26PublicParams.setup(n);
    Pgt26Permutation perm = Pgt26Permutation.random(n, RANDOM);
    byte[] secret = Pgt26EdwardsMath.randomScalar(RANDOM);
    byte[] pk = Pgt26EdwardsMath.publicKey(secret);
    byte[][] peer = new byte[n][];
    for (int i = 0; i < n; i++) {
      peer[i] = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(RANDOM));
    }
    Pgt26AdaptedShuffleProof.AdaptedShuffleHint hint =
        Pgt26AdaptedShuffleProof.preprocess(pp, perm.perm, n, RANDOM);
    byte[][] shuffled = new byte[n][];
    for (int i = 0; i < n; i++) {
      shuffled[i] = Pgt26EdwardsMath.pointMul(peer[perm.inv[i]], secret);
    }
    Pgt26AdaptedShuffleProof good = Pgt26AdaptedShuffleProof.prove(
        pp, pk, hint, peer, peer, shuffled, secret, perm.perm
    );
    Assert.assertNull(Pgt26AdaptedShuffleProof.verifyFailureStage(
        pp, pk, peer, shuffled, peer, shuffled, good, RANDOM
    ));

    Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>[] mutants = mutants(good);
    String[] names = {
        "cPi", "cD", "gD", "cZ", "gU", "cU", "psi.cD", "psi.cDelta", "psi.cA"
    };
    Assert.assertEquals(names.length, mutants.length);
    for (int i = 0; i < mutants.length; i++) {
      Pgt26AdaptedShuffleProof bad = mutants[i].apply(good);
      byte[] packed = Pgt26_2mWire.packAdapted(bad, n);
      Pgt26AdaptedShuffleProof roundTrip = Pgt26_2mWire.unpackAdapted(packed, n);
      Assert.assertEquals(
          names[i],
          "malformed",
          Pgt26AdaptedShuffleProof.verifyFailureStage(
              pp, pk, peer, shuffled, peer, shuffled, roundTrip, RANDOM
          )
      );
    }
  }

  @Test
  public void rddhCommitmentRejectsMixedTorsion() throws Exception {
    int n = 4;
    byte[] secret = Pgt26EdwardsMath.randomScalar(RANDOM);
    byte[] pk = Pgt26EdwardsMath.publicKey(secret);
    byte[][] g = new byte[n][];
    byte[][] h = new byte[n][];
    for (int i = 0; i < n; i++) {
      g[i] = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(RANDOM));
      h[i] = Pgt26EdwardsMath.pointMul(g[i], secret);
    }
    Pgt26BatchedRddhProof good = Pgt26BatchedRddhProof.prove(pk, g, h, secret, RANDOM);
    Assert.assertTrue(Pgt26BatchedRddhProof.verify(pk, g, h, g, h, good));
    byte[] mixedCommitment = Pgt26EdwardsMath.pointAdd(good.commitment, Pgt26EdwardsMath.EIGHT_TORSION[1]);
    Pgt26BatchedRddhProof bad = new Pgt26BatchedRddhProof(mixedCommitment, good.z);
    byte[] packed = Pgt26_2mWire.packBatched(bad);
    Pgt26BatchedRddhProof roundTrip = Pgt26_2mWire.unpackBatched(packed);
    Assert.assertFalse(Pgt26BatchedRddhProof.verify(pk, g, h, g, h, roundTrip));
  }

  @SuppressWarnings("unchecked")
  private static Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>[] mutants(
      Pgt26AdaptedShuffleProof good
  ) {
    byte[] t = Pgt26EdwardsMath.EIGHT_TORSION[1];
    return new Function[]{
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            Pgt26EdwardsMath.pointAdd(p.cPi, t), p.cD, p.gD, p.cZ, p.gU, p.cU,
            p.psi.cD, p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, Pgt26EdwardsMath.pointAdd(p.cD, t), p.gD, p.cZ, p.gU, p.cU,
            p.psi.cD, p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, Pgt26EdwardsMath.pointAdd(p.gD, t), p.cZ, p.gU, p.cU,
            p.psi.cD, p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, p.gD, Pgt26EdwardsMath.pointAdd(p.cZ, t), p.gU, p.cU,
            p.psi.cD, p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, p.gD, p.cZ, Pgt26EdwardsMath.pointAdd(p.gU, t), p.cU,
            p.psi.cD, p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, p.gD, p.cZ, p.gU, Pgt26EdwardsMath.pointAdd(p.cU, t),
            p.psi.cD, p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, p.gD, p.cZ, p.gU, p.cU,
            Pgt26EdwardsMath.pointAdd(p.psi.cD, t), p.psi.cDelta, p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, p.gD, p.cZ, p.gU, p.cU,
            p.psi.cD, Pgt26EdwardsMath.pointAdd(p.psi.cDelta, t), p.psi.cA),
        (Function<Pgt26AdaptedShuffleProof, Pgt26AdaptedShuffleProof>) p -> withPoints(p,
            p.cPi, p.cD, p.gD, p.cZ, p.gU, p.cU,
            p.psi.cD, p.psi.cDelta, Pgt26EdwardsMath.pointAdd(p.psi.cA, t)),
    };
  }

  private static Pgt26AdaptedShuffleProof withPoints(
      Pgt26AdaptedShuffleProof p,
      byte[] cPi, byte[] cD, byte[] gD, byte[] cZ, byte[] gU, byte[] cU,
      byte[] psiCd, byte[] psiCDelta, byte[] psiCa
  ) {
    Pgt26AdaptedShuffleProof.KnownContentProof psi = new Pgt26AdaptedShuffleProof.KnownContentProof(
        psiCd, psiCDelta, psiCa, clone2d(p.psi.f), clone2d(p.psi.fDelta),
        Arrays.copyOf(p.psi.z, p.psi.z.length), Arrays.copyOf(p.psi.zDelta, p.psi.zDelta.length)
    );
    return new Pgt26AdaptedShuffleProof(
        cPi, cD, gD, cZ, gU, cU, clone2d(p.x),
        Arrays.copyOf(p.v, p.v.length), Arrays.copyOf(p.v2, p.v2.length), psi
    );
  }

  private static byte[][] clone2d(byte[][] in) {
    byte[][] out = new byte[in.length][];
    for (int i = 0; i < in.length; i++) {
      out[i] = Arrays.copyOf(in[i], in[i].length);
    }
    return out;
  }
}
