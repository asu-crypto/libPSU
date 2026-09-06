package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuTwoSidedServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26EdwardsMath;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ProtocolTag;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26AdaptedShuffleProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26BatchedRddhProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26PublicParams;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.shuffled.Pgt26ShuffledHashDh;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuPtoDesc.PtoStep;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * PGT26-2M server (P1). Reference {@code twosided.rs} with {@code server = true}.
 */
public class Pgt26_2mPsuServer extends AbstractPsuTwoSidedServer {
  private Pgt26PublicParams publicParams;
  private Pgt26_2mParty party;
  private final boolean skipShuffleProof;
  private final boolean skipRddhProof;

  public Pgt26_2mPsuServer(Rpc serverRpc, Party clientParty, Pgt26_2mPsuConfig config) {
    super(Pgt26_2mPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
    this.skipShuffleProof = config.isSkipShuffleProof();
    this.skipRddhProof = config.isSkipRddhProof();
  }

  @Override
  public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
    setInitInput(maxClientElementSize, maxServerElementSize);
    logPhaseInfo(PtoState.INIT_BEGIN, proofModeLogLine());
    stopWatch.start();
    int maxN = Math.max(maxClientElementSize, maxServerElementSize);
    publicParams = Pgt26PublicParams.setup(maxN);
    stopWatch.stop();
    logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
    stopWatch.reset();
    logPhaseInfo(PtoState.INIT_END);
  }

  @Override
  public PsuTwoSidedOutput psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
      throws MpcAbortException {
    setPtoInput(serverElementSet, clientElementSize, elementByteLength);
    Pgt26ShuffledHashDh.assertItemLength(elementByteLength);
    logPhaseInfo(PtoState.PTO_BEGIN, proofModeLogLine());
    byte[][] items = Pgt26ShuffledHashDh.itemsFromByteBuffers(serverElementArrayList, elementByteLength);
    byte[] aesKey = Pgt26ProtocolTag.feistelKey(getPtoDesc(), clientElementSize, serverElementSize);
    party = Pgt26_2mParty.create(items, aesKey, publicParams, clientElementSize, secureRandom);
    try {
      Set<ByteBuffer> union = runProtocol(true);
      logPhaseInfo(PtoState.PTO_END);
      return new PsuTwoSidedOutput(union);
    } catch (IOException e) {
      throw new MpcAbortException("PGT26-2M IO error: " + e.getMessage());
    }
  }

