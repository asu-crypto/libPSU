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
import edu.alibaba.mpc4j.common.tool.crypto.prp.Prp;
import edu.alibaba.mpc4j.common.tool.crypto.prp.PrpFactory;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cParty;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprp.OprpFactory;
import edu.alibaba.mpc4j.s2pc.opf.oprp.OprpSender;
import edu.alibaba.mpc4j.s2pc.opf.oprp.OprpSenderOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * ZCL23-SKE-PSU client.
 *
 * @author Weiran Liu
 * @date 2022/02/16
 */
public class Zcl23SkePsuClient extends AbstractPsuClient {
    private static final String PARALLEL_STREAM = "IntStream.parallel";
    private static final String SERIAL = "serial";
    /**
     * Z2 circuit receiver
     */
    private final Z2cParty z2cReceiver;
    /**
     * OPRP协议发送方
     */
    private final OprpSender oprpSender;
    /**
     * 核COT协议接收方
     */
    private final CoreCotReceiver coreCotReceiver;
    /**
     * GF2K-DOKVS type
     */
    private final Gf2kDokvsType dokvsType;
    /**
     * GF2E-DOKVS hash keys
     */
    private byte[][] dokvsHashKeys;
    /**
     * PRP密钥
     */
    private byte[] prpKey;
    /**
     * PRP
     */
    private Prp prp;

    public Zcl23SkePsuClient(Rpc clientRpc, Party serverParty, Zcl23SkePsuConfig config) {
        super(Zcl23SkePsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        z2cReceiver = Z2cFactory.createReceiver(clientRpc, serverParty, config.getZ2cConfig());
        addSubPto(z2cReceiver);
        oprpSender = OprpFactory.createSender(z2cReceiver, serverParty, config.getOprpConfig());
        addSubPto(oprpSender);
        coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
        dokvsType = config.getGf2kDokvsType();
    }

    public Zcl23SkePsuClient(Rpc clientRpc, Party serverParty, Party aiderParty, Zcl23SkePsuConfig config) {
        super(Zcl23SkePsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        z2cReceiver = Z2cFactory.createReceiver(clientRpc, serverParty, aiderParty, config.getZ2cConfig());
        addSubPto(z2cReceiver);
        oprpSender = OprpFactory.createSender(z2cReceiver, serverParty, config.getOprpConfig());
        addSubPto(oprpSender);
        coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
        dokvsType = config.getGf2kDokvsType();
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        long expectNum = (long) maxServerElementSize * CommonConstants.BLOCK_BIT_LENGTH
            + OprpFactory.expectZ2TripleNum(oprpSender.getType(), maxServerElementSize);
        z2cReceiver.init((int) Math.min(expectNum, Integer.MAX_VALUE));
        oprpSender.init(maxServerElementSize);
        stopWatch.stop();
        long bcTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 3, bcTime);

        stopWatch.start();
        coreCotReceiver.init();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 3, initTime);

        stopWatch.start();
        DataPacketHeader keysHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_DOKVS_KEYS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> keysPayload = rpc.receive(keysHeader).getPayload();
        int dokvsHashKeyNum = Gf2kDokvsFactory.getHashKeyNum(dokvsType);
        MpcAbortPreconditions.checkArgument(keysPayload.size() == dokvsHashKeyNum);
        dokvsHashKeys = new byte[keysPayload.size()][];
        for (int i = 0; i < keysPayload.size(); i++) {
            dokvsHashKeys[i] = keysPayload.get(i);
        }
        prpKey = BlockUtils.randomBlock(secureRandom);
        prp = PrpFactory.createInstance(oprpSender.getPrpType());
        prp.setKey(prpKey);
        stopWatch.stop();
        long keyTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 3, 3, keyTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        beginPhaseMetric();
        List<byte[]> dokvsPayload = generateDokvsPayload();
        endPhaseMetric("DOKVS_ENCODE", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        DataPacketHeader dokvsHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_DOKVS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(dokvsHeader, dokvsPayload));
        endPhaseMetric("DOKVS_SEND", SERIAL);

        beginPhaseMetric();
        OprpSenderOutput oprpSenderOutput = oprpSender.oprp(prpKey, serverElementSize);
        endPhaseMetric("OPRP_LOWMC", SERIAL);

        byte[] peqtArray = generatePeqtShares(oprpSenderOutput);

        beginPhaseMetric();
        DataPacketHeader peqtSharesHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_PEQT_SHARES.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> peqtSharesPayload = rpc.receive(peqtSharesHeader).getPayload();
        MpcAbortPreconditions.checkArgument(peqtSharesPayload.size() == 1);
        byte[] serverPeqtShares = peqtSharesPayload.remove(0);
        BytesUtils.xori(peqtArray, serverPeqtShares);
        boolean[] choices = BinaryUtils.byteArrayToBinary(peqtArray, serverElementSize);
        int psica = 0;
        for (boolean choice : choices) {
            if (choice) {
                psica++;
            }
        }
        endPhaseMetric("PEQT_RECV_XOR", SERIAL);

        beginPhaseMetric();
        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(choices);
        endPhaseMetric("CORE_COT_RECV", SERIAL);

        beginPhaseMetric();
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> encPayload = rpc.receive(encHeader).getPayload();
        MpcAbortPreconditions.checkArgument(encPayload.size() == serverElementSize);
        ArrayList<byte[]> encArrayList = new ArrayList<byte[]>(encPayload);
        Prg encPrg = PrgFactory.createInstance(envType, elementByteLength);
        Set<ByteBuffer> union = new HashSet<ByteBuffer>(serverElementSize + clientElementSize);
        for (int index = 0; index < serverElementSize; index++) {
            if (choices[index]) {
                union.add(botElementByteBuffer);
            } else {
                byte[] message = encPrg.extendToBytes(cotReceiverOutput.getRb(index));
                BytesUtils.xori(message, encArrayList.get(index));
                union.add(ByteBuffer.wrap(message));
            }
        }
        union.addAll(clientElementSet);
        union.remove(botElementByteBuffer);
        endPhaseMetric("UNION_DEC", parallel ? PARALLEL_STREAM : SERIAL);

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }

