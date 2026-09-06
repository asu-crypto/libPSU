package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26TestHooks;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided.Pgt26_1mPsuConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PGT26-1M correctness and malicious-sender tests.
 * <p>
 * One-sided PGT26 is <strong>internal experimental code</strong> and is not exposed through the
 * public {@link PsuFactory} / {@link edu.alibaba.libpsu.factory.ProtocolRegistry} under
 * {@code EUROCRYPT:PuGaoTri26} (that enum value maps to two-sided PGT26-2M only). These tests remain
 * ignored until a dedicated one-sided factory API and no-hook correctness suite exist.
 * </p>
 */
@Ignore("PGT26-1M is internal/experimental and not on the public PSU factory surface; EUROCRYPT:PuGaoTri26 is two-sided only.")
public class Pgt26_1mPsuTest extends AbstractTwoPartyMemoryRpcPto {
  private static final Logger LOGGER = LoggerFactory.getLogger(Pgt26_1mPsuTest.class);
  private static final int SIZE_2P5 = 1 << 5;
  private static final int SIZE_2P20 = 1 << 20;
  private static final int ELEMENT_LEN = Pgt26Constants.ITEM_BYTE_LENGTH;

  public Pgt26_1mPsuTest() {
    super("EUROCRYPT_PuGaoTri26");
  }

  @After
  public void tearDownHooks() {
    Pgt26TestHooks.reset();
  }

  @Test
  public void testPGT26_1M_proofRoundtrip() {
    byte[] item = new byte[Pgt26Constants.ITEM_BYTE_LENGTH];
    item[0] = 42;
    byte[] tag = new byte[32];
    byte[] hx = edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26CurveOps.hashToCurve(item);
    byte[] k = edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26CurveOps.randomScalar(SECURE_RANDOM);
    byte[] y = edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26CurveOps.scalarMul(hx, k);
    byte[] proof = edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26DdhKnowledgeProof.prove(
        item, hx, y, k, SECURE_RANDOM, tag
    );
    Assert.assertTrue(
        edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26DdhKnowledgeProof.verify(item, hx, y, proof, tag)
    );
  }

