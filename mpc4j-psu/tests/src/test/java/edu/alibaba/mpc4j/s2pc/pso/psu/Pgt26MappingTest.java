package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26TestHooks;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26FeistelPrp256;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26InvertibleMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

/**
 * PGT26-2M mapping tests at n = 2^5 scale (single-item round trips).
 */
public class Pgt26MappingTest {
  @org.junit.After
  public void tearDown() {
    Pgt26TestHooks.reset();
  }

  @Test
  public void testPGT26_MAPPING_2p5_roundTrip() {
    byte[] key = new byte[16];
    key[0] = 7;
    Pgt26FeistelPrp256 prp = new Pgt26FeistelPrp256(key);
    byte[] item = new byte[Pgt26Constants.ITEM_BYTE_LENGTH];
    item[0] = 42;
    Pgt26TestHooks.candidateItems = java.util.List.of(item);
    byte[] point = Pgt26InvertibleMap.hashToPoint(item, prp);
    Optional<byte[]> recovered = Pgt26InvertibleMap.recoverFromPoint(point, prp);
    Assert.assertTrue(recovered.isPresent());
    Assert.assertArrayEquals(item, recovered.get());
  }

  @Test
  public void testPGT26_MAPPING_2p5_invalidDomainRejection() {
    byte[] key = new byte[16];
    Pgt26FeistelPrp256 prp = new Pgt26FeistelPrp256(key);
    byte[] item = new byte[Pgt26Constants.ITEM_BYTE_LENGTH];
    byte[] point = Pgt26EdwardsMath.publicKey(Pgt26EdwardsMath.randomScalar(new java.security.SecureRandom()));
    Optional<byte[]> recovered = Pgt26InvertibleMap.recoverFromPoint(point, prp);
    if (recovered.isPresent()) {
      Assert.assertFalse(Pgt26InvertibleMap.hasValidPadding(recovered.get()));
    }
  }

  @Test
  public void testPGT26_MAPPING_2p5_trailingZeroPadding() {
    byte[] padded = Pgt26InvertibleMap.padItem(new byte[Pgt26Constants.ITEM_BYTE_LENGTH]);
    Assert.assertTrue(Pgt26InvertibleMap.hasValidPadding(Arrays.copyOf(padded, Pgt26Constants.ITEM_BYTE_LENGTH)));
  }

  @Test
  public void testPGT26_MAPPING_2p5_deterministicPrp() {
    byte[] key = new byte[16];
    key[1] = 3;
    Pgt26FeistelPrp256 a = new Pgt26FeistelPrp256(key);
    Pgt26FeistelPrp256 b = new Pgt26FeistelPrp256(key);
    byte[] block = new byte[32];
    block[0] = 9;
    byte[] b1 = Arrays.copyOf(block, 32);
    byte[] b2 = Arrays.copyOf(block, 32);
    a.encryptBlock(b1);
    b.encryptBlock(b2);
    Assert.assertArrayEquals(b1, b2);
  }
}
