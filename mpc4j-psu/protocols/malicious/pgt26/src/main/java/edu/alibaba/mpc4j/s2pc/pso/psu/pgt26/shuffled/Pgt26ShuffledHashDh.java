package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.shuffled;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26CurveOps;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Semi-honest shuffled HashDH core (reference {@code onesided.rs} {@code PartySender}/{@code PartyReceiver}).
 */
public final class Pgt26ShuffledHashDh {
  private Pgt26ShuffledHashDh() {
    // empty
  }

  public static byte[][] blindOwnItems(byte[][] items16, byte[] scalar) {
    byte[][] blinded = new byte[items16.length][];
    for (int i = 0; i < items16.length; i++) {
      byte[] h = Pgt26CurveOps.hashToCurve(items16[i]);
      blinded[i] = Pgt26CurveOps.scalarMul(h, scalar);
    }
    return blinded;
  }

  public static byte[][] blindPeerItems(byte[][] peerBlinded, byte[] scalar) {
    byte[][] out = new byte[peerBlinded.length][];
    for (int i = 0; i < peerBlinded.length; i++) {
      out[i] = Pgt26CurveOps.scalarMul(peerBlinded[i], scalar);
    }
    return out;
  }

  /**
   * Server shuffle step: e_out[i] = peerBlinded[permutation[i]]^scalar (reference {@code blind_and_shuffle}).
   */
  public static byte[][] shuffleBlind(byte[][] peerBlinded, byte[] scalar, int[] permutation) {
    byte[][] shuffled = new byte[permutation.length][];
    for (int i = 0; i < permutation.length; i++) {
      shuffled[i] = Pgt26CurveOps.scalarMul(peerBlinded[permutation[i]], scalar);
    }
    return shuffled;
  }

  public static int[] randomPermutation(int n, SecureRandom random) {
    List<Integer> perm = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      perm.add(i);
    }
    Collections.shuffle(perm, random);
    int[] out = new int[n];
    for (int i = 0; i < n; i++) {
      out[i] = perm.get(i);
    }
    return out;
  }

  /**
   * Receiver membership: b[j] = true iff blinded sender point j is in shuffled receiver-blinded set.
   */
  public static boolean[] membershipBits(byte[][] senderBlinded, byte[][] receiverBlindedShuffled) {
    boolean[] bits = new boolean[senderBlinded.length];
    for (int j = 0; j < senderBlinded.length; j++) {
      bits[j] = false;
      for (byte[] e : receiverBlindedShuffled) {
        if (BytesUtils.equals(senderBlinded[j], e)) {
          bits[j] = true;
          break;
        }
      }
    }
    return bits;
  }

  public static byte[][] itemsFromByteBuffers(java.util.Collection<ByteBuffer> elements, int elementByteLength) {
    byte[][] items = new byte[elements.size()][];
    int i = 0;
    for (ByteBuffer e : elements) {
      ByteBuffer dup = e.duplicate();
      byte[] item = new byte[elementByteLength];
      dup.get(item);
      items[i++] = item;
    }
    return items;
  }

  public static void assertItemLength(int elementByteLength) {
    if (elementByteLength != Pgt26Constants.ITEM_BYTE_LENGTH) {
      throw new IllegalArgumentException("PGT26 requires element_byte_length = " + Pgt26Constants.ITEM_BYTE_LENGTH);
    }
  }
}
