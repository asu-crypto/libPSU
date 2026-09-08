package edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin;

import edu.alibaba.mpc4j.common.tool.galoisfield.Z3ByteField;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrix;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrixFactory.F32WprfMatrixType;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Cross-language KATs vs ladnir/secure-join @ 1e1dddf resource vectors.
 */
public class HaoWan26SecureJoinKatTest {
    private static final Z3ByteField FIELD = new Z3ByteField();

    @Test
    public void gRepresentativeAndBasis() throws Exception {
        assertGLine("zero", hex("00000000000000000000000000000000"));
        // one = block(0,1) → LE bytes 01 00 ...
        assertGLine("one", hex("01000000000000000000000000000000"));
        assertGLine("all_ones", hex("ffffffffffffffffffffffffffffffff"));
        assertGLine("alternating", hex("5555555555555555aaaaaaaaaaaaaaaa"));

        try (BufferedReader r = open("g_basis_images.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.trim().split("\\s+");
                int idx = Integer.parseInt(p[0]);
                byte[] x = new byte[16];
                x[idx >> 3] |= (byte) (1 << (idx & 7));
                byte[] got = HaoWan26SecureJoinParams.expandG(x);
                byte[] expectPacked = hex(p[1]);
                byte[] expectF3 = new byte[F32Wprf.N];
                for (int i = 0; i < F32Wprf.N; i++) {
                    expectF3[i] = HaoWan26SecureJoinParams.getLeBit(expectPacked, i) ? (byte) 1 : (byte) 0;
                }
                Assert.assertArrayEquals("G basis " + idx, expectF3, got);
            }
        }
    }

    @Test
    public void aBasisNaiveAndLong() throws Exception {
        for (F32WprfMatrixType type : new F32WprfMatrixType[]{F32WprfMatrixType.NAIVE, F32WprfMatrixType.LONG}) {
            F32WprfMatrix a = HaoWan26SecureJoinParams.matrixA(FIELD, type);
            try (BufferedReader r = open("a_basis_images.txt")) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.trim().split("\\s+");
                    int idx = Integer.parseInt(p[0]);
                    byte[] in = new byte[F32Wprf.N];
                    in[idx] = 1;
                    byte[] got = a.leftMul(in);
                    byte[] expect = hex(p[1]);
                    Assert.assertArrayEquals("A(" + type + ") basis " + idx, expect, got);
                }
            }
        }
    }

    @Test
    public void bBasis() throws Exception {
        var b = HaoWan26SecureJoinParams.matrixB();
        try (BufferedReader r = open("b_basis_images.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.trim().split("\\s+");
                int idx = Integer.parseInt(p[0]);
                byte[] v = new byte[F32Wprf.M_BYTE_LENGTH];
                BinaryUtils.setBoolean(v, idx, true);
                byte[] gotBe = b.leftMultiply(v);
                byte[] expectLe = hex(p[1]);
                Assert.assertArrayEquals(
                    "B basis " + idx,
                    expectLe,
                    HaoWan26SecureJoinParams.binaryUtilsPackedToLePacked(gotBe)
                );
            }
        }
    }

    @Test
    public void fFixedKeyAllMatrixTypes() throws Exception {
        byte[] keyLe = HaoWan26SecureJoinParams.fixedTestKeyLe();
        for (F32WprfMatrixType type : F32WprfMatrixType.values()) {
            try (BufferedReader r = open("f_fixed_key.txt")) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.contains(" x=") || !line.contains(" y=")) {
                        continue;
                    }
                    int xAt = line.indexOf(" x=");
                    int yAt = line.indexOf(" y=");
                    String name = line.substring(0, xAt).trim();
                    byte[] xLe = hex(line.substring(xAt + 3, yAt).trim());
                    byte[] yLe = hex(line.substring(yAt + 3).trim());
                    byte[] got = HaoWan26SecureJoinParams.evaluateFLe(keyLe, xLe, type);
                    Assert.assertArrayEquals(name + "/" + type, yLe, got);
                }
            }
        }
    }

    @Test
    public void fZeroInputAccepted() {
        F32Wprf wprf = HaoWan26SecureJoinParams.createF32Wprf(FIELD, F32WprfMatrixType.NAIVE);
        wprf.init(HaoWan26SecureJoinParams.lePackedToBinaryUtilsPacked(HaoWan26SecureJoinParams.fixedTestKeyLe()));
        byte[] zeroCodeword = HaoWan26SecureJoinParams.expandG(new byte[16]);
        Assert.assertArrayEquals(new byte[F32Wprf.N], zeroCodeword);
        Assert.assertArrayEquals(new byte[F32Wprf.getOutputByteLength()], wprf.prf(zeroCodeword));
    }

    @Test
    public void fBackendDifferentialRandom() {
        byte[] keyMsb = new byte[F32Wprf.N_BYTE_LENGTH];
        new java.security.SecureRandom().nextBytes(keyMsb);
        F32Wprf naive = HaoWan26SecureJoinParams.createF32Wprf(FIELD, F32WprfMatrixType.NAIVE);
        F32Wprf packedByte = HaoWan26SecureJoinParams.createF32Wprf(FIELD, F32WprfMatrixType.BYTE);
        F32Wprf packedLong = HaoWan26SecureJoinParams.createF32Wprf(FIELD, F32WprfMatrixType.LONG);
        naive.init(keyMsb);
        packedByte.init(keyMsb);
        packedLong.init(keyMsb);
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int t = 0; t < 32; t++) {
            byte[] x = new byte[16];
            random.nextBytes(x);
            byte[] e = HaoWan26SecureJoinParams.expandG(x);
            byte[] yn = naive.prf(e);
            Assert.assertArrayEquals(yn, packedByte.prf(e));
            Assert.assertArrayEquals(yn, packedLong.prf(e));
        }
    }

    private static void assertGLine(String name, byte[] xLe) throws Exception {
        byte[] got = HaoWan26SecureJoinParams.expandG(xLe);
        try (BufferedReader r = open("g_representative.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith(name + " ")) {
                    continue;
                }
                byte[] expectPacked = hex(line.substring(name.length() + 1).trim());
                byte[] expectF3 = new byte[F32Wprf.N];
                for (int i = 0; i < F32Wprf.N; i++) {
                    expectF3[i] = HaoWan26SecureJoinParams.getLeBit(expectPacked, i) ? (byte) 1 : (byte) 0;
                }
                Assert.assertArrayEquals(name, expectF3, got);
                return;
            }
        }
        Assert.fail("missing G rep " + name);
    }

    private static BufferedReader open(String name) {
        InputStream in = HaoWan26SecureJoinKatTest.class.getClassLoader()
            .getResourceAsStream(HaoWan26SecureJoinParams.RESOURCE_ROOT + name);
        Assert.assertNotNull(name, in);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static void writeU64Le(byte[] dest, int off, long v) {
        for (int i = 0; i < 8; i++) {
            dest[off + i] = (byte) (v >>> (8 * i));
        }
    }
}
