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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * RPC-level Figure 6 Round-4 coverage adversarial tests.
 * <p>
 * Production order preserved: decode → structural validate → coverage → RDDH.
 * Mutations keep a valid-looking proof blob and only rewrite ROUND4_UNBLIND payloads
 * (no proof bypass hooks). Both server→client and client→server directions are covered.
 * Every case must {@link MpcAbortException} (coverage/malformed) without hanging.
 */
public class Pgt26_2mRound4CoverageAdversarialTest extends AbstractTwoPartyMemoryRpcPto {
  private static final int ELEMENT_LEN = Pgt26Constants.ITEM_BYTE_LENGTH;
  private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

  public Pgt26_2mRound4CoverageAdversarialTest() {
    super("PGT26_2M_COV_ADV");
  }

  @Test
  public void emptyRound4UnblindAbortsAtCoverage_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        Round4Mutations::emptyIndices,
        "empty Round4 server→client"
    );
  }

  @Test
  public void emptyRound4UnblindAbortsAtCoverage_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        Round4Mutations::emptyIndices,
        "empty Round4 client→server"
    );
  }

  @Test
  public void oneRequiredItemDeletedAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        Round4Mutations::deleteOneRequired,
        "delete-one server→client"
    );
  }

  @Test
  public void oneRequiredItemDeletedAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        Round4Mutations::deleteOneRequired,
        "delete-one client→server"
    );
  }

  @Test
  public void sameCardinalityWrongIndexAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        partialIntersectionSets(3, 1),
        p -> Round4Mutations.sameCardinalityWrongIndex(p, 3),
        "wrong-index server→client"
    );
  }

  @Test
  public void sameCardinalityWrongIndexAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        partialIntersectionSets(3, 1),
        p -> Round4Mutations.sameCardinalityWrongIndex(p, 3),
        "wrong-index client→server"
    );
  }

  @Test
  public void duplicateIndicesAbort_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        Round4Mutations::duplicateIndices,
        "duplicate indices server→client"
    );
  }

  @Test
  public void duplicateIndicesAbort_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        Round4Mutations::duplicateIndices,
        "duplicate indices client→server"
    );
  }

  @Test
  public void negativeIndexAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        Round4Mutations::negativeIndex,
        "negative index server→client"
    );
  }

  @Test
  public void negativeIndexAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        Round4Mutations::negativeIndex,
        "negative index client→server"
    );
  }

  @Test
  public void indexEqualsUpperBoundAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        p -> Round4Mutations.indexAtOrAboveUpperBound(p, 2, false),
        "index==upperBound server→client"
    );
  }

  @Test
  public void indexEqualsUpperBoundAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        p -> Round4Mutations.indexAtOrAboveUpperBound(p, 2, false),
        "index==upperBound client→server"
    );
  }

  @Test
  public void indexGreaterThanUpperBoundAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        p -> Round4Mutations.indexAtOrAboveUpperBound(p, 2, true),
        "index>upperBound server→client"
    );
  }

  @Test
  public void indexGreaterThanUpperBoundAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        p -> Round4Mutations.indexAtOrAboveUpperBound(p, 2, true),
        "index>upperBound client→server"
    );
  }

  @Test
  public void pointIndexCountMismatchAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        Round4Mutations::pointIndexCountMismatch,
        "point/index mismatch server→client"
    );
  }

  @Test
  public void pointIndexCountMismatchAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        Round4Mutations::pointIndexCountMismatch,
        "point/index mismatch client→server"
    );
  }

  @Test
  public void validProofIncompleteCoverageAborts_serverToClient() throws Exception {
    assertVictimAborts(
        Direction.SERVER_TO_CLIENT,
        disjointSets(2),
        Round4Mutations::incompleteCoverageKeepProof,
        "incomplete coverage server→client"
    );
  }

  @Test
  public void validProofIncompleteCoverageAborts_clientToServer() throws Exception {
    assertVictimAborts(
        Direction.CLIENT_TO_SERVER,
        disjointSets(2),
        Round4Mutations::incompleteCoverageKeepProof,
        "incomplete coverage client→server"
    );
  }

  private void assertVictimAborts(
      Direction direction,
      PartySets sets,
      Function<List<byte[]>, List<byte[]>> mutator,
      String label
  ) throws Exception {
    Rpc serverRpc = firstRpc;
    Rpc clientRpc = secondRpc;
    if (direction == Direction.SERVER_TO_CLIENT) {
      serverRpc = new MutatingRound4Rpc(firstRpc, mutator);
    } else {
      clientRpc = new MutatingRound4Rpc(secondRpc, mutator);
    }

    Pgt26_2mPsuConfig config = new Pgt26_2mPsuConfig.Builder().build();
    PsuTwoSidedServer server = new Pgt26_2mPsuServer(serverRpc, secondRpc.ownParty(), config);
    PsuTwoSidedClient client = new Pgt26_2mPsuClient(clientRpc, firstRpc.ownParty(), config);
    int taskId = Math.abs(SECURE_RANDOM.nextInt());
    server.setTaskId(taskId);
    client.setTaskId(taskId);

    AtomicReference<Throwable> serverFail = new AtomicReference<>();
    AtomicReference<Throwable> clientFail = new AtomicReference<>();
    Thread st = new Thread(() -> {
      try {
        server.init(sets.server.size(), sets.client.size());
        server.getRpc().synchronize();
        server.psu(sets.server, sets.client.size(), ELEMENT_LEN);
      } catch (Throwable t) {
        serverFail.set(t);
      }
    });
    Thread ct = new Thread(() -> {
      try {
        client.init(sets.client.size(), sets.server.size());
        client.getRpc().synchronize();
        client.psu(sets.client, sets.server.size(), ELEMENT_LEN);
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
          "PGT26-2M Round4 coverage adversarial (" + label + ")"
      );
      Assert.fail("expected coverage/malformed abort: " + label);
    } catch (AssertionError expected) {
      Throwable victimErr = direction == Direction.SERVER_TO_CLIENT
          ? clientFail.get()
          : serverFail.get();
      if (victimErr == null && expected.getCause() != null) {
        victimErr = expected.getCause();
      }
      Assert.assertNotNull("victim should abort (" + label + ")", victimErr);
      Assert.assertTrue(
          "victim should MpcAbortException (" + label + "), was " + victimErr,
          victimErr instanceof MpcAbortException || victimErr.getCause() instanceof MpcAbortException
      );
      String msg = messageOf(victimErr).toLowerCase();
      // Bare MpcAbortPreconditions.checkArgument() may carry an empty message.
      if (!msg.isBlank()) {
        Assert.assertTrue(
            "diagnostic should identify coverage/malformed Round-4, was: " + msg,
            msg.contains("coverage")
                || msg.contains("index")
                || msg.contains("round-4")
                || msg.contains("round4")
                || msg.contains("argument")
                || msg.contains("malformed")
                || msg.contains("io error")
                || msg.contains("payload")
        );
      }
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

  private static PartySets disjointSets(int n) {
    Set<ByteBuffer> server = new HashSet<>();
    Set<ByteBuffer> client = new HashSet<>();
    for (int i = 0; i < n; i++) {
      server.add(elem(1, i));
      client.add(elem(2, i));
    }
    return new PartySets(server, client);
  }

  /** Partial intersection so expected coverage is a proper subset of {@code [0, n)}. */
  private static PartySets partialIntersectionSets(int n, int intersection) {
    Set<ByteBuffer> server = new HashSet<>();
    Set<ByteBuffer> client = new HashSet<>();
    for (int i = 0; i < intersection; i++) {
      ByteBuffer shared = elem(0, i);
      server.add(shared.duplicate());
      client.add(shared.duplicate());
    }
    for (int i = intersection; i < n; i++) {
      server.add(elem(1, i));
      client.add(elem(2, i));
    }
    return new PartySets(server, client);
  }

  private enum Direction {
    SERVER_TO_CLIENT,
    CLIENT_TO_SERVER
  }

  private static final class PartySets {
    final Set<ByteBuffer> server;
    final Set<ByteBuffer> client;

    PartySets(Set<ByteBuffer> server, Set<ByteBuffer> client) {
      this.server = server;
      this.client = client;
    }
  }

  /**
   * Mutations preserve payload.get(0) (RDDH proof blob) unless structural mismatch requires
   * leaving it untouched while changing points/indices only.
   */
  static final class Round4Mutations {
    private Round4Mutations() {
    }

    static List<byte[]> emptyIndices(List<byte[]> payload) {
      try {
        List<byte[]> mutated = new ArrayList<>(2);
        mutated.add(payload.get(0));
        mutated.add(Pgt26_2mWire.packIndices(new int[0]));
        return mutated;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    static List<byte[]> deleteOneRequired(List<byte[]> payload) {
      ParsedRound4 parsed = ParsedRound4.parse(payload);
      Assert.assertTrue("need at least one index to delete", parsed.indices.length >= 1);
      int keep = parsed.indices.length - 1;
      int[] indices = Arrays.copyOf(parsed.indices, keep);
      List<byte[]> points = new ArrayList<>(parsed.points.subList(0, keep));
      return ParsedRound4.repack(parsed.proof, points, indices);
    }

    static List<byte[]> sameCardinalityWrongIndex(List<byte[]> payload, int upperBound) {
      ParsedRound4 parsed = ParsedRound4.parse(payload);
      Assert.assertTrue(parsed.indices.length >= 1);
      boolean[] used = new boolean[upperBound];
      for (int idx : parsed.indices) {
        if (idx >= 0 && idx < upperBound) {
          used[idx] = true;
        }
      }
      int replacement = -1;
      for (int i = 0; i < upperBound; i++) {
        if (!used[i]) {
          replacement = i;
          break;
        }
      }
      Assert.assertTrue("need a non-expected in-range index (use partial intersection)", replacement >= 0);
      int[] indices = Arrays.copyOf(parsed.indices, parsed.indices.length);
      indices[0] = replacement;
      return ParsedRound4.repack(parsed.proof, parsed.points, indices);
    }

    static List<byte[]> duplicateIndices(List<byte[]> payload) {
      ParsedRound4 parsed = ParsedRound4.parse(payload);
      Assert.assertTrue(parsed.indices.length >= 2);
      int[] indices = Arrays.copyOf(parsed.indices, parsed.indices.length);
      indices[1] = indices[0];
      return ParsedRound4.repack(parsed.proof, parsed.points, indices);
    }

    static List<byte[]> negativeIndex(List<byte[]> payload) {
      ParsedRound4 parsed = ParsedRound4.parse(payload);
      Assert.assertTrue(parsed.indices.length >= 1);
      int[] indices = Arrays.copyOf(parsed.indices, parsed.indices.length);
      indices[0] = -1;
      return ParsedRound4.repack(parsed.proof, parsed.points, indices);
    }

    static List<byte[]> indexAtOrAboveUpperBound(List<byte[]> payload, int upperBound, boolean strictlyGreater) {
      ParsedRound4 parsed = ParsedRound4.parse(payload);
      Assert.assertTrue(parsed.indices.length >= 1);
      int[] indices = Arrays.copyOf(parsed.indices, parsed.indices.length);
      indices[0] = strictlyGreater ? upperBound + 1 : upperBound;
      return ParsedRound4.repack(parsed.proof, parsed.points, indices);
    }

    /** Keep indices, drop one point → {@code peerR4.size() != peerInd.length + 2}. */
    static List<byte[]> pointIndexCountMismatch(List<byte[]> payload) {
      ParsedRound4 parsed = ParsedRound4.parse(payload);
      Assert.assertTrue(parsed.points.size() >= 1);
      List<byte[]> points = new ArrayList<>(parsed.points);
      points.remove(points.size() - 1);
      return ParsedRound4.repack(parsed.proof, points, parsed.indices);
    }

    /** Valid proof blob + subset of required (index,point) pairs → coverage cardinality fail. */
    static List<byte[]> incompleteCoverageKeepProof(List<byte[]> payload) {
      return deleteOneRequired(payload);
    }
  }

  private static final class ParsedRound4 {
    final byte[] proof;
    final List<byte[]> points;
    final int[] indices;

    private ParsedRound4(byte[] proof, List<byte[]> points, int[] indices) {
      this.proof = proof;
      this.points = points;
      this.indices = indices;
    }

    static ParsedRound4 parse(List<byte[]> payload) {
      Assert.assertTrue(payload.size() >= 2);
      try {
        int[] indices = Pgt26_2mWire.unpackIndices(payload.get(payload.size() - 1));
        Assert.assertEquals(payload.size(), indices.length + 2);
        List<byte[]> points = new ArrayList<>(payload.subList(1, 1 + indices.length));
        return new ParsedRound4(payload.get(0), points, indices);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    static List<byte[]> repack(byte[] proof, List<byte[]> points, int[] indices) {
      try {
        List<byte[]> out = new ArrayList<>(2 + points.size());
        out.add(proof);
        out.addAll(points);
        out.add(Pgt26_2mWire.packIndices(indices));
        return out;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private static final class MutatingRound4Rpc implements Rpc {
    private final Rpc delegate;
    private final int round4Step = PtoStep.ROUND4_UNBLIND.ordinal();
    private final int ptoId = Pgt26_2mPsuPtoDesc.getInstance().getPtoId();
    private final Function<List<byte[]>, List<byte[]>> mutator;

    MutatingRound4Rpc(Rpc delegate, Function<List<byte[]>, List<byte[]>> mutator) {
      this.delegate = delegate;
      this.mutator = mutator;
    }

    @Override
    public void send(DataPacket dataPacket) {
      DataPacketHeader h = dataPacket.getHeader();
      if (h.getPtoId() == ptoId && h.getStepId() == round4Step) {
        List<byte[]> mutated = mutator.apply(dataPacket.getPayload());
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
