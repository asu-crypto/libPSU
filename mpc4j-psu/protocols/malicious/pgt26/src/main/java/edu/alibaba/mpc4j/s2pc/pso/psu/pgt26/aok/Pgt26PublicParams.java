package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok;

import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Pedersen generators f_1..f_n (reference {@code aok.rs} {@code PublicParams}).
 */
public final class Pgt26PublicParams {
  private final byte[][] generators;

  private Pgt26PublicParams(byte[][] generators) {
    this.generators = generators;
  }

  public static Pgt26PublicParams setup(int n) {
    byte[][] f = new byte[n][];
    for (int i = 0; i < n; i++) {
      f[i] = Pgt26EdwardsMath.hashToCurve("PGT26_PP_F".getBytes(StandardCharsets.UTF_8), intLe(i));
    }
    return new Pgt26PublicParams(f);
  }

  /** All generators allocated at {@link #setup(int)} (max set size for this session). */
  public byte[][] generators() {
    return generators;
  }

  /**
   * First {@code n} Pedersen generators for a proof over {@code n} peer points.
   * Public params are sized to max(client, server); shuffle proofs use peer count only.
   */
  public byte[][] generators(int n) {
    if (n < 0 || n > generators.length) {
      throw new IllegalArgumentException(
          "requested " + n + " generators but setup has " + generators.length
      );
    }
    if (n == generators.length) {
      return generators;
    }
    return Arrays.copyOfRange(generators, 0, n);
  }

  private static byte[] intLe(int v) {
    return new byte[]{
        (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24),
    };
  }
}
