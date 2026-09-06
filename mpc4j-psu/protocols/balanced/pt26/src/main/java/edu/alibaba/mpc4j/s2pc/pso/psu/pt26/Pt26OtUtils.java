package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;

import java.util.ArrayList;
import java.util.List;

/**
 * 1-out-of-2 and 1-out-of-3 OT from core COT (§5.3, Fig. 4).
 */
public class Pt26OtUtils {
    private Pt26OtUtils() {
        // empty
    }

    public static List<byte[]> senderEncrypt(
        CotSenderOutput cotSenderOutput, List<byte[]> m0, List<byte[]> m1, int messageByteLength,
        edu.alibaba.mpc4j.common.tool.EnvType envType) throws edu.alibaba.mpc4j.common.rpc.MpcAbortException {
        int n = m0.size();
        MpcAbortPreconditions.checkArgument(m1.size() == n);
        Prg prg = PrgFactory.createInstance(envType, messageByteLength);
        ArrayList<byte[]> payload = new ArrayList<byte[]>(n);
        for (int index = 0; index < n; index++) {
            byte[] c0 = prg.extendToBytes(cotSenderOutput.getR0(index));
            BytesUtils.xori(c0, m0.get(index));
            byte[] c1 = prg.extendToBytes(cotSenderOutput.getR1(index));
            BytesUtils.xori(c1, m1.get(index));
            byte[] pair = new byte[messageByteLength * 2];
            System.arraycopy(c0, 0, pair, 0, messageByteLength);
            System.arraycopy(c1, 0, pair, messageByteLength, messageByteLength);
            payload.add(pair);
        }
        return payload;
    }

    public static List<byte[]> receiverDecrypt(
        CotReceiverOutput cotReceiverOutput, List<byte[]> payload, int messageByteLength,
        edu.alibaba.mpc4j.common.tool.EnvType envType) throws edu.alibaba.mpc4j.common.rpc.MpcAbortException {
        int n = payload.size();
        MpcAbortPreconditions.checkArgument(cotReceiverOutput.getNum() == n);
        Prg prg = PrgFactory.createInstance(envType, messageByteLength);
        ArrayList<byte[]> messages = new ArrayList<byte[]>(n);
        for (int index = 0; index < n; index++) {
            byte[] pair = payload.get(index);
            MpcAbortPreconditions.checkArgument(pair.length == messageByteLength * 2);
            byte[] c0 = BytesUtils.clone(pair, 0, messageByteLength);
            byte[] c1 = BytesUtils.clone(pair, messageByteLength, messageByteLength);
            if (cotReceiverOutput.getChoice(index)) {
                byte[] rb = prg.extendToBytes(cotReceiverOutput.getRb(index));
                BytesUtils.xori(c1, rb);
                messages.add(c1);
            } else {
                byte[] rb = prg.extendToBytes(cotReceiverOutput.getRb(index));
                BytesUtils.xori(c0, rb);
                messages.add(c0);
            }
        }
        return messages;
    }

    public static List<byte[]> runSenderOneOfTwo(
        CoreCotSender coreCotSender, List<byte[]> m0, List<byte[]> m1, int messageByteLength,
        edu.alibaba.mpc4j.common.tool.EnvType envType) throws edu.alibaba.mpc4j.common.rpc.MpcAbortException {
        CotSenderOutput cotSenderOutput = coreCotSender.send(m0.size());
        return senderEncrypt(cotSenderOutput, m0, m1, messageByteLength, envType);
    }

    public static List<byte[]> runReceiverOneOfTwo(
        CoreCotReceiver coreCotReceiver, boolean[] choices, List<byte[]> payload, int messageByteLength,
        edu.alibaba.mpc4j.common.tool.EnvType envType) throws edu.alibaba.mpc4j.common.rpc.MpcAbortException {
        CotReceiverOutput cotReceiverOutput = coreCotReceiver.receive(choices);
        return receiverDecrypt(cotReceiverOutput, payload, messageByteLength, envType);
    }

