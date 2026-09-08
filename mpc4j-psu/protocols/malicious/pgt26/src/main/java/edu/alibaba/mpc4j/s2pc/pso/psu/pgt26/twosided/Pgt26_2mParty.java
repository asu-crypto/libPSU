package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Permutation;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26AdaptedShuffleProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26BatchedRddhProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26PublicParams;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26FeistelPrp256;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26InvertibleMap;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Symmetric party state (reference {@code twosided.rs} {@code Party}).
 */
public final class Pgt26_2mParty {
  public final byte[] sk8;
  public final byte[] secret;
  public final byte[] secretInv;
  public final byte[] secret8Inv;
  public final byte[] publicKey;
  public final Pgt26Permutation permutation;
  public final Pgt26AdaptedShuffleProof.AdaptedShuffleHint shuffleHint;
  public final Pgt26FeistelPrp256 permut;
  public final byte[][] ownItems;
  public final int ownSize;
  /** Set when {@link #finalResponse} rejects a shuffle proof (diagnostics). */
  public String lastShuffleVerifyFailure;
  /** Set when {@link #revealPeerItems} rejects the peer RDDH proof or cannot decode a point. */
  public String lastRevealFailure;

  private Pgt26_2mParty(
      byte[] sk8, byte[] secret, byte[] secretInv, byte[] secret8Inv, byte[] publicKey,
      Pgt26Permutation permutation, Pgt26AdaptedShuffleProof.AdaptedShuffleHint shuffleHint,
      Pgt26FeistelPrp256 permut, byte[][] ownItems
  ) {
    this.sk8 = sk8;
    this.secret = secret;
    this.secretInv = secretInv;
    this.secret8Inv = secret8Inv;
    this.publicKey = publicKey;
    this.permutation = permutation;
    this.shuffleHint = shuffleHint;
    this.permut = permut;
    this.ownItems = ownItems;
    this.ownSize = ownItems.length;
  }

  public static Pgt26_2mParty create(
      byte[][] ownItems16,
      byte[] aesKey16,
      Pgt26PublicParams pp,
      int otherSize,
      SecureRandom random
  ) {
    byte[] raw = new byte[32];
    random.nextBytes(raw);
    byte[] sk8 = Pgt26EdwardsMath.clampInteger(raw);
    byte[] secret = Pgt26EdwardsMath.secretFromClamped(sk8);
    byte[] secretInv = Pgt26EdwardsMath.secretInverse(secret);
    byte[] secret8Inv = Pgt26EdwardsMath.secret8Inverse(secret, secretInv);
    byte[] pk = Pgt26EdwardsMath.canonicalizePoint(Pgt26EdwardsMath.publicKey(secret));
    Pgt26Permutation perm = Pgt26Permutation.random(otherSize, random);
    Pgt26FeistelPrp256 prp = new Pgt26FeistelPrp256(aesKey16);
    Pgt26AdaptedShuffleProof.AdaptedShuffleHint hint =
        Pgt26AdaptedShuffleProof.preprocess(pp, perm.perm, otherSize, random);
    return new Pgt26_2mParty(sk8, secret, secretInv, secret8Inv, pk, perm, hint, prp, ownItems16);
  }

  /** Step 1* / local gen: σ, blinded points (reference {@code Party::gen}). */
  public GenOutput gen() {
    byte[][] points = new byte[ownSize][];
    Blake2s256 hasher = new Blake2s256();
    hasher.update(Pgt26EdwardsMath.canonicalizePoint(publicKey));
    for (int i = 0; i < ownSize; i++) {
      byte[] p = Pgt26EdwardsMath.canonicalizePoint(
          Pgt26EdwardsMath.pointMul(
              Pgt26EdwardsMath.cofactorClear(Pgt26InvertibleMap.hashToPoint(ownItems[i], permut)), secret
          )
      );
      points[i] = p;
      hasher.update(p);
    }
    return new GenOutput(hasher.digest(), publicKey, points);
  }

  public boolean verifySigma(byte[] peerSigma, byte[] peerPk, byte[][] peerPointsCompressed) {
    Blake2s256 hasher = new Blake2s256();
    hasher.update(Pgt26EdwardsMath.canonicalizePoint(peerPk));
    for (byte[] p : peerPointsCompressed) {
      hasher.update(p);
      if (!Pgt26EdwardsMath.isValidNonIdentityPoint(p)) {
        return false;
      }
    }
    return Arrays.equals(hasher.digest(), peerSigma);
  }

  public ShuffleOutput blindShuffle(
      Pgt26PublicParams pp, byte[][] peerPoints, byte[][] peerCompressed
  ) {
    int n = peerPoints.length;
    byte[][] shuffled = new byte[n][];
    for (int i = 0; i < n; i++) {
      shuffled[i] = Pgt26EdwardsMath.canonicalizePoint(
          Pgt26EdwardsMath.pointMul(peerPoints[permutation.inv[i]], secret)
      );
    }
    Pgt26AdaptedShuffleProof proof = Pgt26AdaptedShuffleProof.prove(
        pp, publicKey, shuffleHint, peerPoints, peerCompressed, shuffled, secret, permutation.perm
    );
    return new ShuffleOutput(proof, shuffled);
  }

