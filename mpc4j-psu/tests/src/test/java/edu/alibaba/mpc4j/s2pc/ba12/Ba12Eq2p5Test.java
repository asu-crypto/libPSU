package edu.alibaba.mpc4j.s2pc.ba12;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import org.junit.Assert;
import org.junit.Test;

/**
 * Sanity check: {@link edu.alibaba.mpc4j.s2pc.ba12.core.Ba12MpcEngine#eq} on equal shared elements.
 */
public class Ba12Eq2p5Test extends AbstractTwoPartyMemoryRpcPto {
  public Ba12Eq2p5Test() {
    super("BA12_EQ_2P5");
  }

  @Test
  public void testEqAfterSortOnDuplicates() throws Exception {
    Ba12Config config = new Ba12Config.Builder().setEll(16).setSilent(false).build();
    int tid = Math.abs(SECURE_RANDOM.nextInt());
    SortEqServer st = new SortEqServer(config, tid);
    SortEqClient ct = new SortEqClient(config, tid);
    st.start();
    ct.start();
    st.join();
    ct.join();
    Assert.assertEquals(1L, st.getOpenedBit());
    Assert.assertEquals(1L, ct.getOpenedBit());
  }

  @Test
  public void testEqOnEqualShares() throws Exception {
    Ba12Config config = new Ba12Config.Builder().setEll(16).setSilent(false).build();
    int tid = Math.abs(SECURE_RANDOM.nextInt());
    EqServer st = new EqServer(config, tid);
    EqClient ct = new EqClient(config, tid);
    st.start();
    ct.start();
    st.join();
    ct.join();
    Assert.assertEquals(1L, st.getOpenedBit());
    Assert.assertEquals(1L, ct.getOpenedBit());
  }

  private abstract static class EqThread extends Thread {
    final Ba12Config config;
    final int tid;
    long openedBit;

    EqThread(Ba12Config config, int tid) {
      this.config = config;
      this.tid = tid;
    }

    long getOpenedBit() {
      return openedBit;
    }
  }

  private class EqServer extends EqThread {
    EqServer(Ba12Config config, int tid) {
      super(config, tid);
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(firstRpc, secondRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] x = party.inputOwn(new long[] { 42L, 42L });
        MpcZ2Vector eq = party.getEngine().eq(x[0], x[1]);
        openedBit = party.getEngine().reveal(new Ba12Share[] { new Ba12Share(new MpcZ2Vector[] { eq }) })[0];
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private class SortEqServer extends EqThread {
    SortEqServer(Ba12Config config, int tid) {
      super(config, tid);
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(firstRpc, secondRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        long[] a = new long[] { 2L, 1L, 1L, 2L };
        Ba12Share[] x = party.inputOwn(a);
        party.getEngine().sort(x);
        MpcZ2Vector eq = party.getEngine().eq(x[0], x[1]);
        openedBit = party.getEngine().reveal(new Ba12Share[] { new Ba12Share(new MpcZ2Vector[] { eq }) })[0];
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private class SortEqClient extends EqThread {
    SortEqClient(Ba12Config config, int tid) {
      super(config, tid);
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(secondRpc, firstRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] x = party.inputOther(4);
        party.getEngine().sort(x);
        MpcZ2Vector eq = party.getEngine().eq(x[0], x[1]);
        openedBit = party.getEngine().reveal(new Ba12Share[] { new Ba12Share(new MpcZ2Vector[] { eq }) })[0];
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private class EqClient extends EqThread {
    EqClient(Ba12Config config, int tid) {
      super(config, tid);
    }

    @Override
    public void run() {
      try {
        Ba12SetOpsParty party = new Ba12SetOpsParty(secondRpc, firstRpc.ownParty(), config);
        party.setTaskId(tid);
        party.init();
        Ba12Share[] x = party.inputOther(2);
        MpcZ2Vector eq = party.getEngine().eq(x[0], x[1]);
        openedBit = party.getEngine().reveal(new Ba12Share[] { new Ba12Share(new MpcZ2Vector[] { eq }) })[0];
        party.destroy();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