    /**
     * 1-out-of-3 via three parallel 1-out-of-2 OTs.
     */
    public static List<byte[]> runSenderOneOfThree(
        CoreCotSender coreCotSender, List<byte[]> w0, List<byte[]> w1, List<byte[]> w2, int messageByteLength,
        edu.alibaba.mpc4j.common.tool.EnvType envType) throws edu.alibaba.mpc4j.common.rpc.MpcAbortException {
        int n = w0.size();
        List<byte[]> payload0 = runSenderOneOfTwo(coreCotSender, w0, randomMessages(n, messageByteLength), messageByteLength, envType);
        List<byte[]> payload1 = runSenderOneOfTwo(coreCotSender, w1, randomMessages(n, messageByteLength), messageByteLength, envType);
        List<byte[]> payload2 = runSenderOneOfTwo(coreCotSender, w2, randomMessages(n, messageByteLength), messageByteLength, envType);
        ArrayList<byte[]> merged = new ArrayList<byte[]>(n);
        for (int i = 0; i < n; i++) {
            byte[] p0 = payload0.get(i);
            byte[] p1 = payload1.get(i);
            byte[] p2 = payload2.get(i);
            byte[] mergedPair = new byte[p0.length + p1.length + p2.length];
            System.arraycopy(p0, 0, mergedPair, 0, p0.length);
            System.arraycopy(p1, 0, mergedPair, p0.length, p1.length);
            System.arraycopy(p2, 0, mergedPair, p0.length + p1.length, p2.length);
            merged.add(mergedPair);
        }
        return merged;
    }

    public static List<byte[]> runReceiverOneOfThree(
        CoreCotReceiver coreCotReceiver, int[] choices, List<byte[]> mergedPayload, int messageByteLength,
        edu.alibaba.mpc4j.common.tool.EnvType envType) throws edu.alibaba.mpc4j.common.rpc.MpcAbortException {
        int n = choices.length;
        int pairLen = messageByteLength * 2;
        List<byte[]> payload0 = new ArrayList<byte[]>(n);
        List<byte[]> payload1 = new ArrayList<byte[]>(n);
        List<byte[]> payload2 = new ArrayList<byte[]>(n);
        for (int i = 0; i < n; i++) {
            byte[] merged = mergedPayload.get(i);
            MpcAbortPreconditions.checkArgument(merged.length == pairLen * 3);
            payload0.add(BytesUtils.clone(merged, 0, pairLen));
            payload1.add(BytesUtils.clone(merged, pairLen, pairLen));
            payload2.add(BytesUtils.clone(merged, pairLen * 2, pairLen));
        }
        // payload_j carries (slot0 = w_j, slot1 = random); receiver chooses slot 0 of the j-th
        // OT (choice = false) to learn w_j. Bug-fix: the original implementation used
        // choices[i] == j (TRUE when we want slot 0), which inverted the OT direction and
        // returned the random decoy instead of w_j. Hidden until correctness was unit-tested.
        boolean[] c0 = new boolean[n];
        boolean[] c1 = new boolean[n];
        boolean[] c2 = new boolean[n];
        for (int i = 0; i < n; i++) {
            c0[i] = choices[i] != 0;
            c1[i] = choices[i] != 1;
            c2[i] = choices[i] != 2;
        }
        List<byte[]> m0 = runReceiverOneOfTwo(coreCotReceiver, c0, payload0, messageByteLength, envType);
        List<byte[]> m1 = runReceiverOneOfTwo(coreCotReceiver, c1, payload1, messageByteLength, envType);
        List<byte[]> m2 = runReceiverOneOfTwo(coreCotReceiver, c2, payload2, messageByteLength, envType);
        ArrayList<byte[]> out = new ArrayList<byte[]>(n);
        for (int i = 0; i < n; i++) {
            if (choices[i] == 0) {
                out.add(m0.get(i));
            } else if (choices[i] == 1) {
                out.add(m1.get(i));
            } else {
                out.add(m2.get(i));
            }
        }
        return out;
    }

    private static List<byte[]> randomMessages(int n, int messageByteLength) {
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        ArrayList<byte[]> list = new ArrayList<byte[]>(n);
        for (int i = 0; i < n; i++) {
            byte[] block = new byte[messageByteLength];
            secureRandom.nextBytes(block);
            list.add(block);
        }
        return list;
    }
}
