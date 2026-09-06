package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok;

import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Permutation;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

public class Pgt26AdaptedShuffleProofTest {
  @Test
  public void testPGT26_AOK_emptyRddhBatchRoundtrip() {
    SecureRandom random = new SecureRandom();
    byte[] secret = Pgt26EdwardsMath.randomScalar(random);
    byte[] pk = Pgt26EdwardsMath.publicKey(secret);
    byte[][] empty = new byte[0][];
    Pgt26BatchedRddhProof proof = Pgt26BatchedRddhProof.prove(pk, empty, empty, secret, random);
    Assert.assertTrue(Pgt26BatchedRddhProof.verify(pk, empty, empty, empty, empty, proof));
  }

  @Test
  public void testPGT26_AOK_nonEmptyRddhBatchRoundtrip() {
    int n = 8;
    SecureRandom random = new SecureRandom();
    byte[] secret = Pgt26EdwardsMath.randomScalar(random);
    byte[] pk = Pgt26EdwardsMath.publicKey(secret);
    byte[][] g = new byte[n][];
    byte[][] h = new byte[n][];
    for (int i = 0; i < n; i++) {
      g[i] = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(random));
      h[i] = Pgt26EdwardsMath.pointMul(g[i], secret);
    }
    Pgt26BatchedRddhProof proof = Pgt26BatchedRddhProof.prove(pk, g, h, secret, random);
    Assert.assertTrue(Pgt26BatchedRddhProof.verify(pk, g, h, g, h, proof));
  }

  @Test
  public void testPGT26_AOK_lowOrderPointsRejected() {
    byte[] torsion = Pgt26EdwardsMath.EIGHT_TORSION[1];
    Assert.assertTrue(Pgt26EdwardsMath.isValidPoint(torsion));
    Assert.assertFalse(Pgt26EdwardsMath.isValidNonIdentityPoint(torsion));
  }

  @Test
  public void testPGT26_AOK_preprocessWithMaxNLargerThanPeer() {
    int maxN = 64;
    int peerN = 8;
    SecureRandom random = new SecureRandom();
    Pgt26PublicParams pp = Pgt26PublicParams.setup(maxN);
    Pgt26Permutation perm = Pgt26Permutation.random(peerN, random);
    Pgt26AdaptedShuffleProof.preprocess(pp, perm.perm, peerN, random);
  }

  @Test
  public void testPGT26_AOK_2p5_shuffleRoundtrip() {
    int n = 8;
    SecureRandom random = new SecureRandom();
    Pgt26PublicParams pp = Pgt26PublicParams.setup(n);
    Pgt26Permutation perm = Pgt26Permutation.random(n, random);
    byte[] secret = Pgt26EdwardsMath.randomScalar(random);
    byte[] pk = Pgt26EdwardsMath.publicKey(secret);
    byte[][] peer = new byte[n][];
    for (int i = 0; i < n; i++) {
      peer[i] = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(random));
    }
    Pgt26AdaptedShuffleProof.AdaptedShuffleHint hint =
        Pgt26AdaptedShuffleProof.preprocess(pp, perm.perm, n, random);
    byte[][] shuffled = new byte[n][];
    for (int i = 0; i < n; i++) {
      shuffled[i] = Pgt26EdwardsMath.pointMul(peer[perm.inv[i]], secret);
    }
    Pgt26AdaptedShuffleProof proof = Pgt26AdaptedShuffleProof.prove(
        pp, pk, hint, peer, peer, shuffled, secret, perm.perm
    );
    Assert.assertTrue(Pgt26AdaptedShuffleProof.verify(
        pp, pk, peer, shuffled, peer, shuffled, proof, random
    ));
  }
}
