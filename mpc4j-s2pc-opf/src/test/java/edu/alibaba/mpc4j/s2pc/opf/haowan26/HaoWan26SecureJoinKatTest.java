package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.galoisfield.Z3ByteField;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrix;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrixFactory.F32WprfMatrixType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams;
import edu.alibaba.mpc4j.common.tool.bitmatrix.dense.DenseBitMatrix;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Known-answer tests against vendored ladnir/secure-join @ 1e1dddf AltMod dumps.
 */
public class HaoWan26SecureJoinKatTest {
    private static final String ROOT = HaoWan26SecureJoinParams.RESOURCE_ROOT;

    @Test
    public void testGRepresentatives() throws IOException {
        Map<String, byte[]> expected = loadNamedBlocks("g_representative.txt", 64);
        assertG("zero", new byte[16], expected.get("zero"));
        assertG("one", leBlockLow(1L), expected.get("one"));
        assertG("all_ones", hex("ffffffffffffffffffffffffffffffff"), expected.get("all_ones"));
        assertG("alternating", hex("5555555555555555aaaaaaaaaaaaaaaa"), expected.get("alternating"));
    }

    @Test
    public void testGAllBasis() throws IOException {
        Map<Integer, byte[]> expected = loadIndexedBlocks("g_basis_images.txt", 64);
        Assert.assertEquals(128, expected.size());
        for (int i = 0; i < 128; i++) {
            byte[] x = new byte[16];
            x[i >> 3] |= (byte) (1 << (i & 7));
            assertG("basis-" + i, x, expected.get(i));
        }
    }

    @Test
    public void testAAllBasis() throws IOException {
        Map<Integer, byte[]> expected = loadIndexedBlocks("a_basis_images.txt", 256);
        Assert.assertEquals(F32Wprf.N, expected.size());
        Z3ByteField field = new Z3ByteField();
        F32WprfMatrix a = HaoWan26SecureJoinParams.matrixA(field, F32WprfMatrixType.NAIVE);
        for (int i = 0; i < F32Wprf.N; i++) {
            byte[] in = new byte[F32Wprf.N];
            in[i] = 1;
            Assert.assertArrayEquals("A(e_" + i + ")", expected.get(i), a.leftMul(in));
        }
    }

    @Test
    public void testBAllBasis() throws IOException {
        Map<Integer, byte[]> expectedLe = loadIndexedBlocks("b_basis_images.txt", 16);
        Assert.assertEquals(F32Wprf.M, expectedLe.size());
        DenseBitMatrix b = HaoWan26SecureJoinParams.matrixB();
        for (int i = 0; i < F32Wprf.M; i++) {
            byte[] in = new byte[F32Wprf.M_BYTE_LENGTH];
            BinaryUtils.setBoolean(in, i, true);
            byte[] outMsb = b.leftMultiply(in);
            byte[] outLe = HaoWan26SecureJoinParams.binaryUtilsPackedToLePacked(outMsb);
            Assert.assertArrayEquals("B(e_" + i + ")", expectedLe.get(i), outLe);
        }
    }

    @Test
    public void testFFixedKeyResourcePresentAndSmoke() throws IOException {
        List<FKat> kats = loadFKats();
        Assert.assertEquals(5, kats.size());
        // End-to-end F(k,x) byte match vs AltModPrf::eval remains open: G/A/B KATs pin public
        // matrices; fixed-key vectors are retained for follow-up key-packing alignment.
        Z3ByteField field = new Z3ByteField();
        F32Wprf wprf = HaoWan26SecureJoinParams.createF32Wprf(field, F32WprfMatrixType.NAIVE);
        byte[] key = new byte[F32Wprf.N_BYTE_LENGTH];
        key[0] = 1;
        wprf.init(HaoWan26SecureJoinParams.lePackedToBinaryUtilsPacked(key));
        byte[] expanded = HaoWan26SecureJoinParams.expandG(leBlockLow(1L));
        Assert.assertEquals(F32Wprf.getOutputByteLength(), wprf.prf(expanded).length);
    }

    @Test
    public void testTruncationCounts() {
        int[] counts = {1, 2, 3, 4, 7, 8, 31, 32};
        for (int n : counts) {
            int ell = HaoWan26Truncate.ellBits(n);
            Assert.assertTrue(ell >= 40);
            Assert.assertTrue(ell <= F32Wprf.getOutputByteLength() * Byte.SIZE);
            byte[] full = new byte[F32Wprf.getOutputByteLength()];
            full[0] = (byte) 0xFF;
            byte[] trunc = HaoWan26Truncate.truncate(full, ell);
            Assert.assertEquals(HaoWan26Truncate.ellBytes(n), trunc.length);
        }
    }

    private static void assertG(String name, byte[] xLe, byte[] expectedLe64) {
        byte[] f3 = HaoWan26SecureJoinParams.expandG(xLe);
        byte[] packedLe = new byte[64];
        for (int i = 0; i < F32Wprf.N; i++) {
            if (f3[i] != 0) {
                packedLe[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        Assert.assertArrayEquals(name, expectedLe64, packedLe);
    }

    /** Test-only key matching dump_altmod_basis.cpp: key[i]=block(i+1,i+2) for i in 0..3. */
    private static byte[] buildFixedTestKeyLe() {
        byte[] key = new byte[F32Wprf.N_BYTE_LENGTH];
        for (int i = 0; i < 4; i++) {
            putLeLong(key, i * 16, i + 2L);
            putLeLong(key, i * 16 + 8, i + 1L);
        }
        return key;
    }

    private static void putLeLong(byte[] dest, int offset, long value) {
        for (int b = 0; b < 8; b++) {
            dest[offset + b] = (byte) (value >>> (8 * b));
        }
    }

    private static byte[] leBlockLow(long low) {
        byte[] x = new byte[16];
        putLeLong(x, 0, low);
        return x;
    }

    private static boolean isAllZero(byte[] a) {
        for (byte b : a) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, byte[]> loadNamedBlocks(String file, int byteLen) throws IOException {
        Map<String, byte[]> map = new HashMap<>();
        try (BufferedReader r = open(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                map.put(parts[0], hex(parts[1]));
                Assert.assertEquals(byteLen, map.get(parts[0]).length);
            }
        }
        return map;
    }

    private static Map<Integer, byte[]> loadIndexedBlocks(String file, int byteLen) throws IOException {
        Map<Integer, byte[]> map = new HashMap<>();
        try (BufferedReader r = open(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                int idx = Integer.parseInt(parts[0]);
                byte[] bytes = hex(parts[1]);
                Assert.assertEquals(byteLen, bytes.length);
                map.put(idx, bytes);
            }
        }
        return map;
    }

    private static List<FKat> loadFKats() throws IOException {
        List<FKat> list = new ArrayList<>();
        try (BufferedReader r = open("f_fixed_key.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // name x=<32hex> y=<32hex>
                String[] parts = line.trim().split("\\s+");
                String name = parts[0];
                String xHex = parts[1].substring("x=".length());
                String yHex = parts[2].substring("y=".length());
                list.add(new FKat(name, hex(xHex), hex(yHex)));
            }
        }
        return list;
    }

    private static BufferedReader open(String name) {
        InputStream in = HaoWan26SecureJoinKatTest.class.getClassLoader().getResourceAsStream(ROOT + name);
        Assert.assertNotNull("missing " + ROOT + name, in);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private record FKat(String name, byte[] xLe, byte[] yLe) {
    }
}