  private Set<ByteBuffer> runProtocol(boolean serverFirst) throws MpcAbortException, IOException {
    Pgt26_2mParty.GenOutput gen = party.gen();
    sendOne(PtoStep.ROUND1_SIGMA, gen.sigma);
    byte[] peerSigma = receiveOne(PtoStep.ROUND1_SIGMA);
    sendList(PtoStep.ROUND2_PK_POINTS, Pgt26_2mWire.round2Payload(gen.pk, gen.points));
    List<byte[]> peerR2 = receiveList(PtoStep.ROUND2_PK_POINTS);
    MpcAbortPreconditions.checkArgument(peerR2.size() == clientElementSize + 1);
    byte[] peerPk = peerR2.get(0);
    byte[][] peerPoints = Pgt26_2mWire.clonePoints(
        peerR2.subList(1, peerR2.size()).toArray(new byte[0][])
    );
    MpcAbortPreconditions.checkArgument(peerPoints.length == clientElementSize);
    if (!party.verifySigma(peerSigma, peerPk, peerPoints)) {
      throw new MpcAbortException("bad commitment");
    }
    byte[][] peerDecompressed = Pgt26EdwardsMath.decompressPoints(peerPoints);
    Pgt26_2mParty.ShuffleOutput shuffle = party.blindShuffle(publicParams, peerDecompressed, peerPoints);
    // Round 3: pack own shuffle of peer's points (size = clientElementSize).
    // Peer returns a shuffle of *our* points (size = serverElementSize).
    byte[] proofBytes = Pgt26_2mWire.packAdapted(shuffle.proof, clientElementSize);
    sendOne(PtoStep.ROUND3_SHUFFLE_PROOF, proofBytes);
    sendShuffledPoints(shuffle.shuffled);
    byte[] peerProofBytes = receiveOne(PtoStep.ROUND3_SHUFFLE_PROOF);
    byte[][] peerShuffled = receiveShuffledPoints(serverElementSize);
    Pgt26AdaptedShuffleProof peerProof = Pgt26_2mWire.unpackAdapted(peerProofBytes, serverElementSize);
    byte[][] ownDecompressed = Pgt26EdwardsMath.decompressPoints(gen.points);
    byte[][] peerShuffledDecompressed = Pgt26EdwardsMath.decompressPoints(peerShuffled);
    Pgt26_2mParty.UnblindOutput unblind = party.finalResponse(
        publicParams, peerPk, ownDecompressed, peerShuffledDecompressed, gen.points, peerShuffled, peerPoints,
        peerProof, skipShuffleProof, secureRandom
    );
    if (unblind == null) {
      String stage = party.lastShuffleVerifyFailure;
      throw new MpcAbortException(stage == null ? "bad shuffle proof" : "bad shuffle proof: " + stage);
    }
    List<byte[]> r4 = new ArrayList<>();
    r4.add(Pgt26_2mWire.packBatched(unblind.proof));
    for (byte[] p : unblind.unblinded) {
      r4.add(p);
    }
    r4.add(Pgt26_2mWire.packIndices(unblind.indices));
    sendList(PtoStep.ROUND4_UNBLIND, r4);
    List<byte[]> peerR4 = receiveList(PtoStep.ROUND4_UNBLIND);
    MpcAbortPreconditions.checkArgument(peerR4.size() >= 2);
    Pgt26BatchedRddhProof peerDdh = Pgt26_2mWire.unpackBatched(peerR4.get(0));
    int[] peerInd = Pgt26_2mWire.unpackIndices(peerR4.get(peerR4.size() - 1));
    MpcAbortPreconditions.checkArgument(peerR4.size() == peerInd.length + 2);
    byte[][] peerUnblinded = peerR4.subList(1, 1 + peerInd.length).toArray(new byte[0][]);
    byte[][] peerShrinked = new byte[peerInd.length][];
    // The peer proves against the shuffled points we sent to it in Round 3, not the peer's shuffled points.
    for (int i = 0; i < peerInd.length; i++) {
      MpcAbortPreconditions.checkArgument(peerInd[i] >= 0 && peerInd[i] < shuffle.shuffled.length);
      peerShrinked[i] = shuffle.shuffled[peerInd[i]];
    }
    byte[][] peerItems = party.revealPeerItems(peerPk, peerUnblinded, peerShrinked, peerDdh, skipRddhProof);
    if (peerItems == null) {
      if (skipRddhProof) {
        // Benchmark/debug mode: mapping/recovery may be incomplete; keep protocol running for timing.
        peerItems = new byte[0][];
      } else {
        String stage = party.lastRevealFailure;
        throw new MpcAbortException(stage == null ? "bad RDDH or decode" : "bad RDDH or decode: " + stage);
      }
    }
    Set<ByteBuffer> union = new HashSet<>(serverElementArrayList);
    for (byte[] it : peerItems) {
      union.add(ByteBuffer.wrap(BytesUtils.clone(it)));
    }
    return union;
  }

  private void sendOne(PtoStep step, byte[] payload) {
    DataPacketHeader header = header(step, ownParty().getPartyId(), otherParty().getPartyId());
    rpc.send(DataPacket.fromByteArrayList(header, List.of(Arrays.copyOf(payload, payload.length))));
  }

  private byte[] receiveOne(PtoStep step) throws MpcAbortException {
    DataPacketHeader header = header(step, otherParty().getPartyId(), ownParty().getPartyId());
    List<byte[]> list = rpc.receive(header).getPayload();
    MpcAbortPreconditions.checkArgument(list.size() == 1);
    return list.get(0);
  }

  private void sendList(PtoStep step, List<byte[]> payload) {
    DataPacketHeader header = header(step, ownParty().getPartyId(), otherParty().getPartyId());
    rpc.send(DataPacket.fromByteArrayList(header, Pgt26_2mWire.copyPayload(payload)));
  }

  private List<byte[]> receiveList(PtoStep step) throws MpcAbortException {
    DataPacketHeader header = header(step, otherParty().getPartyId(), ownParty().getPartyId());
    return rpc.receive(header).getPayload();
  }

  private void sendShuffledPoints(byte[][] shuffled) {
    sendList(PtoStep.ROUND3_SHUFFLED_POINTS, Arrays.asList(shuffled));
  }

  private byte[][] receiveShuffledPoints(int expected) throws MpcAbortException {
    List<byte[]> payload = receiveList(PtoStep.ROUND3_SHUFFLED_POINTS);
    MpcAbortPreconditions.checkArgument(
        payload.size() == expected,
        "Round-3 shuffled points: expected " + expected + " got " + payload.size()
    );
    return Pgt26_2mWire.clonePoints(payload.toArray(new byte[0][]));
  }

  private String proofModeLogLine() {
    return "shuffleProofEnabled=" + !skipShuffleProof + ", rddhProofEnabled=" + !skipRddhProof;
  }

  private DataPacketHeader header(PtoStep step, int from, int to) {
    return new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), step.ordinal(), extraInfo, from, to
    );
  }
}