  @Test
  public void testPGT26_1M_2p5_emptyIntersection() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P5, SIZE_2P5, 0);
  }

  @Test
  public void testPGT26_1M_2p5_partialIntersection() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P5, SIZE_2P5, SIZE_2P5 / 2);
  }

  @Test
  public void testPGT26_1M_2p5_fullIntersection() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P5, SIZE_2P5, SIZE_2P5);
  }

  @Test
  public void testPGT26_1M_2p5_maliciousInvalidProofNoAbort() throws InterruptedException, MpcAbortException {
    Pgt26TestHooks.mode = Pgt26TestHooks.Mode.INVALID_PROOF;
    PsuClientOutput out = runOnce(SIZE_2P5, SIZE_2P5, 0);
    Assert.assertEquals(0, out.getPsiCa());
    Assert.assertEquals(SIZE_2P5, out.getUnion().size());
  }

  @Test
  public void testPGT26_1M_2p5_maliciousWrongBindingNoAbort() throws InterruptedException, MpcAbortException {
    Pgt26TestHooks.mode = Pgt26TestHooks.Mode.WRONG_ITEM_BINDING;
    PsuClientOutput out = runOnce(SIZE_2P5, SIZE_2P5, 0);
    Assert.assertEquals(0, out.getPsiCa());
    Assert.assertTrue(
        "malicious OT payload must not expand union beyond |V|+|W|",
        out.getUnion().size() <= SIZE_2P5 * 2
    );
    Assert.assertTrue(out.getUnion().size() >= SIZE_2P5);
  }

  @Test
  public void testPGT26_1M_2p20_honest() throws InterruptedException, MpcAbortException {
    runHonest(SIZE_2P20, SIZE_2P20, 0);
  }

  private void runHonest(int serverSize, int clientSize, int intersectionSize)
      throws InterruptedException, MpcAbortException {
    PsuClientOutput out = runOnce(serverSize, clientSize, intersectionSize);
    ArrayList<Set<ByteBuffer>> sets = generateSets(serverSize, clientSize, intersectionSize);
    Set<ByteBuffer> expectUnion = new HashSet<>(sets.get(0));
    expectUnion.addAll(sets.get(1));
    Assert.assertEquals("psi_ca", intersectionSize, out.getPsiCa());
    assertUnionEqual(expectUnion, out.getUnion());
  }

  private static void assertUnionEqual(Set<ByteBuffer> expected, Set<ByteBuffer> actual) {
    Assert.assertEquals(expected.size(), actual.size());
    for (ByteBuffer e : expected) {
      Assert.assertTrue("missing element", unionContains(actual, e));
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

  private PsuClientOutput runOnce(int serverSize, int clientSize, int intersectionSize)
      throws InterruptedException, MpcAbortException {
    Pgt26_1mPsuConfig config = new Pgt26_1mPsuConfig.Builder().build();
    PsuServer server = PsuFactory.createServer(firstRpc, secondRpc.ownParty(), config);
    PsuClient client = PsuFactory.createClient(secondRpc, firstRpc.ownParty(), config);
    int taskId = Math.abs(SECURE_RANDOM.nextInt());
    server.setTaskId(taskId);
    client.setTaskId(taskId);

    ArrayList<Set<ByteBuffer>> sets = generateSets(serverSize, clientSize, intersectionSize);
    PsuServerThread st = new PsuServerThread(server, sets.get(0), clientSize, ELEMENT_LEN);
    PsuClientThread ct = new PsuClientThread(client, sets.get(1), serverSize, ELEMENT_LEN);
    st.start();
    ct.start();
    st.join();
    ct.join();
    st.rethrowIfFailed();
    ct.rethrowIfFailed();
    server.destroy();
    client.destroy();
    return ct.getClientOutput();
  }

  private static ArrayList<Set<ByteBuffer>> generateSets(int serverSize, int clientSize, int intersectionSize) {
    if (intersectionSize == 0) {
      ArrayList<Set<ByteBuffer>> out = new ArrayList<>(2);
      out.add(serverOnlySet(serverSize, 0));
      out.add(clientOnlySet(clientSize, 0));
      return out;
    }
    ArrayList<Set<ByteBuffer>> out = new ArrayList<>(2);
    Set<ByteBuffer> server = new HashSet<>(serverSize);
    Set<ByteBuffer> client = new HashSet<>(clientSize);
    for (int i = 0; i < intersectionSize; i++) {
      ByteBuffer b = ByteBuffer.allocate(ELEMENT_LEN);
      b.putInt(ELEMENT_LEN - Integer.BYTES, i);
      b.rewind();
      byte[] bytes = new byte[ELEMENT_LEN];
      b.get(bytes);
      ByteBuffer shared = ByteBuffer.wrap(bytes);
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

  private static Set<ByteBuffer> serverOnlySet(int serverSize, int intersectionSize) {
    Set<ByteBuffer> server = new HashSet<>();
    for (int i = intersectionSize; i < serverSize; i++) {
      ByteBuffer b = ByteBuffer.allocate(ELEMENT_LEN);
      b.putInt(ELEMENT_LEN - Integer.BYTES, 0x10000 + i);
      byte[] bytes = new byte[ELEMENT_LEN];
      b.rewind();
      b.get(bytes);
      server.add(ByteBuffer.wrap(bytes));
    }
    return server;
  }

  private static ByteBuffer firstServerElement() {
    return elementWithTailInt(0x10000);
  }

  private static ByteBuffer elementWithTailInt(int tail) {
    ByteBuffer b = ByteBuffer.allocate(ELEMENT_LEN);
    b.putInt(ELEMENT_LEN - Integer.BYTES, tail);
    byte[] bytes = new byte[ELEMENT_LEN];
    b.rewind();
    b.get(bytes);
    return ByteBuffer.wrap(bytes);
  }

  private static Set<ByteBuffer> clientOnlySet(int clientSize, int intersectionSize) {
    Set<ByteBuffer> client = new HashSet<>();
    for (int i = intersectionSize; i < clientSize; i++) {
      client.add(elementWithTailInt(0x20000 + i));
    }
    return client;
  }
}
