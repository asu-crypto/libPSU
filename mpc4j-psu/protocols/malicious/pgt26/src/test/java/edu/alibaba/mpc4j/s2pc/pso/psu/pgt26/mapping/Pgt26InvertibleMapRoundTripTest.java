package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping;

import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import org.bouncycastle.util.encoders.Hex;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ProtocolTag;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuPtoDesc;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

/** Round-trip H/H⁻¹ on fair-benchmark element layout without test hooks. */
public class Pgt26InvertibleMapRoundTripTest {
  @Test
  public void fairBench2p5ElementsRoundTripWithoutHooks() {
    int n = 32;
    ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(n, n, Pgt26Constants.ITEM_BYTE_LENGTH);
    byte[] key = Pgt26ProtocolTag.feistelKey(Pgt26_2mPsuPtoDesc.getInstance(), n, n);
    Pgt26FeistelPrp256 prp = new Pgt26FeistelPrp256(key);
    for (Set<ByteBuffer> set : sets) {
      for (ByteBuffer bb : set) {
        byte[] item = bb.array();
        byte[] point = Pgt26InvertibleMap.hashToPoint(item, prp);
        Optional<byte[]> recovered = Pgt26InvertibleMap.recoverFromPoint(point, prp);
        Assert.assertTrue("decode failed for fair-bench item " + Hex.toHexString(item), recovered.isPresent());
        Assert.assertArrayEquals(item, recovered.get());
      }
    }
  }
}
