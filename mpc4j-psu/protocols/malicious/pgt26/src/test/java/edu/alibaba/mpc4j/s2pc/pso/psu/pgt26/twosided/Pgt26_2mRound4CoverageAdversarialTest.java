package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuPtoDesc.PtoStep;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RPC-level Figure 6 coverage adversarial test: empty Round-4 indices must abort
 * at the coverage check even if an RDDH proof blob is preserved.
 */
public class Pgt26_2mRound4CoverageAdversarialTest extends AbstractTwoPartyMemoryRpcPto {
  private static final int ELEMENT_LEN = Pgt26Constants.ITEM_BYTE_LENGTH;
  private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

  public Pgt26_2mRound4CoverageAdversarialTest() {
    super("PGT26_2M_COV_ADV");
  }

  @Test
  public void emptyRound4UnblindAbortsAtCoverage() throws Exception {
    Set<ByteBuffer> serverSet = new HashSet<>();
    Set<ByteBuffer> clientSet = new HashSet<>();
    serverSet.add(elem(1, 0));
    serverSet.add(elem(1, 1));
    clientSet.add(elem(2, 0));
    clientSet.add(elem(2, 1));

    EmptyRound4Rpc adversarialServerRpc = new EmptyRound4Rpc(firstRpc);
    Pgt26_2mPsuConfig config = new Pgt26_2mPsuConfig.Builder().build();
    PsuTwoSidedServer server = new Pgt26_2mPsuServer(adversarialServerRpc, secondRpc.ownParty(), config);
    PsuTwoSidedClient client = new Pgt26_2mPsuClient(secondRpc, firstRpc.ownParty(), config);
    int taskId = Math.abs(SECURE_RANDOM.nextInt());
    server.setTaskId(taskId);
    client.setTaskId(taskId);

    AtomicReference<Throwable> serverFail = new AtomicReference<>();
    AtomicReference<Throwable> clientFail = new AtomicReference<>();
    Thread st = new Thread(() -> {
      try {
        server.init(serverSet.size(), clientSet.size());
        server.getRpc().synchronize();
        server.psu(serverSet, clientSet.size(), ELEMENT_LEN);
      } catch (Throwable t) {
        serverFail.set(t);
      }
    });
    Thread ct = new Thread(() -> {
      try {
        client.init(clientSet.size(), serverSet.size());
        client.getRpc().synchronize();
        client.psu(clientSet, serverSet.size(), ELEMENT_LEN);
      } catch (Throwable t) {
        clientFail.set(t);
      }
    });
    st.start();
    ct.start();
    try {
      TwoPartyTestJoin.joinFailFast(
          st, serverFail::get, server::destroy,
          ct, clientFail::get, client::destroy,
          TIMEOUT_MS,
          "PGT26-2M Round4 coverage adversarial"
      );
      Assert.fail("expected coverage abort");
    } catch (AssertionError expected) {
      Throwable clientErr = clientFail.get();
      if (clientErr == null && expected.getCause() != null) {
        clientErr = expected.getCause();
      }
      Assert.assertNotNull("client should abort", clientErr);
      Assert.assertTrue(
          clientErr instanceof MpcAbortException || clientErr.getCause() instanceof MpcAbortException
      );
      String msg = messageOf(clientErr).toLowerCase();
      Assert.assertTrue(
          "diagnostic should identify coverage/indices, was: " + msg,
          msg.contains("coverage") || msg.contains("index") || msg.contains("round-4")
      );
    }
  }

  private static String messageOf(Throwable t) {
    StringBuilder sb = new StringBuilder();
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (c.getMessage() != null) {
        sb.append(c.getMessage()).append(' ');
      }
    }
    return sb.toString();
  }

  private static ByteBuffer elem(int domain, int index) {
    byte[] b = new byte[ELEMENT_LEN];
    b[0] = (byte) domain;
    b[ELEMENT_LEN - 1] = (byte) index;
    return ByteBuffer.wrap(b);
  }

  /**
   * On outbound {@link PtoStep#ROUND4_UNBLIND}, keep the proof blob, drop unblinded
   * points, and replace indices with an empty packed list.
   */
  private static final class EmptyRound4Rpc implements Rpc {
    private final Rpc delegate;
    private final int round4Step = PtoStep.ROUND4_UNBLIND.ordinal();
    private final int ptoId = Pgt26_2mPsuPtoDesc.getInstance().getPtoId();

    EmptyRound4Rpc(Rpc delegate) {
      this.delegate = delegate;
    }

    @Override
    public void send(DataPacket dataPacket) {
      DataPacketHeader h = dataPacket.getHeader();
      if (h.getPtoId() == ptoId && h.getStepId() == round4Step) {
        List<byte[]> payload = dataPacket.getPayload();
        List<byte[]> mutated = new ArrayList<>(2);
        mutated.add(payload.get(0)); // preserve proof
        try {
          mutated.add(Pgt26_2mWire.packIndices(new int[0]));
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
        delegate.send(DataPacket.fromByteArrayList(h, mutated));
        return;
      }
      delegate.send(dataPacket);
    }

    @Override public Party ownParty() { return delegate.ownParty(); }
    @Override public java.util.Set<Party> getPartySet() { return delegate.getPartySet(); }
    @Override public Party getParty(int partyId) { return delegate.getParty(partyId); }
    @Override public void connect() { delegate.connect(); }
    @Override public DataPacket receive(DataPacketHeader header) { return delegate.receive(header); }
    @Override public DataPacket receiveAny(int ptoId) { return delegate.receiveAny(ptoId); }
    @Override public long getPayloadByteLength() { return delegate.getPayloadByteLength(); }
    @Override public long getSendByteLength() { return delegate.getSendByteLength(); }
    @Override public long getSendDataPacketNum() { return delegate.getSendDataPacketNum(); }
    @Override public void synchronize() { delegate.synchronize(); }
    @Override public void reset() { delegate.reset(); }
    @Override public void disconnect() { delegate.disconnect(); }
  }
}
