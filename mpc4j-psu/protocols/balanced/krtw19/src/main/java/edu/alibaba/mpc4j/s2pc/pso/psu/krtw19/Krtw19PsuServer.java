package edu.alibaba.mpc4j.s2pc.pso.psu.krtw19;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.EmptyPadHashBin;
import edu.alibaba.mpc4j.common.tool.polynomial.gf2e.Gf2ePoly;
import edu.alibaba.mpc4j.common.tool.polynomial.gf2e.Gf2ePolyFactory;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.*;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AC_KRTW19-PSU server.
 *
 * @author Weiran Liu
 * @date 2022/02/20
 */
public class Krtw19PsuServer extends AbstractPsuServer {
    /**
     * OPRF used in RMPT
     */
    private final OprfReceiver rpmtOprfReceiver;
    /**
     * OPRF used in PEQT
     */
    private final OprfSender peqtOprfSender;
    /**
     * core COT
     */
    private final CoreCotSender coreCotSender;
    /**
     * pipeline size
     */
    private final int pipeSize;
    /**
     * bin hash keys
     */
    private byte[][] hashBinKeys;
    /**
     * bin num (β)
     */
    private int binNum;
    /**
     * max bin size (m)
     */
    private int maxBinSize;
    /**
     * simple hash bin
     */
    private EmptyPadHashBin<ByteBuffer> hashBin;
    /**
     * coefficient num
     */
    private int coefficientNum;
    /**
     * polynomial evaluation
     */
    private Gf2ePoly gf2ePoly;
    /**
     * finite field hash
     */
    private Hash finiteFieldHash;
    /**
     * PEQT hash
     */
    private Hash peqtHash;
    /**
     * PRG for encryption
     */
    private Prg encPrg;

