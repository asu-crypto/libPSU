package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26TestHooks;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26FeistelPrp256;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26InvertibleMap;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ProtocolTag;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuConfig;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuPtoDesc;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PGT26-2M tests (not EUROCRYPT_PisTri26).
 */
public class Pgt26_2mPsuTest extends AbstractTwoPartyMemoryRpcPto {
  private static final int SIZE_2P5 = 1 << 5;
  private static final int SIZE_2P20 = 1 << 20;
  private static final int ELEMENT_LEN = Pgt26Constants.ITEM_BYTE_LENGTH;

  public Pgt26_2mPsuTest() {
    super("EUROCRYPT_PuGaoTri26");
  }

  @org.junit.After
  public void tearDownHooks() {
    Pgt26TestHooks.reset();
  }

  @Test
  public void testPGT26_2M_2p5_emptyIntersection() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P5, SIZE_2P5, 0);
  }

  @Test
  public void testPGT26_2M_2p5_partialIntersection() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P5, SIZE_2P5, SIZE_2P5 / 2);
  }

  @Test
  public void testPGT26_2M_unequal_2p3x2p5() throws InterruptedException, MpcAbortException {
    runHonest(1 << 3, 1 << 5, 4);
  }

  @Test
  public void testPGT26_2M_2p5_fullIntersection() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P5, SIZE_2P5, SIZE_2P5);
  }

  @Test
  public void testPGT26_2M_2p20_honest() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P20, SIZE_2P20, 0);
  }

  /** Fair-bench 2^5 layout with full shuffle + RDDH proofs and no {@link Pgt26TestHooks}. */
  @Test
  public void testPGT26_2M_2p5_fairBenchElements_noHooks_fullProofs()
      throws InterruptedException, MpcAbortException {
    ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(SIZE_2P5, SIZE_2P5, ELEMENT_LEN);
    Set<ByteBuffer> expect = new HashSet<>(sets.get(0));
    expect.addAll(sets.get(1));
    ParallelOut out = runParallel(sets.get(0), SIZE_2P5, sets.get(1), SIZE_2P5);
    assertUnionEqual(expect, out.clientOut.getUnion());
    assertUnionEqual(expect, out.serverOut.getUnion());
  }

  /** Same element layout as {@link PsuMain} warmup (1024×1024, 16-byte), without {@link Pgt26TestHooks} point cache. */
  @Test
  public void testPGT26_2M_warmup_benchmarkElements_noPointCache() throws InterruptedException, MpcAbortException {
    int warmup = 1 << 10;
    ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(warmup, warmup, ELEMENT_LEN);
    Set<ByteBuffer> expect = new HashSet<>(sets.get(0));
    expect.addAll(sets.get(1));
    ParallelOut out = runParallel(sets.get(0), warmup, sets.get(1), warmup);
    assertUnionEqual(expect, out.clientOut.getUnion());
    assertUnionEqual(expect, out.serverOut.getUnion());
  }

  private void runHonest(int serverSize, int clientSize, int intersectionSize)
      throws InterruptedException, MpcAbortException {
    ArrayList<Set<ByteBuffer>> sets = generateSets(serverSize, clientSize, intersectionSize);
    Set<ByteBuffer> expect = new HashSet<>(sets.get(0));
    expect.addAll(sets.get(1));
    java.util.List<byte[]> items = toItemBytes(expect);
    Pgt26TestHooks.candidateItems = items;
    Pgt26TestHooks.candidatePointCache = buildPointCache(items, clientSize, serverSize);
    ParallelOut out = runParallel(sets.get(0), clientSize, sets.get(1), serverSize);
    assertUnionEqual(expect, out.clientOut.getUnion());
    assertUnionEqual(expect, out.serverOut.getUnion());
  }

  private ParallelOut runParallel(
      Set<ByteBuffer> serverSet, int clientSize, Set<ByteBuffer> clientSet, int serverSize
  ) throws InterruptedException, MpcAbortException {
    Pgt26_2mPsuConfig config = new Pgt26_2mPsuConfig.Builder().build();
    PsuTwoSidedServer server = PsuFactory.createTwoSidedServer(firstRpc, secondRpc.ownParty(), config);
    PsuTwoSidedClient client = PsuFactory.createTwoSidedClient(secondRpc, firstRpc.ownParty(), config);
    int taskId = Math.abs(SECURE_RANDOM.nextInt());
    server.setTaskId(taskId);
    client.setTaskId(taskId);
    PsuTwoSidedServerThread st = new PsuTwoSidedServerThread(server, serverSet, clientSize, ELEMENT_LEN);
    PsuTwoSidedClientThread ct = new PsuTwoSidedClientThread(client, clientSet, serverSize, ELEMENT_LEN);
    st.start();
    ct.start();
    st.join();
    ct.join();
    server.destroy();
    client.destroy();
    return new ParallelOut(ct.getOutput(), st.getOutput());
  }

  private static final class ParallelOut {
    final PsuTwoSidedOutput clientOut;
    final PsuTwoSidedOutput serverOut;

    ParallelOut(PsuTwoSidedOutput clientOut, PsuTwoSidedOutput serverOut) {
      this.clientOut = clientOut;
      this.serverOut = serverOut;
    }
  }

  private static void assertUnionEqual(Set<ByteBuffer> expected, Set<ByteBuffer> actual) {
    Assert.assertEquals(expected.size(), actual.size());
    for (ByteBuffer e : expected) {
      Assert.assertTrue(unionContains(actual, e));
    }
  }

  private static boolean unionContains(Set<ByteBuffer> union, ByteBuffer needle) {
    byte[] target = byteArray(needle);
    for (ByteBuffer b : union) {
      if (Arrays.equals(target, byteArray(b))) {
        return true;
      }
    }
    return false;
  }

  private static byte[] byteArray(ByteBuffer buffer) {
    ByteBuffer dup = buffer.duplicate();
    byte[] item = new byte[dup.remaining()];
    dup.get(item);
    return item;
  }

  private static ArrayList<Set<ByteBuffer>> generateSets(int serverSize, int clientSize, int intersectionSize) {
    ArrayList<Set<ByteBuffer>> out = new ArrayList<>(2);
    Set<ByteBuffer> server = new HashSet<>();
    Set<ByteBuffer> client = new HashSet<>();
    for (int i = 0; i < intersectionSize; i++) {
      ByteBuffer shared = elementWithTailInt(i);
      server.add(shared.duplicate());
      client.add(shared.duplicate());
    }
    for (int i = intersectionSize; i < serverSize; i++) {
      server.add(elementWithTailInt(0x10000 + i));
    }
    for (int i = intersectionSize; i < clientSize; i++) {
      client.add(elementWithTailInt(0x20000 + i));
    }
    out.add(server);
    out.add(client);
    return out;
  }

  private static java.util.Map<ByteBuffer, byte[]> buildPointCache(
      java.util.List<byte[]> items, int clientSize, int serverSize
  ) {
    byte[] key = Pgt26ProtocolTag.feistelKey(Pgt26_2mPsuPtoDesc.getInstance(), clientSize, serverSize);
    Pgt26FeistelPrp256 prp = new Pgt26FeistelPrp256(key);
    java.util.HashMap<ByteBuffer, byte[]> map = new java.util.HashMap<>();
    for (byte[] item : items) {
      byte[] point = Pgt26InvertibleMap.hashToPoint(item, prp);
      map.put(ByteBuffer.wrap(Arrays.copyOf(point, point.length)), item);
      for (byte[] torsion : Pgt26EdwardsMath.EIGHT_TORSION) {
        byte[] shifted = Pgt26EdwardsMath.pointAdd(point, torsion);
        map.put(ByteBuffer.wrap(Arrays.copyOf(shifted, shifted.length)), item);
      }
    }
    return map;
  }

  private static java.util.List<byte[]> toItemBytes(Set<ByteBuffer> buffers) {
    java.util.ArrayList<byte[]> out = new java.util.ArrayList<>(buffers.size());
    for (ByteBuffer b : buffers) {
      out.add(byteArray(b));
    }
    return out;
  }

  private static ByteBuffer elementWithTailInt(int tail) {
    ByteBuffer b = ByteBuffer.allocate(ELEMENT_LEN);
    b.putInt(ELEMENT_LEN - Integer.BYTES, tail);
    byte[] bytes = new byte[ELEMENT_LEN];
    b.rewind();
    b.get(bytes);
    return ByteBuffer.wrap(bytes);
  }

  private static class PsuTwoSidedClientThread extends Thread {
    private final PsuTwoSidedClient client;
    private final Set<ByteBuffer> set;
    private final int otherSize;
    private final int elementLen;
    private PsuTwoSidedOutput output;

    PsuTwoSidedClientThread(PsuTwoSidedClient client, Set<ByteBuffer> set, int otherSize, int elementLen) {
      this.client = client;
      this.set = set;
      this.otherSize = otherSize;
      this.elementLen = elementLen;
    }

    @Override
    public void run() {
      try {
        client.init(set.size(), otherSize);
        client.getRpc().synchronize();
        output = client.psu(set, otherSize, elementLen);
      } catch (MpcAbortException e) {
        throw new IllegalStateException(e);
      }
    }

    PsuTwoSidedOutput getOutput() {
      return output;
    }
  }

  private static class PsuTwoSidedServerThread extends Thread {
    private final PsuTwoSidedServer server;
    private final Set<ByteBuffer> set;
    private final int otherSize;
    private final int elementLen;
    private PsuTwoSidedOutput output;

    PsuTwoSidedServerThread(PsuTwoSidedServer server, Set<ByteBuffer> set, int otherSize, int elementLen) {
      this.server = server;
      this.set = set;
      this.otherSize = otherSize;
      this.elementLen = elementLen;
    }

    @Override
    public void run() {
      try {
        server.init(otherSize, set.size());
        server.getRpc().synchronize();
        output = server.psu(set, otherSize, elementLen);
      } catch (MpcAbortException e) {
        throw new IllegalStateException(e);
      }
    }

    PsuTwoSidedOutput getOutput() {
      return output;
    }
  }
}
