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
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.common.tool.crypto.ecc.utils.Ed25519ByteEccUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26Constants;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26CurveOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26ProtocolTag;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26TestHooks;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.Pgt26TestHooks.Mode;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.aok.Pgt26DdhKnowledgeProof;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided.Pgt26_1mPsuPtoDesc.PtoStep;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.shuffled.Pgt26ShuffledHashDh;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * PGT26-1M server = P1 sender (no output). Reference {@code onesided.rs} {@code PartySender1M}.
 */
public class Pgt26_1mPsuServer extends AbstractPsuServer implements PsuServer {
  private static final int OT_PAYLOAD_BYTES = Pgt26Constants.ITEM_BYTE_LENGTH + Pgt26DdhKnowledgeProof.PROOF_BYTES;

  private final CoreCotSender coreCotSender;
  private byte[] senderScalar;
  private int[] shufflePermutation;
  private byte[] protocolTag;

  public Pgt26_1mPsuServer(Rpc serverRpc, Party clientParty, Pgt26_1mPsuConfig config) {
    super(Pgt26_1mPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
    coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
    addSubPto(coreCotSender);
  }

  @Override
  public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
    setInitInput(maxServerElementSize, maxClientElementSize);
    logPhaseInfo(PtoState.INIT_BEGIN);
    stopWatch.start();
    senderScalar = Pgt26CurveOps.randomScalar(secureRandom);
    shufflePermutation = Pgt26ShuffledHashDh.randomPermutation(maxClientElementSize, secureRandom);
    protocolTag = Pgt26ProtocolTag.oneSided1m(
        getPtoDesc(), 0, maxClientElementSize, maxServerElementSize
    );
    byte[] delta = BlockUtils.randomBlock(secureRandom);
    coreCotSender.init(delta);
    stopWatch.stop();
    logStepInfo(PtoState.INIT_STEP, 1, 1, stopWatch.getTime(TimeUnit.MILLISECONDS));
    stopWatch.reset();
    logPhaseInfo(PtoState.INIT_END);
  }

  @Override
  public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
      throws MpcAbortException {
    setPtoInput(serverElementSet, clientElementSize, elementByteLength);
    Pgt26ShuffledHashDh.assertItemLength(elementByteLength);
    logPhaseInfo(PtoState.PTO_BEGIN);

    stopWatch.start();
    byte[][] peerBlindedX = receiveBlindedX(clientElementSize);
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 1, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "recv X");
    stopWatch.reset();

    byte[][] serverItems = Pgt26ShuffledHashDh.itemsFromByteBuffers(serverElementArrayList, elementByteLength);
    byte[][] blindedY = new byte[serverElementSize][];
    byte[][] hx = new byte[serverElementSize][];
    for (int j = 0; j < serverElementSize; j++) {
      hx[j] = Pgt26CurveOps.hashToCurve(serverItems[j]);
      blindedY[j] = Pgt26CurveOps.scalarMul(hx[j], senderScalar);
    }

    stopWatch.start();
    sendBlindedY(blindedY);
    byte[][] shuffledE = Pgt26ShuffledHashDh.shuffleBlind(peerBlindedX, senderScalar, shufflePermutation);
    sendShuffledE(shuffledE);
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 2, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "HashDH");
    stopWatch.reset();

    stopWatch.start();
    sendOtPayloads(serverItems, hx, blindedY);
    stopWatch.stop();
    logStepInfo(PtoState.PTO_STEP, 3, 4, stopWatch.getTime(TimeUnit.MILLISECONDS), "OT");
    stopWatch.reset();

    logPhaseInfo(PtoState.PTO_END);
  }

  private void sendOtPayloads(byte[][] serverItems, byte[][] hx, byte[][] blindedY) throws MpcAbortException {
    int n = serverElementSize;
    CotSenderOutput cotOut = coreCotSender.send(n);
    Prg prg = PrgFactory.createInstance(envType, OT_PAYLOAD_BYTES);
    List<byte[]> encPayload = new ArrayList<>(n);
    for (int j = 0; j < n; j++) {
      byte[] item = serverItems[j];
      byte[] proof = Pgt26DdhKnowledgeProof.prove(
          item, hx[j], blindedY[j], senderScalar, secureRandom, protocolTag
      );
      if (Pgt26TestHooks.mode == Mode.INVALID_PROOF) {
        secureRandom.nextBytes(proof);
      } else if (Pgt26TestHooks.mode == Mode.WRONG_ITEM_BINDING && j == 0) {
        item = BytesUtils.randomByteArray(Pgt26Constants.ITEM_BYTE_LENGTH, secureRandom);
        proof = Pgt26DdhKnowledgeProof.prove(
            serverItems[0], hx[0], blindedY[0], senderScalar, secureRandom, protocolTag
        );
      }
      byte[] payload = new byte[OT_PAYLOAD_BYTES];
      System.arraycopy(item, 0, payload, 0, Pgt26Constants.ITEM_BYTE_LENGTH);
      System.arraycopy(proof, 0, payload, Pgt26Constants.ITEM_BYTE_LENGTH, Pgt26DdhKnowledgeProof.PROOF_BYTES);
      byte[] ciphertext = prg.extendToBytes(cotOut.getR0(j));
      BytesUtils.xori(ciphertext, payload);
      encPayload.add(ciphertext);
    }
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_OT_PAYLOAD.ordinal(), extraInfo,
        ownParty().getPartyId(), otherParty().getPartyId()
    );
    rpc.send(DataPacket.fromByteArrayList(header, encPayload));
  }

  private byte[][] receiveBlindedX(int n) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_BLINDED_X.ordinal(), extraInfo,
        otherParty().getPartyId(), ownParty().getPartyId()
    );
    List<byte[]> payload = rpc.receive(header).getPayload();
    MpcAbortPreconditions.checkArgument(payload.size() == n);
    byte[][] blindedX = new byte[n][];
    for (int i = 0; i < n; i++) {
      blindedX[i] = payload.get(i);
      MpcAbortPreconditions.checkArgument(blindedX[i].length == Ed25519ByteEccUtils.POINT_BYTES);
    }
    return blindedX;
  }

  private void sendBlindedY(byte[][] blindedY) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_BLINDED_Y.ordinal(), extraInfo,
        ownParty().getPartyId(), otherParty().getPartyId()
    );
    rpc.send(DataPacket.fromByteArrayList(
        header, Arrays.stream(blindedY).map(BytesUtils::clone).collect(Collectors.toList())
    ));
  }

  private void sendShuffledE(byte[][] shuffledE) throws MpcAbortException {
    DataPacketHeader header = new DataPacketHeader(
        encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_SHUFFLED_E.ordinal(), extraInfo,
        ownParty().getPartyId(), otherParty().getPartyId()
    );
    rpc.send(DataPacket.fromByteArrayList(
        header, Arrays.stream(shuffledE).map(BytesUtils::clone).collect(Collectors.toList())
    ));
  }
}
