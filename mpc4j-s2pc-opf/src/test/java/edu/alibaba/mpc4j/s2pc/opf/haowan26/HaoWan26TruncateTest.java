package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Truncation content KATs for secure-join prefix copy vs native createReduceByteArray.
 */
public class HaoWan26TruncateTest {
    private static final int[] SECURE_JOIN_ELL = {
        8, 9, 15, 16, 40, 41, 47, 48, 63, 64, 127, 128
    };

    @Test
    public void secureJoinTruncatesFirstCeilEllOver8BytesOfLePrf() throws IOException {
        byte[] lePrf = loadOneFixedKeyY();
        Assert.assertEquals(F32Wprf.getOutputByteLength(), lePrf.length);
        for (int ell : SECURE_JOIN_ELL) {
            byte[] got = HaoWan26Truncate.truncate(lePrf, ell, F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN);
            int byteLen = CommonUtils.getByteLength(ell);
            Assert.assertEquals("ell=" + ell, byteLen, got.length);
            Assert.assertArrayEquals("ell=" + ell, Arrays.copyOfRange(lePrf, 0, byteLen), got);
        }
    }

    @Test
    public void secureJoinProtocolShareIsFixedReduce() throws IOException {
        byte[] lePrf = loadOneFixedKeyY();
        byte[] msb = HaoWan26SecureJoinParams.lePackedToBinaryUtilsPacked(lePrf);
        for (int ell : SECURE_JOIN_ELL) {
            byte[] got = HaoWan26Truncate.truncateJavaMsbShare(
                msb, ell, F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN
            );
            int byteLen = CommonUtils.getByteLength(ell);
            Assert.assertTrue(
                "ell=" + ell,
                BytesUtils.isFixedReduceByteArray(got, byteLen, ell)
            );
            // Same ell bits as LE prefix (mask + reverse), not the unmasked KAT prefix when ell%8!=0.
            byte[] lePrefix = Arrays.copyOfRange(lePrf, 0, byteLen);
            byte[] expect = HaoWan26Truncate.secureJoinLePrefixToFixedReduce(lePrefix, ell);
            Assert.assertArrayEquals("ell=" + ell, expect, got);
        }
    }

    @Test
    public void nativeUsesCreateReduceByteArrayOnMsbExample() {
        // Distinctive MSB-packed block: high-order bytes non-zero so prefix ≠ reduce.
        byte[] msb = hex("ff0102030405060708090a0b0c0d0e0f");
        Assert.assertEquals(F32Wprf.getOutputByteLength(), msb.length);
        for (int ell : SECURE_JOIN_ELL) {
            byte[] expect = BytesUtils.createReduceByteArray(msb, ell);
            byte[] got = HaoWan26Truncate.truncate(msb, ell, F32WprfPublicParamsType.MPC4J_NATIVE);
            Assert.assertArrayEquals("ell=" + ell, expect, got);
            if (ell < msb.length * Byte.SIZE) {
                // Sanity: secure-join prefix differs from native reduce for this MSB pattern.
                byte[] securePrefix = Arrays.copyOfRange(msb, 0, CommonUtils.getByteLength(ell));
                Assert.assertFalse(
                    "ell=" + ell + " expected native≠secure-join for MSB example",
                    Arrays.equals(securePrefix, got)
                );
            }
        }
    }

    private static byte[] loadOneFixedKeyY() throws IOException {
        String root = HaoWan26SecureJoinParams.RESOURCE_ROOT;
        InputStream in = HaoWan26TruncateTest.class.getClassLoader().getResourceAsStream(root + "f_fixed_key.txt");
        Assert.assertNotNull("missing " + root + "f_fixed_key.txt", in);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("one ") && line.contains(" y=")) {
                    int yAt = line.indexOf(" y=");
                    return hex(line.substring(yAt + 3).trim());
                }
            }
        }
        Assert.fail("missing one y= vector in f_fixed_key.txt");
        return new byte[0];
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
