package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.Hn12PsiConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * HN12_PSI_2p5 correctness tests.
 */
public class Hn12Psi2p5Test extends AbstractTwoPartyMemoryRpcPto {
  public Hn12Psi2p5Test() {
    super("JOC_HazNis12");
  }

  @Test
  public void testHN12_PSI_2p5_partialIntersection() throws InterruptedException {
    runCase(bytes(2L, 4L, 6L, 8L, 10L), bytes(1L, 4L, 7L, 8L, 9L), intersection(4L, 8L));
  }

  @Test
  public void testHN12_PSI_2p5_emptyIntersection() throws InterruptedException {
    runCase(bytes(2L, 4L, 6L, 8L, 10L), bytes(1L, 3L, 5L, 7L, 9L), intersection());
  }

  @Test
  public void testHN12_PSI_2p5_fullOverlap() throws InterruptedException {
    runCase(bytes(1L, 2L, 3L, 4L, 5L), bytes(1L, 2L, 3L, 4L, 5L), intersection(1L, 2L, 3L, 4L, 5L));
  }

  private void runCase(Set<ByteBuffer> clientSet, Set<ByteBuffer> serverSet, Set<ByteBuffer> expect)
      throws InterruptedException {
    Hn12PsiConfig config = new Hn12PsiConfig.Builder()
        .setGroupBitLength(256)
        .setUseIdealPrfForTesting(true)
        .build();
    PsiServer server = PsiFactory.createServer(firstRpc, secondRpc.ownParty(), config);
    PsiClient client = PsiFactory.createClient(secondRpc, firstRpc.ownParty(), config);
    int taskId = Math.abs(SECURE_RANDOM.nextInt());
    server.setTaskId(taskId);
    client.setTaskId(taskId);
    PsiServerThread st = new PsiServerThread(server, serverSet, clientSet.size(), Long.BYTES);
    PsiClientThread ct = new PsiClientThread(client, clientSet, serverSet.size(), Long.BYTES);
    st.start();
    ct.start();
    st.join();
    ct.join();
    Set<ByteBuffer> actual = ct.getClientOutput().getIntersection();
    Assert.assertEquals(expect, actual);
    Set<ByteBuffer> golden = new HashSet<>(clientSet);
    golden.retainAll(serverSet);
    Assert.assertEquals(golden, actual);
  }

  private static Set<ByteBuffer> bytes(long... values) {
    Set<ByteBuffer> set = new HashSet<>();
    for (long v : values) {
      set.add(ByteBuffer.wrap(longBytes(v)));
    }
    return set;
  }

  private static Set<ByteBuffer> intersection(long... values) {
    return bytes(values);
  }

  private static byte[] longBytes(long v) {
    byte[] raw = new byte[Long.BYTES];
    for (int i = Long.BYTES - 1; i >= 0; i--) {
      raw[i] = (byte) (v & 0xFF);
      v >>>= 8;
    }
    return raw;
  }
}
