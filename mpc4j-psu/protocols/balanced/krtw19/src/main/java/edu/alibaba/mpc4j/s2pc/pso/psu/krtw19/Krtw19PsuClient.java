package edu.alibaba.mpc4j.s2pc.pso.psu.krtw19;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.EmptyPadHashBin;
import edu.alibaba.mpc4j.common.tool.hashbin.object.HashBinEntry;
import edu.alibaba.mpc4j.common.tool.polynomial.gf2e.Gf2ePoly;
import edu.alibaba.mpc4j.common.tool.polynomial.gf2e.Gf2ePolyFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.*;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AC_KRTW19-PSU client.
 *
 * @author Weiran Liu
 * @date 2022/02/20
 */
public class Krtw19PsuClient extends AbstractPsuClient {
    /**
     * OPRF used in RMPT
     */
    private final OprfSender rpmtOprfSender;
    /**
     * OPRF used in PEQT
     */
    private final OprfReceiver peqtOprfReceiver;
    /**
     * core COT
     */
    private final CoreCotReceiver coreCotReceiver;
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
     * polynomial interpolation
     */
    private Gf2ePoly gf2ePoly;
    /**
     * field byte length
     */
    private int fieldByteLength;
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
    /**
     * private set different cardinality
     */
    private int difference;

