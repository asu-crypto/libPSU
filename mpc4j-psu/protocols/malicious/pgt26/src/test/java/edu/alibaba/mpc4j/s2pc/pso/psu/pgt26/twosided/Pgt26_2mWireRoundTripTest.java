package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Permutation;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26AdaptedShuffleProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26PublicParams;
import org.junit.Assert;
import org.junit.Test;

import java.security.SecureRandom;

/**
 * Pack/unpack round-trip for adapted shuffle proofs (isolates {@link Pgt26_2mWire} from Netty).
 */
public class Pgt26_2mWireRoundTripTest {
  private static final SecureRandom RANDOM = new SecureRandom();

  @Test
  public void packUnpackPreservesVerify() throws Exception {
    int n = 1 << 10;
    Pgt26PublicParams pp = Pgt26PublicParams.setup(n);
    byte[] sk = Pgt26EdwardsMath.randomScalar(RANDOM);
    byte[] pk = Pgt26EdwardsMath.publicKey(sk);
    byte[][] peer = new byte[n][];
    for (int i = 0; i < n; i++) {
      peer[i] = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(RANDOM));
    }
    Pgt26Permutation permutation = Pgt26Permutation.random(n, RANDOM);
    Pgt26AdaptedShuffleProof.AdaptedShuffleHint hint =
        Pgt26AdaptedShuffleProof.preprocess(pp, permutation.perm, n, RANDOM);
    byte[][] shuffled = new byte[n][];
    for (int i = 0; i < n; i++) {
      shuffled[i] = Pgt26EdwardsMath.pointMul(peer[permutation.inv[i]], sk);
    }
    Pgt26AdaptedShuffleProof proof = Pgt26AdaptedShuffleProof.prove(
        pp, pk, hint, peer, peer, shuffled, sk, permutation.perm
    );
    byte[] wire = Pgt26_2mWire.packAdapted(proof, n);
    Pgt26AdaptedShuffleProof decoded = Pgt26_2mWire.unpackAdapted(wire);
    Assert.assertTrue(Pgt26AdaptedShuffleProof.verify(
        pp, pk, peer, shuffled, peer, shuffled, decoded, RANDOM
    ));
  }
}