    public Krtw19PsuServer(Rpc serverRpc, Party clientParty, Krtw19PsuConfig config) {
        super(Krtw19PsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        rpmtOprfReceiver = OprfFactory.createOprfReceiver(serverRpc, clientParty, config.getRpmtOprfConfig());
        addSubPto(rpmtOprfReceiver);
        peqtOprfSender = OprfFactory.createOprfSender(serverRpc, clientParty, config.getPeqtOprfConfig());
        addSubPto(peqtOprfSender);
        coreCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(coreCotSender);
        pipeSize = config.getPipeSize();
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        rpmtOprfReceiver.init(Krtw19PsuPtoDesc.MAX_BIN_NUM);
        peqtOprfSender.init(Krtw19PsuPtoDesc.MAX_BIN_NUM);
        byte[] delta = BlockUtils.randomBlock(secureRandom);
        coreCotSender.init(delta);
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 2, initTime);

        stopWatch.start();
        List<byte[]> keysPayload = new LinkedList<byte[]>();
        hashBinKeys = BlockUtils.zeroBlocks(1);
        secureRandom.nextBytes(hashBinKeys[0]);
        keysPayload.add(hashBinKeys[0]);
        DataPacketHeader keysHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_KEYS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(keysHeader, keysPayload));
        stopWatch.stop();
        long keyTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 2, keyTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        initParams();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, initTime);

        for (int binColumnIndex = 0; binColumnIndex < maxBinSize; binColumnIndex++) {
            handleBinColumn(binColumnIndex);
        }

        logPhaseInfo(PtoState.PTO_END);
    }

    private void initParams() {
        int n = Math.max(serverElementSize, clientElementSize);
        binNum = Krtw19PsuPtoDesc.getBinNum(n);
        maxBinSize = Krtw19PsuPtoDesc.getMaxBinSize(n);
        hashBin = new EmptyPadHashBin<ByteBuffer>(envType, binNum, maxBinSize, serverElementSize, hashBinKeys);
        hashBin.insertItems(serverElementArrayList);
        hashBin.insertPaddingItems(botElementByteBuffer);
        int fieldByteLength = Krtw19PsuPtoDesc.getFiniteFieldByteLength(binNum, maxBinSize);
        int fieldBitLength = fieldByteLength * Byte.SIZE;
        finiteFieldHash = HashFactory.createInstance(envType, fieldByteLength);
        gf2ePoly = Gf2ePolyFactory.createInstance(envType, fieldBitLength);
        // interpolate maxBinSize - 1 elements
        coefficientNum = gf2ePoly.rootCoefficientNum(maxBinSize - 1);
        int peqtLength = Krtw19PsuPtoDesc.getPeqtByteLength(binNum, maxBinSize);
        peqtHash = HashFactory.createInstance(getEnvType(), peqtLength);
        encPrg = PrgFactory.createInstance(envType, elementByteLength);
    }

    private void handleBinColumn(int binColumnIndex) throws MpcAbortException {
        stopWatch.start();
        byte[][] xs = new byte[binNum][];
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            xs[binIndex] = hashBin.getBin(binIndex).get(binColumnIndex).getItem().array();
        }
        OprfReceiverOutput rpmtOprfReceiverOprfOutput = rpmtOprfReceiver.oprf(xs);
        byte[][] qs = new byte[rpmtOprfReceiverOprfOutput.getBatchSize()][];
        for (int i = 0; i < rpmtOprfReceiverOprfOutput.getBatchSize(); i++) {
            qs[i] = finiteFieldHash.digestToBytes(rpmtOprfReceiverOprfOutput.getPrf(i));
        }
        stopWatch.stop();
        long qTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 1 + (binColumnIndex * 4), maxBinSize * 4, qTime);

        stopWatch.start();
        byte[][] ss = new byte[binNum][];
        // pipeline execution
        int pipelineTime = binNum / pipeSize;
        int round;
        for (round = 0; round < pipelineTime; round++) {
            DataPacketHeader polyHeader = new DataPacketHeader(
                encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_POLYS.ordinal(), extraInfo,
                otherParty().getPartyId(), ownParty().getPartyId()
            );
            List<byte[]> polyPayload = rpc.receive(polyHeader).getPayload();
            handlePoly(ss, qs, polyPayload, round * pipeSize, (round + 1) * pipeSize);
            extraInfo++;
        }
        int remain = binNum - round * pipeSize;
        if (remain > 0) {
            DataPacketHeader polyHeader = new DataPacketHeader(
                encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_POLYS.ordinal(), extraInfo,
                otherParty().getPartyId(), ownParty().getPartyId()
            );
            List<byte[]> polyPayload = rpc.receive(polyHeader).getPayload();
            handlePoly(ss, qs, polyPayload, round * pipeSize, binNum);
            extraInfo++;
        }
        stopWatch.stop();
        long polyTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 2 + (binColumnIndex * 4), maxBinSize * 4, polyTime);

        stopWatch.start();
        OprfSenderOutput peqtOprfSenderOutput = peqtOprfSender.oprf(binNum);
        List<byte[]> sStarPayload = new java.util.ArrayList<byte[]>(binNum);
        for (int sIndex = 0; sIndex < binNum; sIndex++) {
            sStarPayload.add(peqtHash.digestToBytes(peqtOprfSenderOutput.getPrf(sIndex, ss[sIndex])));
        }
        DataPacketHeader sStarHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_S_STAR_OPRFS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(sStarHeader, sStarPayload));
        stopWatch.stop();
        long peqtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 3 + (binColumnIndex * 4), maxBinSize * 4, peqtTime);

        stopWatch.start();
        CotSenderOutput cotSenderOutput = coreCotSender.send(binNum);
        List<byte[]> encPayload = new java.util.ArrayList<byte[]>(binNum);
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            byte[] element = xs[binIndex];
            byte[] ciphertext = encPrg.extendToBytes(cotSenderOutput.getR0(binIndex));
            BytesUtils.xori(ciphertext, element);
            encPayload.add(ciphertext);
        }
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(encHeader, encPayload));
        stopWatch.stop();
        long encTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 4 + (binColumnIndex * 4), maxBinSize * 4, encTime);
    }

    private void handlePoly(byte[][] ss, byte[][] qs, List<byte[]> polyPayload, int start, int end)
        throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(polyPayload.size() == (end - start) * coefficientNum);
        byte[][] flatPolyArray = new byte[polyPayload.size()][];
        for (int i = 0; i < polyPayload.size(); i++) {
            flatPolyArray[i] = polyPayload.get(i);
        }
        for (int index = start; index < end; index++) {
            int polyStart = (index - start) * coefficientNum;
            byte[][] coefficients = new byte[coefficientNum][];
            System.arraycopy(flatPolyArray, polyStart, coefficients, 0, coefficientNum);
            ss[index] = gf2ePoly.evaluate(coefficients, qs[index]);
        }
    }
}
