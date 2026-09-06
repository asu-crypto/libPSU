package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * Test-only hooks for malicious-behavior unit tests (default {@link Mode#NONE} in production).
 */
public final class Pgt26TestHooks {
  public enum Mode {
    NONE,
    /** Replace φ_j with random bytes for the first non-intersection OT slot. */
    INVALID_PROOF,
    /** Send wrong item bytes with a proof for a different item. */
    WRONG_ITEM_BINDING,
  }

  public static volatile Mode mode = Mode.NONE;

  /**
   * Optional item universe for {@code H^{-1}} during tests when {@code M2P} fallback is active.
   */
  public static volatile List<byte[]> candidateItems = null;

  /** Precomputed encoded point → item for fast test recovery. */
  public static volatile Map<ByteBuffer, byte[]> candidatePointCache = null;

  private Pgt26TestHooks() {
    // empty
  }

  public static void reset() {
    mode = Mode.NONE;
    candidateItems = null;
    candidatePointCache = null;
  }
}
