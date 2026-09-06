package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26CurveOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ProtocolTag;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26DdhKnowledgeProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided.Pgt26_1mPsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.shuffled.Pgt26ShuffledHashDh;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PGT26-1M client = P0 receiver (learns union). Reference {@code onesided.rs} {@code PartyReceiver1M}.
 */
public class Pgt26_1mPsuClient extends AbstractPsuClient implements PsuClient {
  private static final int OT_PAYLOAD_BYTES = Pgt26Constants.ITEM_BYTE_LENGTH + Pgt26DdhKnowledgeProof.PROOF_BYTES;

  private final CoreCotReceiver coreCotReceiver;
  private byte[] receiverScalar;
  private byte[] protocolTag;

  public Pgt26_1mPsuClient(Rpc clientRpc, Party serverParty, Pgt26_1mPsuConfig config) {
    super(Pgt26_1mPsuPtoDesc.getInstance(), clientRpc, serverParty, config);
    coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
    addSubPto(coreCotReceiver);
  }

  @Override
  public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
    setInitInput(maxClientElementSize, maxServerElementSize);
    logPhaseInfo(PtoState.INIT_BEGIN);
    stopWatch.start();
    receiverScalar = Pgt26CurveOps.randomScalar(secureRandom);
    protocolTag = Pgt26ProtocolTag.oneSided1m(
        getPtoDesc(), 0, maxClientElementSize, maxServerElementSize
    );
    coreCotReceiver.init();
    stopWatch.stop();
    logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
    stopWatch.reset();
    logPhaseInfo(PtoState.INIT_END);
  }

  @Override
  public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
      throws MpcAbortException {
    setPtoInput(clientElementSet, serverElementSize, elementByteLength);
    Pgt26ShuffledHashDh.assertItemLength(elementByteLength);
    logPhaseInfo(PtoState.PTO_BEGIN);

    byte[][] clientItems = Pgt26ShuffledHashDh.itemsFromByteBuffers(clientElementArrayList, elementByteLength);
    byte[][] blindedX = Pgt26ShuffledHashDh.blindOwnItems(clientItems, receiverScalar);

    stopWatch.start();
    sendBlindedX(blindedX);
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 1, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "send X");
    stopWatch.reset();

    stopWatch.start();
    List<byte[]> serverY = receiveServerY(serverElementSize);
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 2, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv Y");
    stopWatch.reset();

    stopWatch.start();
    byte[][] blindedServerY = Pgt26ShuffledHashDh.blindPeerItems(toArray(serverY), receiverScalar);
    List<byte[]> shuffledE = receiveShuffledE(clientElementSize);
    boolean[] intersection = Pgt26ShuffledHashDh.membershipBits(blindedServerY, toArray(shuffledE));
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 3, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "shuffle compare");
    stopWatch.reset();

    stopWatch.start();
    Set<ByteBuffer> union = runOtAndUnion(clientElementSet, serverY, intersection);
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 4, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "OT+union");
    stopWatch.reset();

    logPhaseInfo(PtoState.PTO_END);
    int psica = 0;
    for (boolean b : intersection) {
      if (b) {
        psica++;
      }
    }
    return new PsuClientOutput(union, psica);
  }

  private Set<ByteBuffer> runOtAndUnion(
      Set<ByteBuffer> clientSet, List<byte[]> serverY, boolean[] intersection
  ) throws MpcAbortException {
    int n = serverElementSize;
    CotReceiverOutput cotOut = coreCotReceiver.receive(intersection);
    List<byte[]> encPayload = receiveOtPayload(n);
    MpcAbortPreconditions.checkArgument(encPayload.size() == n);

    Prg prg = PrgFactory.createInstance(envType, OT_PAYLOAD_BYTES);
    Set<ByteBuffer> union = new HashSet<>(clientSet);
    for (int j = 0; j < n; j++) {
      if (intersection[j]) {
        continue;
      }
      byte[] payload = prg.extendToBytes(cotOut.getRb(j));
      BytesUtils.xori(payload, encPayload.get(j));
      byte[] item = Arrays.copyOfRange(payload, 0, Pgt26Constants.ITEM_BYTE_LENGTH);
      byte[] proof = Arrays.copyOfRange(payload, Pgt26Constants.ITEM_BYTE_LENGTH, OT_PAYLOAD_BYTES);
      byte[] hx = Pgt26CurveOps.hashToCurve(item);
      boolean ok;
      try {
        ok = Pgt26DdhKnowledgeProof.verify(item, hx, serverY.get(j), proof, protocolTag);
      } catch (Throwable t) {
        ok = false;
      }
      if (!ok) {
        continue;
      }
      union.add(ByteBuffer.wrap(BytesUtils.clone(item)));
    }
    return union;
  }

  private void sendBlindedX(byte[][] blindedX) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_BLINDED_X.ordinal(), extraInfo,
        ownParty().getPartyId(), otherParty().getPartyId()
    );
    rpc.send(DataPacket.fromByteArrayList(
        header, Arrays.stream(blindedX).map(BytesUtils::clone).collect(Collectors.toList())
    ));
  }

  private List<byte[]> receiveServerY(int n) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_BLINDED_Y.ordinal(), extraInfo,
        otherParty().getPartyId(), ownParty().getPartyId()
    );
    List<byte[]> payload = rpc.receive(header).getPayload();
    MpcAbortPreconditions.checkArgument(payload.size() == n);
    return payload;
  }

  private List<byte[]> receiveShuffledE(int n) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_SHUFFLED_E.ordinal(), extraInfo,
        otherParty().getPartyId(), ownParty().getPartyId()
    );
    List<byte[]> payload = rpc.receive(header).getPayload();
    MpcAbortPreconditions.checkArgument(payload.size() == n);
    return payload;
  }

  private List<byte[]> receiveOtPayload(int n) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_OT_PAYLOAD.ordinal(), extraInfo,
        otherParty().getPartyId(), ownParty().getPartyId()
    );
    List<byte[]> payload = rpc.receive(header).getPayload();
    MpcAbortPreconditions.checkArgument(payload.size() == n);
    return payload;
  }

  private static byte[][] toArray(List<byte[]> list) {
    return list.toArray(new byte[0][]);
  }
}