    public Krtw19PsuClient(Rpc clientRpc, Party serverParty, Krtw19PsuConfig config) {
        super(Krtw19PsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        rpmtOprfSender = OprfFactory.createOprfSender(clientRpc, serverParty, config.getRpmtOprfConfig());
        addSubPto(rpmtOprfSender);
        peqtOprfReceiver = OprfFactory.createOprfReceiver(clientRpc, serverParty, config.getPeqtOprfConfig());
        addSubPto(peqtOprfReceiver);
        coreCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(coreCotReceiver);
        pipeSize = config.getPipeSize();
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        rpmtOprfSender.init(Krtw19PsuPtoDesc.MAX_BIN_NUM);
        peqtOprfReceiver.init(Krtw19PsuPtoDesc.MAX_BIN_NUM);
        coreCotReceiver.init();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 2, initTime);

        stopWatch.start();
        DataPacketHeader keysHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), Krtw19PsuPtoDesc.PtoStep.SERVER_SEND_KEYS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> keysPayload = rpc.receive(keysHeader).getPayload();
        MpcAbortPreconditions.checkArgument(keysPayload.size() == 1);
        hashBinKeys = new byte[keysPayload.size()][];
        for (int i = 0; i < keysPayload.size(); i++) {
            hashBinKeys[i] = keysPayload.get(i);
        }
        stopWatch.stop();
        long keyTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 2, 2, keyTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        initParams();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, initTime);

        Set<ByteBuffer> union = new HashSet<ByteBuffer>(serverElementSize + clientElementSize);
        for (int binColumnIndex = 0; binColumnIndex < maxBinSize; binColumnIndex++) {
            union.addAll(handleBinColumn(binColumnIndex));
        }
        union.addAll(clientElementSet);
        union.remove(botElementByteBuffer);

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, serverElementSize - difference);
    }

    private void initParams() {
        int n = Math.max(serverElementSize, clientElementSize);
        binNum = Krtw19PsuPtoDesc.getBinNum(n);
        maxBinSize = Krtw19PsuPtoDesc.getMaxBinSize(n);
        hashBin = new EmptyPadHashBin<ByteBuffer>(envType, binNum, maxBinSize, clientElementSize, hashBinKeys);
        hashBin.insertItems(clientElementArrayList);
        hashBin.insertPaddingItems(botElementByteBuffer);
        fieldByteLength = Krtw19PsuPtoDesc.getFiniteFieldByteLength(binNum, maxBinSize);
        int fieldBitLength = fieldByteLength * Byte.SIZE;
        finiteFieldHash = HashFactory.createInstance(envType, fieldByteLength);
        gf2ePoly = Gf2ePolyFactory.createInstance(envType, fieldBitLength);
        int peqtByteLength = Krtw19PsuPtoDesc.getPeqtByteLength(binNum, maxBinSize);
        peqtHash = HashFactory.createInstance(envType, peqtByteLength);
        encPrg = PrgFactory.createInstance(envType, elementByteLength);
        difference = 0;
    }

    private Set<ByteBuffer> handleBinColumn(int binColumnIndex) throws MpcAbortException {
        stopWatch.start();
        OprfSenderOutput rpmtOprfSenderOutput = rpmtOprfSender.oprf(binNum);
        stopWatch.stop();
        long qTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 1 + (binColumnIndex * 4), maxBinSize * 4, qTime);

        stopWatch.start();
        byte[][] ss = new byte[binNum][];
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            byte[] s = new byte[fieldByteLength];
            secureRandom.nextBytes(s);
            ss[binIndex] = s;
        }
        // pipeline execution
        int pipeTime = binNum / pipeSize;
        int round;
        for (round = 0; round < pipeTime; round++) {
            byte[][][] polys = generatePolys(rpmtOprfSenderOutput, ss, round * pipeSize, (round + 1) * pipeSize);
            List<byte[]> polyPayload = flattenPolys(polys);
            DataPacketHeader polyHeader = new DataPacketHeader(
                encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_POLYS.ordinal(), extraInfo,
                ownParty().getPartyId(), otherParty().getPartyId()
            );
            rpc.send(DataPacket.fromByteArrayList(polyHeader, polyPayload));
            extraInfo++;
        }
        int remain = binNum - round * pipeSize;
        if (remain > 0) {
            byte[][][] polys = generatePolys(rpmtOprfSenderOutput, ss, round * pipeSize, binNum);
            List<byte[]> polyPayload = flattenPolys(polys);
            DataPacketHeader polyHeader = new DataPacketHeader(
                encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_POLYS.ordinal(), extraInfo,
                ownParty().getPartyId(), otherParty().getPartyId()
            );
            rpc.send(DataPacket.fromByteArrayList(polyHeader, polyPayload));
            extraInfo++;
        }
        stopWatch.stop();
        long polyTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 2 + (binColumnIndex * 4), maxBinSize * 4, polyTime);

        stopWatch.start();
        OprfReceiverOutput peqtOprfReceiverOutput = peqtOprfReceiver.oprf(ss);
        byte[][] sOprfs = new byte[binNum][];
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            sOprfs[binIndex] = peqtHash.digestToBytes(peqtOprfReceiverOutput.getPrf(binIndex));
        }
        DataPacketHeader sStarOprfHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_S_STAR_OPRFS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> sStarOprfPayload = rpc.receive(sStarOprfHeader).getPayload();
        MpcAbortPreconditions.checkArgument(sStarOprfPayload.size() == binNum);
        byte[][] sStarOprfs = new byte[sStarOprfPayload.size()][];
        for (int i = 0; i < sStarOprfPayload.size(); i++) {
            sStarOprfs[i] = sStarOprfPayload.get(i);
        }
        boolean[] choices = new boolean[binNum];
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            choices[binIndex] = BytesUtils.equals(sOprfs[binIndex], sStarOprfs[binIndex]);
        }
        for (int i = 0; i < choices.length; i++) {
            if (!choices[i]) {
                difference++;
            }
        }
        stopWatch.stop();
        long peqtTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 3 + (binColumnIndex * 4), maxBinSize * 4, peqtTime);

        stopWatch.start();
        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(choices);
        DataPacketHeader encHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_ENC_ELEMENTS.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> encPayload = rpc.receive(encHeader).getPayload();
        MpcAbortPreconditions.checkArgument(encPayload.size() == binNum);
        ArrayList<byte[]> encArrayList = new ArrayList<byte[]>(encPayload);
        // Y \cup Z
        Set<ByteBuffer> binColumnUnion = new HashSet<ByteBuffer>(binNum);
        for (int binIndex = 0; binIndex < binNum; binIndex++) {
            if (choices[binIndex]) {
                binColumnUnion.add(botElementByteBuffer);
            } else {
                // do not need CRHF since we call prg
                byte[] message = encPrg.extendToBytes(cotReceiverOutput.getRb(binIndex));
                BytesUtils.xori(message, encArrayList.get(binIndex));
                binColumnUnion.add(ByteBuffer.wrap(message));
            }
        }
        stopWatch.stop();
        long unionTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logSubStepInfo(PtoState.PTO_STEP, 2, 4 + (binColumnIndex * 4), maxBinSize * 4, unionTime);

        return binColumnUnion;
    }

    private List<byte[]> flattenPolys(byte[][][] polys) {
        int total = 0;
        for (byte[][] poly : polys) {
            total += poly.length;
        }
        List<byte[]> polyPayload = new ArrayList<byte[]>(total);
        for (byte[][] poly : polys) {
            for (byte[] coeff : poly) {
                polyPayload.add(coeff);
            }
        }
        return polyPayload;
    }

    private byte[][][] generatePolys(OprfSenderOutput rpmtOprfSenderOutput, byte[][] ss, int start, int end) {
        byte[][][] polys = new byte[end - start][][];
        for (int binIndex = start; binIndex < end; binIndex++) {
            // q_i
            List<HashBinEntry<ByteBuffer>> bin = hashBin.getBin(binIndex);
            Set<ByteBuffer> distinctItems = new LinkedHashSet<ByteBuffer>();
            for (HashBinEntry<ByteBuffer> entry : bin) {
                distinctItems.add(entry.getItem());
            }
            byte[][] qs = new byte[distinctItems.size()][];
            int qIndex = 0;
            for (ByteBuffer item : distinctItems) {
                byte[] x = item.array();
                byte[] q = rpmtOprfSenderOutput.getPrf(binIndex, x);
                qs[qIndex++] = finiteFieldHash.digestToBytes(q);
            }
            polys[binIndex - start] = gf2ePoly.rootInterpolate(maxBinSize - 1, qs, ss[binIndex]);
        }

        return polys;
    }
}
