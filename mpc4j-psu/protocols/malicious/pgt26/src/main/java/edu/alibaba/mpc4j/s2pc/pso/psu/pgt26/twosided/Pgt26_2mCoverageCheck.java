package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Figure 6 Step IV exact coverage check: the Round-4 index set must equal the multiset
 * difference of double-blinded points (peer-only positions), not merely verify an RDDH
 * proof on an adversary-chosen subset.
 */
public final class Pgt26_2mCoverageCheck {
  private Pgt26_2mCoverageCheck() {
  }

  /**
   * Compute expected response indices for peer-only items.
   *
   * @param shuffleShuffled our Round-3 shuffled/double-blinded array of the peer's points
   * @param peerShuffled    peer's Round-3 shuffled/double-blinded array of our points
   * @param perm            {@code party.permutation.perm} ({@code shuffled[pos] = peerPoints[inv[pos]]^s})
   * @param peerPointsLen   number of peer original points ({@code = perm.length})
   */
  public static BitSet expectedResponseIndices(
      byte[][] shuffleShuffled, byte[][] peerShuffled, int[] perm, int peerPointsLen
  ) {
    if (shuffleShuffled == null || peerShuffled == null || perm == null) {
      throw new IllegalArgumentException("null coverage inputs");
    }
    if (perm.length != peerPointsLen || perm.length != shuffleShuffled.length) {
      throw new IllegalArgumentException("perm / shuffle length mismatch");
    }
    Map<ByteBuffer, Integer> remaining = new HashMap<>(peerShuffled.length * 2);
    for (byte[] p : peerShuffled) {
      ByteBuffer key = ByteBuffer.wrap(BytesUtils.clone(p));
      remaining.merge(key, 1, Integer::sum);
    }
    BitSet expected = new BitSet(shuffleShuffled.length);
    for (int peerOriginalIndex = 0; peerOriginalIndex < peerPointsLen; peerOriginalIndex++) {
      int pos = perm[peerOriginalIndex];
      if (pos < 0 || pos >= shuffleShuffled.length) {
        throw new IllegalArgumentException("permutation index out of range");
      }
      ByteBuffer candidate = ByteBuffer.wrap(shuffleShuffled[pos]);
      Integer count = remaining.get(candidate);
      if (count != null && count > 0) {
        if (count == 1) {
          remaining.remove(candidate);
        } else {
          remaining.put(candidate, count - 1);
        }
      } else {
        expected.set(pos);
      }
    }
    return expected;
  }

  /**
   * Validate Round-4 indices against {@link #expectedResponseIndices}. Reordering of
   * (index, unblinded-point) pairs is allowed; the index multiset must match exactly.
   */
  public static void validateReceivedIndices(
      int[] peerInd, byte[][] peerUnblinded, BitSet expected, int shuffledLength
  ) throws MpcAbortException {
    MpcAbortPreconditions.checkArgument(peerInd != null && peerUnblinded != null);
    MpcAbortPreconditions.checkArgument(peerInd.length == peerUnblinded.length);
    int expectedCard = expected.cardinality();
    MpcAbortPreconditions.checkArgument(
        peerInd.length == expectedCard,
        "Round-4 coverage: expected " + expectedCard + " indices, got " + peerInd.length
    );
    BitSet seen = new BitSet(shuffledLength);
    BitSet received = new BitSet(shuffledLength);
    for (int idx : peerInd) {
      MpcAbortPreconditions.checkArgument(idx >= 0 && idx < shuffledLength, "Round-4 index out of range");
      MpcAbortPreconditions.checkArgument(!seen.get(idx), "Round-4 duplicate index");
      seen.set(idx);
      received.set(idx);
    }
    MpcAbortPreconditions.checkArgument(received.equals(expected), "Round-4 index set != expected coverage");
  }
}
