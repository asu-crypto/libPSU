package edu.alibaba.mpc4j.s2pc.pso.psu.zcl23;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvs;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvsFactory;
import edu.alibaba.mpc4j.common.structure.okve.dokvs.gf2k.Gf2kDokvsFactory.Gf2kDokvsType;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.bitmatrix.trans.TransBitMatrix;
import edu.alibaba.mpc4j.common.tool.bitmatrix.trans.TransBitMatrixFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.opf.oprp.OprpFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprp.OprpReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprp.OprpReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ZCL23-SKE-PSU protocol server.
 *
 * @author Weiran Liu
 * @date 2022/02/16
 */
public class Zcl23SkePsuServer extends AbstractPsuServer {
    private static final String PARALLEL_STREAM = "IntStream.parallel";
    private static final String SERIAL = "serial";
    /**
     * Z2 circuit sender
     */
    private final Z2cParty z2cSender;
    /**
     * OPRP协议接收方
     */
    private final OprpReceiver oprpReceiver;
    /**
     * 核COT协议发送方
     */
    private final CoreCotSender coreCotSender;
    /**
     * GF2K-DOKVS type
     */
    private final Gf2kDokvsType dokvsType;
    /**
     * GF2E-DOKVS hash keys
     */
    private byte[][] dokvsHashKeys;
    /**
     * 服务端密文
     */
    private byte[][] senderMessages;

