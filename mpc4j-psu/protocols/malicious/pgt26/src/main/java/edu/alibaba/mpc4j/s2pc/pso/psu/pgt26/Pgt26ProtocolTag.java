package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

import java.nio.charset.StandardCharsets;

/**
 * Fiat–Shamir domain separator for PGT26 proofs (binds protocol id, role, round, sizes).
 */
public final class Pgt26ProtocolTag {
  private Pgt26ProtocolTag() {
    // empty
  }

  public static byte[] twoSided2m(PtoDesc desc, int round, int clientSize, int serverSize) {
    Blake2s256 h = new Blake2s256();
    h.update(desc.getPtoName().getBytes(StandardCharsets.UTF_8));
    h.update(new byte[]{(byte) round});
    h.update(intLe(clientSize));
    h.update(intLe(serverSize));
    h.update(new byte[]{(byte) Pgt26Constants.ITEM_BYTE_LENGTH});
    h.update("EUROCRYPT_PuGaoTri26".getBytes(StandardCharsets.UTF_8));
    return h.digest();
  }

  /** Deterministic AES key for Π (both parties). */
  public static byte[] feistelKey(PtoDesc desc, int clientSize, int serverSize) {
    byte[] tag = twoSided2m(desc, 0, clientSize, serverSize);
    return java.util.Arrays.copyOf(tag, 16);
  }

  public static byte[] oneSided1m(PtoDesc desc, int round, int clientSize, int serverSize) {
    Blake2s256 h = new Blake2s256();
    h.update(desc.getPtoName().getBytes(StandardCharsets.UTF_8));
    h.update(new byte[]{(byte) round});
    h.update(intLe(clientSize));
    h.update(intLe(serverSize));
    h.update(new byte[]{(byte) Pgt26Constants.ITEM_BYTE_LENGTH});
    h.update("X25519".getBytes(StandardCharsets.UTF_8));
    return h.digest();
  }

  private static byte[] intLe(int v) {
    return new byte[]{
        (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24),
    };
  }
}
