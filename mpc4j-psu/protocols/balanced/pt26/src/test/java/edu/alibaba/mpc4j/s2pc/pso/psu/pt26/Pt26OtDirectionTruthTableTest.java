package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26UnionPeel.Step1Messages;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Truth-table for Figure 4 Step 1 bound to {@link Pt26UnionPeel#buildStep1Messages}
 * and the real {@link Pt26OtUtils} encrypt/decrypt path.
 */
public class Pt26OtDirectionTruthTableTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final EnvType ENV = EnvType.STANDARD;
    private static final int ELEMENT_LEN = 8;

    @Test
    public void step1MessagesThroughOtForAllCntCombinations() throws Exception {
        Pt26IbltParams params = params();
        byte[] sum1 = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0x11)), params);
        int wireLen = Pt26Zm.wireByteLength(params);
        for (int cnt0 : new int[]{0, 1}) {
            for (int cnt1 : new int[]{0, 1, 2}) {
                boolean choice = (cnt0 == 0);
                Step1Messages step1 = Pt26UnionPeel.buildStep1Messages(cnt1, sum1, params);
                Assert.assertTrue(Pt26Zm.isWireBot(step1.m0(), params));
                if (cnt1 == 1) {
                    Optional<byte[]> m1 = Pt26Zm.decodeWire(step1.m1(), params);
                    Assert.assertTrue(m1.isPresent());
                    Assert.assertArrayEquals(sum1, m1.get());
                } else {
                    Assert.assertTrue(Pt26Zm.isWireBot(step1.m1(), params));
                }
                byte[] got = encryptDecrypt(choice, step1.m0(), step1.m1(), wireLen);
                Optional<byte[]> decoded = Pt26Zm.decodeWire(got, params);
                boolean expectSum1 = cnt0 == 0 && cnt1 == 1;
                if (expectSum1) {
                    Assert.assertTrue("cnt0=" + cnt0 + " cnt1=" + cnt1, decoded.isPresent());
                    Assert.assertArrayEquals(sum1, decoded.get());
                } else {
                    Assert.assertTrue("cnt0=" + cnt0 + " cnt1=" + cnt1, decoded.isEmpty());
                }
            }
        }
    }

    @Test
    public void reversedMessagesWouldLeakOrMiss() throws Exception {
        Pt26IbltParams params = params();
        byte[] sum1 = Pt26Zm.encodeElement(ByteBuffer.wrap(bytes(0x33)), params);
        // Old buggy assignment: m0 = sum1 when c1==1, m1 = BOT.
        byte[] buggyM0 = Pt26Zm.encodeWireValue(sum1, params);
        byte[] buggyM1 = Pt26Zm.encodeWireBot(params);
        int wireLen = Pt26Zm.wireByteLength(params);
        Assert.assertTrue(Pt26Zm.decodeWire(encryptDecrypt(true, buggyM0, buggyM1, wireLen), params).isEmpty());
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

    @Test
    public void modulusIsPowerOfTwo() {
        Pt26IbltParams params = params();
        Assert.assertEquals(BigInteger.ONE.shiftLeft(params.getZmByteLength() * 8), params.modulus());
    }

    private static byte[] peel(int c0, int c1, byte[] s0, byte[] s1) {
        Pt26Iblt iblt0 = new Pt26Iblt(params());
        Pt26Iblt iblt1 = new Pt26Iblt(params());
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
}