  public UnblindOutput finalResponse(
      Pgt26PublicParams pp,
      byte[] peerPk,
      byte[][] ownPoints,
      byte[][] shuffledPeer,
      byte[][] ownCompressed,
      byte[][] shuffledCompressed,
      byte[][] peerOriginalCompressed,
      Pgt26AdaptedShuffleProof proof,
      SecureRandom random
  ) {
    String shuffleFail = Pgt26AdaptedShuffleProof.verifyFailureStage(
        pp, peerPk, ownPoints, shuffledPeer, ownCompressed, shuffledCompressed, proof, random
    );
    if (shuffleFail != null) {
      lastShuffleVerifyFailure = shuffleFail;
      return null;
    }
    Set<ByteBuffer> peerSet = new HashSet<>();
    for (byte[] p : peerOriginalCompressed) {
      peerSet.add(ByteBuffer.wrap(BytesUtils.clone(p)));
    }
    int n = ownPoints.length;
    int count = 0;
    for (int i = 0; i < n; i++) {
      byte[] p = Pgt26EdwardsMath.pointMul(shuffledPeer[i], secretInv);
      if (!peerSet.contains(ByteBuffer.wrap(p))) {
        count++;
      }
    }
    int[] indices = new int[count];
    byte[][] unblinded = new byte[count][];
    byte[][] shrinked = new byte[count][];
    int k = 0;
    for (int i = 0; i < n; i++) {
      byte[] p = Pgt26EdwardsMath.pointMul(shuffledPeer[i], secretInv);
      if (!peerSet.contains(ByteBuffer.wrap(p))) {
        unblinded[k] = p;
        shrinked[k] = shuffledCompressed[i];
        indices[k] = i;
        k++;
      }
    }
    Pgt26BatchedRddhProof ddh = Pgt26BatchedRddhProof.prove(publicKey, unblinded, shrinked, secret, random);
    return new UnblindOutput(unblinded, indices, ddh);
  }

  public byte[][] revealPeerItems(
      byte[] peerPk,
      byte[][] peerUnblinded,
      byte[][] peerShrinkedCompressed,
      Pgt26BatchedRddhProof proof
  ) {
    if (!Pgt26EdwardsMath.isValidNonIdentityPoint(peerPk)
        || peerUnblinded == null || peerShrinkedCompressed == null
        || peerUnblinded.length != peerShrinkedCompressed.length) {
      lastRevealFailure = "malformed reveal input";
      return null;
    }
    for (int i = 0; i < peerUnblinded.length; i++) {
      if (!Pgt26EdwardsMath.isValidNonIdentityPoint(peerUnblinded[i])
          || !Pgt26EdwardsMath.isValidNonIdentityPoint(peerShrinkedCompressed[i])) {
        lastRevealFailure = "malformed reveal point";
        return null;
      }
    }
    if (!Pgt26BatchedRddhProof.verify(
        peerPk, peerUnblinded, peerShrinkedCompressed,
        peerUnblinded, peerShrinkedCompressed, proof
    )) {
      lastRevealFailure = "bad RDDH proof";
      return null;
    }
    byte[][] items = new byte[peerUnblinded.length][];
    for (int i = 0; i < peerUnblinded.length; i++) {
      byte[] scaled = Pgt26EdwardsMath.pointMul(peerUnblinded[i], secret8Inv);
      Optional<byte[]> item = Pgt26InvertibleMap.recoverFromPoint(scaled, permut);
      if (item.isEmpty() || !Pgt26InvertibleMap.hasValidPadding(item.get())) {
        lastRevealFailure = "decode failed";
        return null;
      }
      items[i] = item.get();
    }
    lastRevealFailure = null;
    return items;
  }

  public static final class GenOutput {
    public final byte[] sigma;
    public final byte[] pk;
    public final byte[][] points;

    GenOutput(byte[] sigma, byte[] pk, byte[][] points) {
      this.sigma = sigma;
      this.pk = pk;
      this.points = points;
    }
  }

  public static final class ShuffleOutput {
    public final Pgt26AdaptedShuffleProof proof;
    public final byte[][] shuffled;

    ShuffleOutput(Pgt26AdaptedShuffleProof proof, byte[][] shuffled) {
      this.proof = proof;
      this.shuffled = shuffled;
    }
  }

  public static final class UnblindOutput {
    public final byte[][] unblinded;
    public final int[] indices;
    public final Pgt26BatchedRddhProof proof;

    UnblindOutput(byte[][] unblinded, int[] indices, Pgt26BatchedRddhProof proof) {
      this.unblinded = unblinded;
      this.indices = indices;
      this.proof = proof;
    }
  }
}
