package edu.alibaba.mpc4j.s2pc.ba12;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Plaintext;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * BA12 set-operation correctness at {@code 2^5}.
 */
public class Ba12SetOps2p5Test extends AbstractTwoPartyMemoryRpcPto {
  private static final int M = 1 << 5;
  private static final int ELL = 16;

  public Ba12SetOps2p5Test() {
    super("BA12_SET_OPS_2P5");
  }

  @Test
  public void testUnionDisjoint() throws Exception {
    long[] a = seq(1, M / 2);
    long[] b = seq(M / 2 + 1, M);
    runBinary(Ba12Operation.BA12_UNION, a, b, Ba12Plaintext.union(a, b));
  }

  @Test
  public void testIntersectionPartial() throws Exception {
    long[] a = seq(1, M);
    long[] b = seq(M / 2, M + M / 2 - 1);
    runBinary(Ba12Operation.BA12_INTERSECTION, a, b, Ba12Plaintext.intersection(a, b));
  }

  @Test
  public void testDifference() throws Exception {
    long[] a = seq(1, M / 2);
    long[] b = seq(M / 4, M / 2 + M / 4);
    runBinary(Ba12Operation.BA12_DIFFERENCE, a, b, Ba12Plaintext.difference(a, b));
  }

  @Test
  public void testSymmetricDifference() throws Exception {
    long[] a = seq(1, M / 2);
    long[] b = seq(M / 4, M / 2 + M / 4 - 1);
    runBinary(Ba12Operation.BA12_SYMMETRIC_DIFFERENCE, a, b, Ba12Plaintext.symmetricDifference(a, b));
  }

  @Test
  public void testElementReduction() throws Exception {
    long[] a = withDup(seq(1, M / 2));
    runUnary(Ba12Operation.BA12_ELEMENT_REDUCTION, a, Ba12Plaintext.elementReduction(a));
  }

  private void runBinary(Ba12Operation op, long[] a, long[] b, long[] expect) throws Exception {
    Ba12Config config = new Ba12Config.Builder().setEll(ELL).setSilent(false).build();
    int tid = Math.abs(SECURE_RANDOM.nextInt());
    ServerThread st = new ServerThread(config, tid, op, a, b);
    ClientThread ct = new ClientThread(config, tid, op, a, b);
    st.start();
    ct.start();
    st.join();
    ct.join();
    assertSetEquals(expect, st.getOpened());
    assertSetEquals(expect, ct.getOpened());
  }

  private void runUnary(Ba12Operation op, long[] a, long[] expect) throws Exception {
    Ba12Config config = new Ba12Config.Builder().setEll(ELL).setSilent(false).build();
    int tid = Math.abs(SECURE_RANDOM.nextInt());
    UnaryServerThread st = new UnaryServerThread(config, tid, op, a);
    UnaryClientThread ct = new UnaryClientThread(config, tid, op, a);
    st.start();
    ct.start();
    st.join();
    ct.join();
    assertSetEquals(expect, st.getOpened());
    assertSetEquals(expect, ct.getOpened());
  }

  private static void assertSetEquals(long[] expect, long[] actual) {
    long[] exp = Ba12Plaintext.asSortedNonZero(expect);
    long[] act = Ba12Plaintext.asSortedNonZero(actual);
    Assert.assertEquals(
      "expected=" + Arrays.toString(exp) + " actual=" + Arrays.toString(act),
      exp.length,
      act.length
    );
    Assert.assertArrayEquals(exp, act);
  }

  private static long[] seq(int from, int toInclusive) {
    return java.util.stream.IntStream.rangeClosed(from, toInclusive).asLongStream().toArray();
  }

  private static long[] withDup(long[] base) {
    long[] out = Arrays.copyOf(base, base.length * 2);
    System.arraycopy(base, 0, out, 0, base.length);
    System.arraycopy(base, 0, out, base.length, base.length);
    return out;
  }

  private class ServerThread extends Thread {
    private final Ba12Config config;
    private final int tid;
    private final Ba12Operation op;
    private final long[] a;
    private final long[] b;
    private long[] opened;

    ServerThread(Ba12Config config, int tid, Ba12Operation op, long[] a, long[] b) {
      this.config = config;
      this.tid = tid;
      this.op = op;
      this.a = a;
      this.b = b;
    }

    long[] getOpened() {
      return opened;
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(firstRpc, secondRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] aIn = party.inputOwn(a);
        Ba12Share[] bIn = party.inputOther(b.length);
        Ba12Share[] out = party.runBinary(op, aIn, bIn);
        opened = party.reveal(out);
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private class ClientThread extends Thread {
    private final Ba12Config config;
    private final int tid;
    private final Ba12Operation op;
    private final long[] a;
    private final long[] b;
    private long[] opened;

    ClientThread(Ba12Config config, int tid, Ba12Operation op, long[] a, long[] b) {
      this.config = config;
      this.tid = tid;
      this.op = op;
      this.a = a;
      this.b = b;
    }

    long[] getOpened() {
      return opened;
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(secondRpc, firstRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] bIn = party.inputOwn(b);
        Ba12Share[] aIn = party.inputOther(a.length);
        Ba12Share[] out = party.runBinary(op, aIn, bIn);
        opened = party.reveal(out);
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private class UnaryServerThread extends Thread {
    private final Ba12Config config;
    private final int tid;
    private final Ba12Operation op;
    private final long[] a;
    private long[] opened;

    UnaryServerThread(Ba12Config config, int tid, Ba12Operation op, long[] a) {
      this.config = config;
      this.tid = tid;
      this.op = op;
      this.a = a;
    }

    long[] getOpened() {
      return opened;
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(firstRpc, secondRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] aIn = party.inputOwn(a);
        Ba12Share[] out = party.runUnary(op, aIn);
        opened = party.reveal(out);
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private class UnaryClientThread extends Thread {
    private final Ba12Config config;
    private final int tid;
    private final Ba12Operation op;
    private final long[] a;
    private long[] opened;

    UnaryClientThread(Ba12Config config, int tid, Ba12Operation op, long[] a) {
      this.config = config;
      this.tid = tid;
      this.op = op;
      this.a = a;
    }

    long[] getOpened() {
      return opened;
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(secondRpc, firstRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] aIn = party.inputOther(a.length);
        Ba12Share[] out = party.runUnary(op, aIn);
        opened = party.reveal(out);
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
