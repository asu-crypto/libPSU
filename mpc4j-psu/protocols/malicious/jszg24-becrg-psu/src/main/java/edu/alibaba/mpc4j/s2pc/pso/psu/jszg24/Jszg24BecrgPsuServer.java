package edu.alibaba.mpc4j.s2pc.pso.psu.jszg24;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.PtoState;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBin;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.network.PermutationNetworkUtils;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.SquareZ2Vector;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtFactory;
import edu.alibaba.mpc4j.s2pc.aby.operator.row.peqt.PeqtParty;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnPartyOutput;
import edu.alibaba.mpc4j.s2pc.aby.pcg.osn.dosn.DosnReceiver;
import edu.alibaba.mpc4j.s2pc.opf.opprf.batch.BopprfFactory;
import edu.alibaba.mpc4j.s2pc.opf.opprf.batch.BopprfReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotSender;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * JSZG24 sender (paper S): Cuckoo side, OPPRF query party, bECRG sender, permutation owner in PS, ciphertext sender.
 *
 * <p>Semantics: sender outputs Finished only (no union output).</p>
 */
public class Jszg24BecrgPsuServer extends AbstractPsuServer {
    /**
     * config
     */
    private final Jszg24BecrgPsuConfig config;
    /**
     * batch OPPRF receiver (programmed by receiver).
     */
    private final BopprfReceiver bopprfReceiver;
    /**
     * PET sender (PEQT sender).
     */
    private final PeqtParty peqtSender;
    /**
     * LNOT sender (l = 1) used to implement eqOTe.
     */
    private final LnotSender lnotSender;
    /**
     * Permute+Share permutation owner (DOSN receiver).
     */
    private final DosnReceiver dosnReceiver;

