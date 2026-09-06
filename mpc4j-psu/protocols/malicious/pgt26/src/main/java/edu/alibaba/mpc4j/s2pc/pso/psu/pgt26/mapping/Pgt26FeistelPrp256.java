package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * 256-bit 3-round Feistel PRP with AES-128 (reference {@code mapping.rs} {@code FeistelPrp256}).
 */
public final class Pgt26FeistelPrp256 {
  private final Cipher[] roundCiphers = new Cipher[3];
  private final byte[][] roundKeys = new byte[3][16];

  public Pgt26FeistelPrp256(byte[] masterKey16) {
    if (masterKey16.length != 16) {
      throw new IllegalArgumentException("master key must be 16 bytes");
    }
    try {
      Cipher master = Cipher.getInstance("AES/ECB/NoPadding");
      master.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey16, "AES"));
      for (int i = 0; i < 3; i++) {
        byte[] buf = new byte[16];
        buf[0] = (byte) (i + 1);
        roundKeys[i] = master.doFinal(buf);
        roundCiphers[i] = Cipher.getInstance("AES/ECB/NoPadding");
        roundCiphers[i].init(Cipher.ENCRYPT_MODE, new SecretKeySpec(roundKeys[i], "AES"));
      }
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES init failed", e);
    }
  }

  /** Encrypts one 32-byte block in place (Π). */
  public void encryptBlock(byte[] block32) {
    feistel(block32, false);
  }

  /** Decrypts one 32-byte block in place (Π⁻¹). */
  public void decryptBlock(byte[] block32) {
    feistel(block32, true);
  }

  private void feistel(byte[] block32, boolean decrypt) {
    byte[] l = Arrays.copyOfRange(block32, 0, 16);
    byte[] r = Arrays.copyOfRange(block32, 16, 32);
    if (decrypt) {
      for (int round = 2; round >= 0; round--) {
        swapFeistelRound(l, r, round, true);
      }
    } else {
      for (int round = 0; round < 3; round++) {
        swapFeistelRound(l, r, round, false);
      }
    }
    System.arraycopy(l, 0, block32, 0, 16);
    System.arraycopy(r, 0, block32, 16, 16);
  }

  private void swapFeistelRound(byte[] l, byte[] r, int round, boolean decrypt) {
    byte[] f = new byte[16];
    byte[] xor = new byte[16];
    byte[] rk = roundKeys[round];
    if (decrypt) {
      for (int i = 0; i < 16; i++) {
        xor[i] = (byte) (l[i] ^ rk[i]);
      }
    } else {
      for (int i = 0; i < 16; i++) {
        xor[i] = (byte) (r[i] ^ rk[i]);
      }
    }
    try {
      f = roundCiphers[round].doFinal(xor);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
    if (decrypt) {
      for (int i = 0; i < 16; i++) {
        byte tmp = r[i];
        r[i] = l[i];
        l[i] = (byte) (tmp ^ f[i]);
      }
    } else {
      for (int i = 0; i < 16; i++) {
        byte tmp = (byte) (l[i] ^ f[i]);
        l[i] = r[i];
        r[i] = tmp;
      }
    }
  }

  public static byte[] deriveProtocolKey(byte[] seed) {
    return BytesUtils.clone(Arrays.copyOf(seed, 16));
  }
}
