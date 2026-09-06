package edu.alibaba.mpc4j.s2pc.pso.psu.jszg24;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prf.Prf;
import edu.alibaba.mpc4j.common.tool.crypto.prf.PrfFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.MaxBinSizeUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnPartyOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnSender;
import edu.alibaba.mpc4j.s2pc.opf.opprf.batch.BopprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.opprf.batch.BopprfSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JSZG24 receiver (paper R): simple-hash side, OPPRF programmer, bECRG receiver, value owner in PS, union output.
 */
public class Jszg24BecrgPsuClient extends AbstractPsuClient {
    /**
     * config
     */
    private final Jszg24BecrgPsuConfig config;
    /**
     * batch OPPRF sender (programmer).
     */
    private final BopprfSender bopprfSender;
    /**
     * PET receiver (PEQT receiver).
     */
    private final PeqtParty peqtReceiver;
    /**
     * LNOT receiver (l = 1) used to implement eqOTe.
     */
    private final LnotReceiver lnotReceiver;
    /**
     * Permute+Share value owner (DOSN sender).
     */
    private final DosnSender dosnSender;

    public Jszg24BecrgPsuClient(Rpc clientRpc, Party serverParty, Jszg24BecrgPsuConfig config) {
        super(Jszg24BecrgPsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        this.config = config;
        bopprfSender = BopprfFactory.createSender(clientRpc, serverParty, config.getBopprfConfig());
        addSubPto(bopprfSender);
        peqtReceiver = PeqtFactory.createReceiver(clientRpc, serverParty, config.getPeqtConfig());
        addSubPto(peqtReceiver);
        lnotReceiver = LnotFactory.createReceiver(clientRpc, serverParty, config.getLnotConfig());
        addSubPto(lnotReceiver);
        dosnSender = DosnFactory.createSender(clientRpc, serverParty, config.getDosnConfig());
        addSubPto(dosnSender);
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        int bMax = (int) Math.ceil(config.getEpsilon() * maxServerElementSize);
        int maxPointNum = Math.max(1, config.getGamma() * maxClientElementSize);
        bopprfSender.init(bMax, maxPointNum);
        int l2Max = config.getLambda() + LongUtils.ceilLog2(bMax);
        peqtReceiver.init(l2Max, bMax);
        lnotReceiver.init(1, bMax);
        dosnSender.init();
        stopWatch.stop();
        long t = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, t);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength) throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);
        MathPreconditions.checkPositive("serverElementSize", serverElementSize);
        MathPreconditions.checkPositive("clientElementSize", clientElementSize);
        MpcAbortPreconditions.checkArgument(elementByteLength == CommonUtils.getByteLength(config.getItemBitLength()));

        // ---------------- Fig.17 Step 1: receive hash keys; build simple-hash bins Y_S ----------------
        stopWatch.start();
        int binNum = (int) Math.ceil(config.getEpsilon() * serverElementSize);
        int gamma = config.getGamma();
        List<byte[]> hashKeyPayload = receiveOtherPartyPayload(PtoStep.HASH_KEYS.ordinal());
        MpcAbortPreconditions.checkArgument(hashKeyPayload.size() == gamma);
        byte[][] hashKeys = hashKeyPayload.toArray(new byte[0][]);
        Hash dummyHash = HashFactory.createInstance(envType, elementByteLength);
        byte[] d = dummyHash.digestToBytes(concat(hashKeys));
        // build γ keyed hashes to map items to bins
        Prf[] hashes = new Prf[gamma];
        for (int i = 0; i < gamma; i++) {
            hashes[i] = PrfFactory.createInstance(envType, Integer.BYTES);
            hashes[i].setKey(hashKeys[i]);
        }
        // simple hashing: insert each y into all γ bins
        @SuppressWarnings("unchecked")
        HashSet<ByteBuffer>[] yBinSets = IntStream.range(0, binNum)
            .mapToObj(i -> new HashSet<ByteBuffer>(Math.max(1, clientElementSize / binNum + 1)))
            .toArray(HashSet[]::new);
        for (ByteBuffer y : clientElementSet) {
            byte[] yBytes = y.array();
            for (int h = 0; h < gamma; h++) {
                int idx = Math.floorMod(IntUtils.byteArrayToInt(hashes[h].getBytes(yBytes)), binNum);
                // Deduplicate within a bin: multiple hash functions can map the same item to the same bin.
                // Clone to enforce a canonical (position=0, limit=len) buffer identity for HashSet semantics.
                yBinSets[idx].add(ByteBuffer.wrap(yBytes.clone()));
            }
        }
        // approximate rho (max bin size) for sizing only; OPPRF path does not require padding.
        int rho = MaxBinSizeUtils.expectMaxBinSize(gamma * clientElementSize, binNum);
        stopWatch.stop();
        long hashTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 6, hashTime, "Fig.17 Step 1: receive keys + simple hashing");

        // ---------------- Fig.17 Step 2: receive {t'_i} ----------------
        stopWatch.start();
        List<byte[]> tPrimePayload = receiveOtherPartyPayload(PtoStep.T_PRIME.ordinal());
        MpcAbortPreconditions.checkArgument(tPrimePayload.size() == binNum);
        int l2 = config.getLambda() + LongUtils.ceilLog2(binNum);
        int tByteLen = CommonUtils.getByteLength(l2);
        byte[][] tPrime = tPrimePayload.toArray(new byte[0][]);
        for (byte[] tp : tPrime) {
            MpcAbortPreconditions.checkArgument(tp.length == tByteLen);
        }
        // Defensive: ensure t'_i is ℓ2-bit reduced (required by batch OPPRF target validation).
        for (int i = 0; i < binNum; i++) {
            BytesUtils.reduceByteArray(tPrime[i], l2);
        }
        stopWatch.stop();
        long tPrimeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 6, tPrimeTime, "Fig.17 Step 2: receive t'_i");

        // ---------------- Fig.17 Step 3: OPPRF programming ----------------
        stopWatch.start();
        byte[][][] inputArrays = new byte[binNum][][];
        byte[][][] targetArrays = new byte[binNum][][];
        int pointNum = 0;
        for (int i = 0; i < binNum; i++) {
            final int binIndex = i;
            // stable iteration order
            List<ByteBuffer> ys = new ArrayList<>(yBinSets[i]);
            inputArrays[i] = ys.stream().map(bb -> encodeBinPoint(binIndex, bb.array())).toArray(byte[][]::new);
            targetArrays[i] = new byte[ys.size()][];
            for (int j = 0; j < ys.size(); j++) {
                targetArrays[i][j] = tPrime[i];
            }
            pointNum += ys.size();
        }
        sendOtherPartyPayload(PtoStep.OPPRF_POINT_NUM.ordinal(), Collections.singletonList(IntUtils.intToByteArray(pointNum)));
        bopprfSender.opprf(l2, inputArrays, targetArrays);
        stopWatch.stop();
        long opprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 6, opprfTime, "Fig.17 Step 3: OPPRF programming");

        // ---------------- Fig.17 Step 4: bECRG receive via PET + eqOTe ----------------
        stopWatch.start();
        SquareZ2Vector eqShare1 = peqtReceiver.peqt(l2, tPrime);
        boolean[] neqShare1 = new boolean[binNum];
        for (int i = 0; i < binNum; i++) {
            // neq = 1 ⊕ eq: flip receiver share.
            neqShare1[i] = !eqShare1.getBitVector().get(i);
        }
        int[] choiceArray = new int[binNum];
        for (int i = 0; i < binNum; i++) {
            choiceArray[i] = neqShare1[i] ? 1 : 0;
        }
        LnotReceiverOutput lnotOut1 = lnotReceiver.receive(choiceArray);
        Prg prg = PrgFactory.createInstance(envType, elementByteLength);
        List<byte[]> eqOtePayload = receiveOtherPartyEqualSizePayload(PtoStep.BECRG_EQOTE.ordinal(), binNum, elementByteLength * 2);
        byte[][] rPrime = new byte[binNum][elementByteLength];
        for (int i = 0; i < binNum; i++) {
            byte[] row = eqOtePayload.get(i);
            byte[] c0 = new byte[elementByteLength];
            byte[] c1 = new byte[elementByteLength];
            System.arraycopy(row, 0, c0, 0, elementByteLength);
            System.arraycopy(row, elementByteLength, c1, 0, elementByteLength);
            byte[] k = prg.extendToBytes(lnotOut1.getRb(i));
            byte[] chosen = (choiceArray[i] == 0) ? c0 : c1;
            rPrime[i] = BytesUtils.xor(chosen, k);
        }
        stopWatch.stop();
        long becrgTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 6, becrgTime, "Fig.17 Step 4: bECRG output r'_i");

        // ---------------- Fig.17 Step 5: Permute+Share (R holds r'_i) ----------------
        stopWatch.start();
        DosnPartyOutput s2Out = dosnSender.dosn(rPrime, elementByteLength);
        byte[][] s2 = s2Out.getShareVector();
        stopWatch.stop();
        long psTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 5, 6, psTime, "Fig.17 Step 5: Permute+Share");

        // ---------------- Fig.17 Step 6-7: receive ciphertexts; decrypt candidates; output union ----------------
        stopWatch.start();
        List<byte[]> cPayload = receiveOtherPartyEqualSizePayload(PtoStep.SEND_CIPHERTEXTS.ordinal(), binNum, elementByteLength);
        Set<ByteBuffer> z = new HashSet<>();
        for (int i = 0; i < binNum; i++) {
            byte[] zi = BytesUtils.xor(cPayload.get(i), s2[i]);
            if (!BytesUtils.equals(zi, d)) {
                z.add(ByteBuffer.wrap(zi));
            }
        }
        Set<ByteBuffer> union = new HashSet<>(clientElementSet);
        union.addAll(z);
        stopWatch.stop();
        long outTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 6, 6, outTime, "Fig.17 Step 6-8: decrypt + output union");

        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, 0);
    }

    private static byte[] concat(byte[][] xs) {
        int len = 0;
        for (byte[] x : xs) {
            len += x.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] x : xs) {
            System.arraycopy(x, 0, out, off, x.length);
            off += x.length;
        }
        return out;
    }

    /**
     * Domain-separates OPPRF points by bin index: point = int(binIndex) || itemBytes.
     * This avoids duplicates because a receiver item is inserted into multiple bins in simple hashing.
     */
    private static byte[] encodeBinPoint(int binIndex, byte[] item) {
        byte[] prefix = IntUtils.intToByteArray(binIndex);
        byte[] out = new byte[prefix.length + item.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(item, 0, out, prefix.length, item.length);
        return out;
    }
}

