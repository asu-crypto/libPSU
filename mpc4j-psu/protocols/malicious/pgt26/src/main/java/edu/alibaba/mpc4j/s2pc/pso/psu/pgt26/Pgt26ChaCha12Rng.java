package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import org.bouncycastle.crypto.engines.ChaChaEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.util.Arrays;

/**
 * ChaCha12 PRNG from a 32-byte seed (reference {@code aok.rs} {@code ChaCha12Rng::from_seed}).
 */
public final class Pgt26ChaCha12Rng {
  private final ChaChaEngine engine;
  private final byte[] buf = new byte[64];
  private int bufPos = 64;

  public Pgt26ChaCha12Rng(byte[] seed32) {
    if (seed32.length != 32) {
      throw new IllegalArgumentException("seed must be 32 bytes");
    }
    engine = new ChaChaEngine(12);
    engine.init(true, new ParametersWithIV(new KeyParameter(seed32), new byte[8]));
  }

  public byte[] nextScalar() {
    byte[] out = new byte[64];
    nextBytes(out);
    return Pgt26EdwardsMath.scalarFromHash(out);
  }

  public void nextBytes(byte[] dest) {
    int offset = 0;
    while (offset < dest.length) {
      if (bufPos >= buf.length) {
        engine.processBytes(buf, 0, buf.length, buf, 0);
        bufPos = 0;
      }
      int take = Math.min(dest.length - offset, buf.length - bufPos);
      System.arraycopy(buf, bufPos, dest, offset, take);
      bufPos += take;
      offset += take;
    }
  }

  public static byte[][] scalarsFromSeed(byte[] seed32, int count) {
    Pgt26ChaCha12Rng rng = new Pgt26ChaCha12Rng(seed32);
    byte[][] out = new byte[count][];
    for (int i = 0; i < count; i++) {
      out[i] = rng.nextScalar();
    }
    return out;
  }

  public static boolean seedEquals(byte[] a, byte[] b) {
    return Arrays.equals(a, b);
  }

  public static byte[] cloneSeed(byte[] seed) {
    return BytesUtils.clone(seed);
  }
}
