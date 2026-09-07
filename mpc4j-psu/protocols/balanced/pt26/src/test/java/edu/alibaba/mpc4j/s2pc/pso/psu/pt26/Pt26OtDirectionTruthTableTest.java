package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Truth-table for Figure 4 Step 1 using real {@link Pt26OtUtils} encrypt/decrypt direction
 * (not a plaintext reimplementation of uPeel).
 */
public class Pt26OtDirectionTruthTableTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final EnvType ENV = EnvType.STANDARD;
    private static final int ELEMENT_LEN = 8;

    @Test
    public void step1OtSelectsM1WhenCnt0IsZero() throws Exception {
        Pt26IbltParams params = params();
        byte[] sum1 = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0x11)), params);
        // Paper: m0 = BOT, m1 = sum1 when cnt1 == 1. Server choice c = [cnt0 == 0] = true.
        byte[] m0 = Pt26Zm.encodeWireBot(params);
        byte[] m1 = Pt26Zm.encodeWireValue(sum1, params);
        byte[] got = encryptDecrypt(true, m0, m1, Pt26Zm.wireByteLength(params));
        Optional<byte[]> decoded = Pt26Zm.decodeWire(got, params);
        Assert.assertTrue(decoded.isPresent());
        Assert.assertArrayEquals(sum1, decoded.get());
    }

    @Test
    public void step1OtSelectsM0BotWhenCnt0Nonzero() throws Exception {
        Pt26IbltParams params = params();
        byte[] sum1 = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0x22)), params);
        byte[] m0 = Pt26Zm.encodeWireBot(params);
        byte[] m1 = Pt26Zm.encodeWireValue(sum1, params);
        // Reversed-bug assignment put sum1 in m0; with correct choice=false (cnt0!=0) that would
        // incorrectly reveal sum1. Correct messages keep m0=BOT so choice=false yields BOT.
        byte[] got = encryptDecrypt(false, m0, m1, Pt26Zm.wireByteLength(params));
        Assert.assertTrue(Pt26Zm.decodeWire(got, params).isEmpty());
    }

    @Test
    public void reversedMessagesWouldLeakOrMiss() throws Exception {
        Pt26IbltParams params = params();
        byte[] sum1 = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0x33)), params);
        // Old buggy assignment: m0 = sum1 when c1==1, m1 = BOT.
        byte[] buggyM0 = Pt26Zm.encodeWireValue(sum1, params);
        byte[] buggyM1 = Pt26Zm.encodeWireBot(params);
        int wireLen = Pt26Zm.wireByteLength(params);
        // cnt0==0 ⇒ choice true ⇒ decrypts buggyM1 = BOT (misses sum1 — cascade failure).
        Assert.assertTrue(Pt26Zm.decodeWire(encryptDecrypt(true, buggyM0, buggyM1, wireLen), params).isEmpty());
        // cnt0!=0 ⇒ choice false ⇒ decrypts buggyM0 = sum1 (should have been BOT).
        Optional<byte[]> leak = Pt26Zm.decodeWire(encryptDecrypt(false, buggyM0, buggyM1, wireLen), params);
        Assert.assertTrue(leak.isPresent());
        Assert.assertArrayEquals(sum1, leak.get());
    }

    @Test
    public void plaintextUPeelTruthTable() {
        Pt26IbltParams params = params();
        byte[] a = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0xA1)), params);
        byte[] b = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0xB2)), params);
        Assert.assertNull(peel(0, 0, a, b));
        Assert.assertArrayEquals(b, peel(0, 1, a, b));
        Assert.assertArrayEquals(a, peel(1, 0, a, b));
        Assert.assertArrayEquals(a, peel(1, 1, a, a));
        Assert.assertNull(peel(1, 1, a, b));
        Assert.assertNull(peel(2, 1, a, b));
        Assert.assertNull(peel(1, 2, a, b));
        Assert.assertNull(peel(3, 0, a, b));
    }

    private static byte[] peel(int c0, int c1, byte[] s0, byte[] s1) {
        Pt26IbltParams params = params();
        Pt26Iblt iblt0 = new Pt26Iblt(params);
        Pt26Iblt iblt1 = new Pt26Iblt(params);
        // Directly set via insert/delete is hard for arbitrary counts; use reflection-free
        // approach: build from elements when counts are 0/1, else mutate through package fields.
        setBin(iblt0, 0, 0, c0, s0);
        setBin(iblt1, 0, 0, c1, s1);
        return Pt26Iblt.uPeel(iblt0, iblt1, 0, 0);
    }

    private static void setBin(Pt26Iblt iblt, int i, int j, int cnt, byte[] sum) {
        try {
            java.lang.reflect.Field cntField = Pt26Iblt.class.getDeclaredField("cnt");
            java.lang.reflect.Field sumField = Pt26Iblt.class.getDeclaredField("sum");
            cntField.setAccessible(true);
            sumField.setAccessible(true);
            int[][] cntArr = (int[][]) cntField.get(iblt);
            byte[][][] sumArr = (byte[][][]) sumField.get(iblt);
            cntArr[i][j] = cnt;
            sumArr[i][j] = sum.clone();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] encryptDecrypt(boolean choice, byte[] m0, byte[] m1, int messageLen)
        throws Exception {
        byte[] delta = BlockUtils.randomBlock(RANDOM);
        CotSenderOutput senderOut = CotSenderOutput.createRandom(1, delta, RANDOM);
        CotReceiverOutput receiverOut = CotReceiverOutput.createRandom(senderOut, RANDOM);
        // Force the desired choice bit by rebuilding receiver output if needed.
        if (receiverOut.getChoice(0) != choice) {
            boolean[] choices = new boolean[]{choice};
            byte[][] rb = new byte[][]{choice ? senderOut.getR1(0) : senderOut.getR0(0)};
            receiverOut = CotReceiverOutput.create(choices, rb);
        }
        List<byte[]> payload = Pt26OtUtils.senderEncrypt(
            senderOut, Collections.singletonList(m0), Collections.singletonList(m1), messageLen, ENV
        );
        return Pt26OtUtils.receiverDecrypt(receiverOut, payload, messageLen, ENV).get(0);
    }

    private static Pt26IbltParams params() {
        byte[][] keys = new byte[Pt26IbltParams.DEFAULT_K][];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = BlockUtils.randomBlock(RANDOM);
        }
        return Pt26IbltParams.createDefault(8, 8, ELEMENT_LEN, keys);
    }

    private static byte[] bytes(int marker) {
        byte[] out = new byte[ELEMENT_LEN];
        out[ELEMENT_LEN - 1] = (byte) marker;
        return out;
    }

    @Test
    public void modulusIsPowerOfTwo() {
        Pt26IbltParams params = params();
        Assert.assertEquals(BigInteger.ONE.shiftLeft(params.getZmByteLength() * 8), params.modulus());
    }
}