    public Jszg24BecrgPsuServer(Rpc serverRpc, Party clientParty, Jszg24BecrgPsuConfig config) {
        super(Jszg24BecrgPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        this.config = config;
        bopprfReceiver = BopprfFactory.createReceiver(serverRpc, clientParty, config.getBopprfConfig());
        addSubPto(bopprfReceiver);
        peqtSender = PeqtFactory.createSender(serverRpc, clientParty, config.getPeqtConfig());
        addSubPto(peqtSender);
        lnotSender = LnotFactory.createSender(serverRpc, clientParty, config.getLnotConfig());
        addSubPto(lnotSender);
        dosnReceiver = DosnFactory.createReceiver(serverRpc, clientParty, config.getDosnConfig());
        addSubPto(dosnReceiver);
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);
        stopWatch.start();
        // JSZG24 (Fig.17): init sub-protocols.
        int bMax = (int) Math.ceil(config.getEpsilon() * maxServerElementSize);
        int maxPointNum = Math.max(1, config.getGamma() * maxClientElementSize);
        bopprfReceiver.init(bMax, maxPointNum);
        int l2Max = config.getLambda() + LongUtils.ceilLog2(bMax);
        peqtSender.init(l2Max, bMax);
        lnotSender.init(1, bMax);
        dosnReceiver.init();
        stopWatch.stop();
        long t = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, t);
        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);
        MathPreconditions.checkPositive("serverElementSize", serverElementSize);
        MathPreconditions.checkPositive("clientElementSize", clientElementSize);
        MpcAbortPreconditions.checkArgument(elementByteLength == CommonUtils.getByteLength(config.getItemBitLength()));

        // ---------------- Fig.17 Step 1: hashing (S sends hash keys; S builds X_C) ----------------
        stopWatch.start();
        int binNum = (int) Math.ceil(config.getEpsilon() * serverElementSize);
        int gamma = config.getGamma();
        List<byte[]> xList = serverElementSet.stream().map(ByteBuffer::array).collect(Collectors.toList());
        byte[][] hashKeys = null;
        CuckooHashBin<byte[]> cuckoo = null;
        for (int attempt = 1; attempt <= config.getMaxCuckooRetry(); attempt++) {
            byte[][] candidateKeys = BlockUtils.randomBlocks(gamma, secureRandom);
            try {
                cuckoo = createNoStashCuckooOrAbort(candidateKeys, xList, binNum);
                hashKeys = candidateKeys;
                break;
            } catch (MpcAbortException ignored) {
                // retry with fresh keys
            }
        }
        MpcAbortPreconditions.checkArgument(hashKeys != null && cuckoo != null);
        sendOtherPartyPayload(PtoStep.HASH_KEYS.ordinal(), Arrays.stream(hashKeys).collect(Collectors.toList()));
        Hash dummyHash = HashFactory.createInstance(envType, elementByteLength);
        byte[] d = dummyHash.digestToBytes(concat(hashKeys));
        cuckoo.insertPaddingItems(d);
        final CuckooHashBin<byte[]> finalCuckoo = cuckoo;
        byte[][] xC = IntStream.range(0, binNum)
            .mapToObj(i -> finalCuckoo.getHashBinEntry(i).getItemByteArray())
            .toArray(byte[][]::new);
        stopWatch.stop();
        long hashTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 6, hashTime, "Fig.17 Step 1: hashing");

        // ---------------- Fig.17 Step 2: S samples t'_i and sends to R ----------------
        stopWatch.start();
        int l2 = config.getLambda() + LongUtils.ceilLog2(binNum);
        int tByteLen = CommonUtils.getByteLength(l2);
        byte[][] tPrime = new byte[binNum][tByteLen];
        for (int i = 0; i < binNum; i++) {
            tPrime[i] = BytesUtils.randomByteArray(tByteLen, secureRandom);
            // OPPRF programmed targets must be reduced to ℓ2 bits (AbstractBopprfSender assertion).
            BytesUtils.reduceByteArray(tPrime[i], l2);
        }
        sendOtherPartyPayload(PtoStep.T_PRIME.ordinal(), Arrays.asList(tPrime));
        stopWatch.stop();
        long tPrimeTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 6, tPrimeTime, "Fig.17 Step 2: send t'_i");

        // ---------------- Fig.17 Step 3: OPPRF (S queries X_C[i]) ----------------
        stopWatch.start();
        List<byte[]> pointNumPayload = receiveOtherPartyPayload(PtoStep.OPPRF_POINT_NUM.ordinal());
        MpcAbortPreconditions.checkArgument(pointNumPayload.size() == 1);
        int pointNum = IntUtils.byteArrayToInt(pointNumPayload.get(0));
        MathPreconditions.checkPositive("pointNum", pointNum);
        byte[][] xCQueries = new byte[binNum][];
        for (int i = 0; i < binNum; i++) {
            xCQueries[i] = encodeBinPoint(i, xC[i]);
        }
        byte[][] t = bopprfReceiver.opprf(l2, xCQueries, pointNum);
        stopWatch.stop();
        long opprfTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 3, 6, opprfTime, "Fig.17 Step 3: OPPRF");

        // ---------------- Fig.17 Step 4: bECRG (PET + eqOTe) ----------------
        stopWatch.start();
        byte[][] r = new byte[binNum][elementByteLength];
        for (int i = 0; i < binNum; i++) {
            r[i] = BytesUtils.randomByteArray(elementByteLength, secureRandom);
        }
        // PET shares: PEQT outputs equality shares, convert to inequality shares:
        // neq = 1 ⊕ eq, so flip the receiver share later; sender keeps eqShare0 as neqShare0.
        SquareZ2Vector eqShare0 = peqtSender.peqt(l2, t);
        boolean[] neqShare0 = new boolean[binNum];
        for (int i = 0; i < binNum; i++) {
            neqShare0[i] = eqShare0.getBitVector().get(i);
        }
        // eqOTe from l=1 random LNOT + OTP masking.
        LnotSenderOutput lnotOut0 = lnotSender.send(binNum);
        Prg prg = PrgFactory.createInstance(envType, elementByteLength);
        List<byte[]> eqOtePayload = new ArrayList<>(binNum);
        for (int i = 0; i < binNum; i++) {
            // Ciphertext uses e = xC ⊕ r; client recovers z = e ⊕ r' = xC ⊕ r ⊕ r'.
            // Equality (neq=0) must yield z = d ⇒ r' = x0 = xC ⊕ r ⊕ d.
            // Inequality (neq=1) must yield z = xC ⇒ r' = r.
            // LNOT eqOT is arranged so the client, choosing with neqShare1, learns the payload for
            // neq = neqShare0 ⊕ neqShare1 (where neqShare0 stores the PEQT equality share).
            byte[] x0 = BytesUtils.xor(BytesUtils.xor(xC[i], r[i]), d);
            byte[] m0 = neqShare0[i] ? r[i] : x0;
            byte[] m1 = neqShare0[i] ? x0 : r[i];
            byte[] k0 = prg.extendToBytes(lnotOut0.getRs(i)[0]);
            byte[] k1 = prg.extendToBytes(lnotOut0.getRs(i)[1]);
            // Clone OT payloads so xC/r/d are never mutated by later XOR masking.
            byte[] c0 = BytesUtils.xor(BytesUtils.clone(m0), k0);
            byte[] c1 = BytesUtils.xor(BytesUtils.clone(m1), k1);
            eqOtePayload.add(concat(c0, c1));
        }
        sendOtherPartyEqualSizePayload(PtoStep.BECRG_EQOTE.ordinal(), eqOtePayload);
        stopWatch.stop();
        long becrgTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 4, 6, becrgTime, "Fig.17 Step 4: bECRG");

        // ---------------- Fig.17 Step 5: Permute+Share (FPS) ----------------
        stopWatch.start();
        int[] pi = PermutationNetworkUtils.randomPermutation(binNum, secureRandom);
        DosnPartyOutput s1Out = dosnReceiver.dosn(pi, elementByteLength);
        byte[][] s1 = s1Out.getShareVector();
        stopWatch.stop();
        long psTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 5, 6, psTime, "Fig.17 Step 5: Permute+Share");

        // ---------------- Fig.17 Step 6: send ciphertext candidates ----------------
        stopWatch.start();
        List<byte[]> cPayload = new ArrayList<>(binNum);
        for (int i = 0; i < binNum; i++) {
            int src = pi[i];
            byte[] e = BytesUtils.xor(xC[src], r[src]);
            cPayload.add(BytesUtils.xor(e, s1[i]));
        }
        sendOtherPartyEqualSizePayload(PtoStep.SEND_CIPHERTEXTS.ordinal(), cPayload);
        stopWatch.stop();
        long cTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 6, 6, cTime, "Fig.17 Step 6: ciphertexts");

        logPhaseInfo(PtoState.PTO_END);
    }

    private CuckooHashBin<byte[]> createNoStashCuckooOrAbort(byte[][] hashKeys, List<byte[]> items, int binNum)
        throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(CuckooHashBinFactory.isNoStashType(config.getCuckooHashBinType()));
        try {
            CuckooHashBin<byte[]> cuckoo = CuckooHashBinFactory.createCuckooHashBin(
                envType, config.getCuckooHashBinType(), serverElementSize, binNum, hashKeys
            );
            cuckoo.insertItems(items);
            MpcAbortPreconditions.checkArgument(cuckoo.itemNumInStash() == 0);
            return cuckoo;
        } catch (ArithmeticException e) {
            throw new MpcAbortException("JSZG24 Cuckoo hashing failed with the sampled keys");
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
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
     */
    private static byte[] encodeBinPoint(int binIndex, byte[] item) {
        byte[] prefix = IntUtils.intToByteArray(binIndex);
        byte[] out = new byte[prefix.length + item.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(item, 0, out, prefix.length, item.length);
        return out;
    }
}

