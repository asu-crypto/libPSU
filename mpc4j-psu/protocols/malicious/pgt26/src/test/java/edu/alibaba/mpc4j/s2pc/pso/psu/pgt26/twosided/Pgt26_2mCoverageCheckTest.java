package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import org.junit.Assert;
import org.junit.Test;

import java.util.BitSet;

/**
 * Unit coverage for Figure 6 Step IV expected-index multiset logic.
 */
public class Pgt26_2mCoverageCheckTest {
  @Test
  public void emptyWhenFullIntersection() {
    byte[] a = new byte[32];
    a[0] = 1;
    byte[][] shuffled = new byte[][]{a.clone(), a.clone()};
    byte[][] peerShuffled = new byte[][]{a.clone(), a.clone()};
    int[] perm = new int[]{0, 1};
    BitSet expected = Pgt26_2mCoverageCheck.expectedResponseIndices(shuffled, peerShuffled, perm, 2);
    Assert.assertEquals(0, expected.cardinality());
  }

  @Test
  public void allWhenDisjoint() {
    byte[] a = new byte[32];
    a[0] = 1;
    byte[] b = new byte[32];
    b[0] = 2;
    byte[][] shuffled = new byte[][]{a.clone(), a.clone()};
    byte[][] peerShuffled = new byte[][]{b.clone(), b.clone()};
    int[] perm = new int[]{0, 1};
    BitSet expected = Pgt26_2mCoverageCheck.expectedResponseIndices(shuffled, peerShuffled, perm, 2);
    Assert.assertEquals(2, expected.cardinality());
    Assert.assertTrue(expected.get(0));
    Assert.assertTrue(expected.get(1));
  }

  @Test
  public void multisetAllowsDuplicatePoints() {
    byte[] a = new byte[32];
    a[0] = 7;
    byte[][] shuffled = new byte[][]{a.clone(), a.clone()};
    byte[][] peerShuffled = new byte[][]{a.clone()};
    int[] perm = new int[]{0, 1};
    BitSet expected = Pgt26_2mCoverageCheck.expectedResponseIndices(shuffled, peerShuffled, perm, 2);
    Assert.assertEquals(1, expected.cardinality());
  }

  @Test
  public void validateRejectsEmptyWhenExpectedNonEmpty() {
    BitSet expected = new BitSet();
    expected.set(0);
    try {
      Pgt26_2mCoverageCheck.validateReceivedIndices(new int[0], new byte[0][], expected, 2);
      Assert.fail();
    } catch (MpcAbortException e) {
      Assert.assertTrue(e.getMessage().contains("coverage") || e.getMessage().contains("expected"));
    }
  }

  @Test
  public void validateAllowsReorderedIndices() throws MpcAbortException {
    BitSet expected = new BitSet();
    expected.set(0);
    expected.set(2);
    byte[][] pts = new byte[][]{new byte[32], new byte[32]};
    Pgt26_2mCoverageCheck.validateReceivedIndices(new int[]{2, 0}, pts, expected, 3);
  }

  @Test
  public void validateRejectsDuplicateIndex() {
    BitSet expected = new BitSet();
    expected.set(0);
    expected.set(1);
    try {
      Pgt26_2mCoverageCheck.validateReceivedIndices(
          new int[]{0, 0}, new byte[][]{new byte[32], new byte[32]}, expected, 2
      );
      Assert.fail();
    } catch (MpcAbortException e) {
      Assert.assertTrue(e.getMessage().toLowerCase().contains("duplicate")
          || e.getMessage().contains("Argument"));
    }
  }
}