    private List<byte[]> generateDokvsPayload() {
        Gf2kDokvs<ByteBuffer> dokvs = Gf2kDokvsFactory.<ByteBuffer>createInstance(
            envType, dokvsType, clientElementSize, dokvsHashKeys
        );
        Map<ByteBuffer, byte[]> keyValueMap = new HashMap<ByteBuffer, byte[]>(clientElementSize);
        for (int index = 0; index < clientElementSize; index++) {
            byte[] indexBlock = ByteBuffer.allocate(CommonConstants.BLOCK_BYTE_LENGTH)
                .putInt(CommonConstants.BLOCK_BYTE_LENGTH - Integer.BYTES, index)
                .array();
            byte[] value = oprpSender.isInvPrp() ? prp.prp(indexBlock) : prp.invPrp(indexBlock);
            keyValueMap.put(clientElementArrayList.get(index), value);
        }
        byte[][] encoded = dokvs.encode(keyValueMap, true);
        List<byte[]> payload = new ArrayList<byte[]>(encoded.length);
        for (byte[] row : encoded) {
            payload.add(row);
        }
        return payload;
    }

    private byte[] generatePeqtShares(OprpSenderOutput oprpSenderOutput) throws MpcAbortException {
        beginPhaseMetric();
        TransBitMatrix transBitMatrix = TransBitMatrixFactory.createInstance(
            envType, CommonConstants.BLOCK_BIT_LENGTH, serverElementSize, parallel
        );
        for (int i = 0; i < serverElementSize; i++) {
            transBitMatrix.setColumn(i, oprpSenderOutput.getShare(i));
        }
        endPhaseMetric("PEQT_BIT_MATRIX", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        TransBitMatrix transposeTransBitMatrix = transBitMatrix.transpose();
        endPhaseMetric("PEQT_TRANSPOSE", parallel ? PARALLEL_STREAM : SERIAL);

        beginPhaseMetric();
        int logSize = LongUtils.ceilLog2(clientElementSize);
        SquareZ2Vector clientPeqtShares = SquareZ2Vector.createOnes(serverElementSize);
        for (int index = 0; index < CommonConstants.BLOCK_BIT_LENGTH - logSize; index++) {
            byte[] bits = transposeTransBitMatrix.getColumn(index);
            SquareZ2Vector notBits = z2cReceiver.not(SquareZ2Vector.create(serverElementSize, bits, false));
            clientPeqtShares = z2cReceiver.and(clientPeqtShares, notBits);
        }
        endPhaseMetric("PEQT_Z2_AND_LOOP", SERIAL);
        return clientPeqtShares.getBitVector().getBytes();
    }
}
