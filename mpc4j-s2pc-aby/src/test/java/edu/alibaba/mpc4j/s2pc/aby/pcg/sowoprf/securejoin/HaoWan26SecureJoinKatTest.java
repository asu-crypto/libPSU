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
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Cross-language KATs vs vectors dumped from the original {@code ePSU_fast/ePSU} link path
 * ({@code haowan26/secure-join-4a23526-epsu-fast/}).
 */
public class HaoWan26SecureJoinKatTest {
    private static final Z3ByteField FIELD = new Z3ByteField();
    private static final String[] MANIFEST_DIGESTS = {
        "f73d0d2afb43d545e005f424da480352a756e048ab1e36ba9ff399139d7e63fb  a_basis_images.txt",
        "0b653445cc53a6bc25361c560aed3f72986b781fdc3bc4661dd514eb19ff4253  b_basis_images.txt",
        "fb49b2e5670ce9a9407c84ac4efb74db0e8b39ae1bf4c4ae20ed1c5a48281caf  b_parity_rows.txt",
        "cba98ecd3474b8fa4e2bbebe9544ae2d838ee0867d55e6d40b2424bc7781f806  f_fixed_key.txt",
        "b2353c85012a7828b24f193ccc4e03e1ce13607b514d453c5773753fc3d0b327  g_basis_images.txt",
        "3ab586b7d6b10c94da33a5dd1eaac0a4a678d03097457d9f2fe6316142b28622  g_representative.txt",
    };

    @Test
    public void resourceDigestsMatchManifest() throws Exception {
        for (String entry : MANIFEST_DIGESTS) {
            String[] parts = entry.split("\\s+");
            Assert.assertEquals(2, parts.length);
            Assert.assertEquals(parts[0], sha256Resource(parts[1]));
        }
        try (BufferedReader r = open("A_SEED_CLASS.txt")) {
            String line = r.readLine();
            Assert.assertEquals("A_SEED_CLASS=CC", line.trim());
        }
    }

    @Test
    public void gRepresentativeAndBasis() throws Exception {
        assertGLine("zero", hex("00000000000000000000000000000000"));
        assertGLine("one", hex("01000000000000000000000000000000"));
        assertGLine("all_ones", hex("ffffffffffffffffffffffffffffffff"));
        assertGLine("alternating", hex("5555555555555555aaaaaaaaaaaaaaaa"));

        BitSet seen = new BitSet(128);
        int count = 0;
        try (BufferedReader r = open("g_basis_images.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.trim().split("\\s+");
                int idx = Integer.parseInt(p[0]);
                Assert.assertTrue("G index range", idx >= 0 && idx < 128);
                Assert.assertFalse("duplicate G index " + idx, seen.get(idx));
                seen.set(idx);
                count++;
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
        Assert.assertEquals(128, count);
        Assert.assertEquals(128, seen.cardinality());
    }

    @Test
    public void aBasisAllMatrixTypes() throws Exception {
        for (F32WprfMatrixType type : F32WprfMatrixType.values()) {
            F32WprfMatrix a = HaoWan26SecureJoinParams.matrixA(FIELD, type);
            BitSet seen = new BitSet(F32Wprf.N);
            int count = 0;
            try (BufferedReader r = open("a_basis_images.txt")) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.trim().split("\\s+");
                    int idx = Integer.parseInt(p[0]);
                    Assert.assertTrue(idx >= 0 && idx < F32Wprf.N);
                    Assert.assertFalse("duplicate A index " + idx, seen.get(idx));
                    seen.set(idx);
                    count++;
                    byte[] in = new byte[F32Wprf.N];
                    in[idx] = 1;
                    Assert.assertArrayEquals("A(" + type + ") basis " + idx, hex(p[1]), a.leftMul(in));
                }
            }
            Assert.assertEquals("A count/" + type, F32Wprf.N, count);
            Assert.assertEquals(F32Wprf.N, seen.cardinality());
        }
    }

    @Test
    public void bBasis() throws Exception {
        var b = HaoWan26SecureJoinParams.matrixB();
        BitSet seen = new BitSet(F32Wprf.M);
        int count = 0;
        try (BufferedReader r = open("b_basis_images.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.trim().split("\\s+");
                int idx = Integer.parseInt(p[0]);
                Assert.assertTrue(idx >= 0 && idx < F32Wprf.M);
                Assert.assertFalse(seen.get(idx));
                seen.set(idx);
                count++;
                byte[] v = new byte[F32Wprf.M_BYTE_LENGTH];
                BinaryUtils.setBoolean(v, idx, true);
                Assert.assertArrayEquals(
                    "B basis " + idx,
                    hex(p[1]),
                    HaoWan26SecureJoinParams.binaryUtilsPackedToLePacked(b.leftMultiply(v))
                );
            }
        }
        Assert.assertEquals(F32Wprf.M, count);
        Assert.assertEquals(F32Wprf.M, seen.cardinality());
    }

    @Test
    public void fFixedKeyAllMatrixTypes() throws Exception {
        byte[] keyLe = HaoWan26SecureJoinParams.fixedTestKeyLe();
        Set<String> names = new HashSet<>();
        for (F32WprfMatrixType type : F32WprfMatrixType.values()) {
            names.clear();
            try (BufferedReader r = open("f_fixed_key.txt")) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.contains(" x=") || !line.contains(" y=")) {
                        continue;
                    }
                    int xAt = line.indexOf(" x=");
                    int yAt = line.indexOf(" y=");
                    String name = line.substring(0, xAt).trim();
                    names.add(name);
                    byte[] xLe = hex(line.substring(xAt + 3, yAt).trim());
                    byte[] yLe = hex(line.substring(yAt + 3).trim());
                    byte[] got = HaoWan26SecureJoinParams.evaluateFLe(keyLe, xLe, type);
                    Assert.assertArrayEquals(name + "/" + type, yLe, got);
                }
            }
            Assert.assertEquals("F vector count/" + type, 5, names.size());
            Assert.assertTrue(names.contains("zero"));
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
        boolean found = false;
        int reps = 0;
        try (BufferedReader r = open("g_representative.txt")) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                reps++;
                if (!line.startsWith(name + " ")) {
                    continue;
                }
                found = true;
                byte[] expectPacked = hex(line.substring(name.length() + 1).trim());
                byte[] expectF3 = new byte[F32Wprf.N];
                for (int i = 0; i < F32Wprf.N; i++) {
                    expectF3[i] = HaoWan26SecureJoinParams.getLeBit(expectPacked, i) ? (byte) 1 : (byte) 0;
                }
                Assert.assertArrayEquals(name, expectF3, got);
            }
        }
        Assert.assertEquals(4, reps);
        Assert.assertTrue("missing G rep " + name, found);
    }

    private static BufferedReader open(String name) {
        InputStream in = HaoWan26SecureJoinKatTest.class.getClassLoader()
            .getResourceAsStream(HaoWan26SecureJoinParams.RESOURCE_ROOT + name);
        Assert.assertNotNull(name, in);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private static String sha256Resource(String name) throws Exception {
        InputStream in = HaoWan26SecureJoinKatTest.class.getClassLoader()
            .getResourceAsStream(HaoWan26SecureJoinParams.RESOURCE_ROOT + name);
        Assert.assertNotNull(name, in);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (n > 0) {
                md.update(buf, 0, n);
            }
        }
        in.close();
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (byte b : dig) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