    public Zcl23SkePsuServer(Rpc serverRpc, Party clientParty, Zcl23SkePsuConfig config) {
        super(Zcl23SkePsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        z2cSender = Z2cFactory.createSender(serverRpc, clientParty, config.getZ2cConfig());
        addSubPto(z2cSender);
        oprpReceiver = OprpFactory.createReceiver(z2cSender, clientParty, config.getOprpConfig());
        addSubPto(oprpReceiver);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
        dokvsType = config.getGf2kDokvsType();
    }

    public Zcl23SkePsuServer(Rpc serverRpc, Party clientParty, Party aiderParty, Zcl23SkePsuConfig config) {
        super(Zcl23SkePsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        z2cSender = Z2cFactory.createSender(serverRpc, clientParty, aiderParty, config.getZ2cConfig());
        addSubPto(z2cSender);
        oprpReceiver = OprpFactory.createReceiver(z2cSender, clientParty, config.getOprpConfig());
        addSubPto(oprpReceiver);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
        dokvsType = config.getGf2kDokvsType();
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        long expectNum = (long) maxServerElementSize * CommonConstants.BLOCK_BIT_LENGTH
            + OprpFactory.expectZ2TripleNum(oprpReceiver.getType(), maxServerElementSize);
        z2cSender.init((int) Math.min(expectNum, Integer.MAX_VALUE));
        oprpReceiver.init(maxServerElementSize);
        stopWatch.stop();
        long bcTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 3, bcTime);

        stopWatch.start();
        // 其他部分初始化
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        coreCotSender.init(delta);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 3, initTime);

        stopWatch.start();
        List<byte[]> keysPayload = new LinkedList<>();
        // init DOKVS hash keys
        int dokvsHashKeyNum = Gf2kDokvsFactory.getHashKeyNum(dokvsType);
        dokvsHashKeys = IntStream.range(0, dokvsHashKeyNum)
            .mapToObj(keyIndex -> {
                byte[] key = BlockUtils.randomBlock(secureRandom);
                keysPayload.add(key);
                return key;
            })
            .toArray(byte[][]::new);
        DataPacketHeader keysHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), Zcl23SkePsuPtoDesc.PtoStep.SERVER_SEND_DOKVS_KEYS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(keysHeader, keysPayload));
        stopWatch.stop();
        long keyTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 3, 3, keyTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        beginPhaseMetric();
        DataPacketHeader dokvsHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_DOKVS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> dokvsPayload = rpc.receive(dokvsHeader).getPayload();
        endPhaseMetric("DOKVS_RECV", SERIAL);

        beginPhaseMetric();
        handleDokvsPayload(dokvsPayload);
        endPhaseMetric("DOKVS_DECODE", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        OprpReceiverOutput oprpReceiverOutput = oprpReceiver.oprp(senderMessages);
        endPhaseMetric("OPRP_LOWMC", SERIAL);

        byte[] serverChoiceShares = generatePeqtShares(oprpReceiverOutput);

        beginPhaseMetric();
        List<byte[]> peqtSharesPayload = new LinkedList<>();
        peqtSharesPayload.add(serverChoiceShares);
        DataPacketHeader peqtSharesHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PEQT_SHARES.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(peqtSharesHeader, peqtSharesPayload));
        endPhaseMetric("PEQT_SHARE_SEND", SERIAL);

        beginPhaseMetric();
        CotSenderOutput cotSenderOutput = coreCotSender.send(serverElementSize);
        endPhaseMetric("CORE_COT_SEND", SERIAL);

        beginPhaseMetric();
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        IntStream encIntStream = IntStream.range(0, serverElementSize);
        encIntStream = parallel ? encIntStream.parallel() : encIntStream;
        List<byte[]> encPayload = encIntStream
            .mapToObj(index -> {
                byte[] ciphertext = encPrg.extendToBytes(cotSenderOutput.getR0(index));
                BytesUtils.xori(ciphertext, serverElementArrayList.get(index).array());
                return ciphertext;
            })
            .collect(Collectors.toList());
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(encHeader, encPayload));
        endPhaseMetric("ENC_UNION_SEND", parallel ? PARALLEL_STREAM : SERIAL);

        logPhaseInfo(PtoState.PTO_END);
    }

    private void handleDokvsPayload(List<byte[]> dokvsPayload) throws MpcAbortException {
        int dokvsM = Gf2kDokvsFactory.getM(envType, dokvsType, clientElementSize);
        MpcAbortPreconditions.checkArgument(dokvsPayload.size() == dokvsM);
        byte[][] storages = dokvsPayload.toArray(new byte[0][]);
        Gf2kDokvs<ByteBuffer> dokvs = Gf2kDokvsFactory.createInstance(envType, dokvsType, clientElementSize, dokvsHashKeys);
        IntStream senderMessageIntStream = IntStream.range(0, serverElementSize);
        senderMessageIntStream = parallel ? senderMessageIntStream.parallel() : senderMessageIntStream;
        senderMessages = senderMessageIntStream
            .mapToObj(index -> dokvs.decode(storages, serverElementArrayList.get(index)))
            .toArray(byte[][]::new);
    }

    private byte[] generatePeqtShares(OprpReceiverOutput oprpReceiverOutput) throws MpcAbortException {
        beginPhaseMetric();
        TransBitMatrix transBitMatrix = TransBitMatrixFactory.createInstance(
            envType, CommonConstants.BLOCK_BIT_LENGTH, serverElementSize, parallel
        );
        for (int i = 0; i < serverElementSize; i++) {
            transBitMatrix.setColumn(i, oprpReceiverOutput.getShare(i));
        }
        endPhaseMetric("PEQT_BIT_MATRIX", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        TransBitMatrix transposeTransBitMatrix = transBitMatrix.transpose();
        endPhaseMetric("PEQT_TRANSPOSE", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        int logSize = LongUtils.ceilLog2(clientElementSize);
        SquareZ2Vector serverPeqtShares = SquareZ2Vector.createOnes(serverElementSize);
        for (int index = 0; index < CommonConstants.BLOCK_BIT_LENGTH - logSize; index++) {
            byte[] bits = transposeTransBitMatrix.getColumn(index);
            SquareZ2Vector notBits = z2cSender.not(SquareZ2Vector.create(serverElementSize, bits, false));
            serverPeqtShares = z2cSender.and(serverPeqtShares, notBits);
        }
        endPhaseMetric("PEQT_Z2_AND_LOOP", SERIAL);
        return serverPeqtShares.getBitVector().getBytes();
    }
}
