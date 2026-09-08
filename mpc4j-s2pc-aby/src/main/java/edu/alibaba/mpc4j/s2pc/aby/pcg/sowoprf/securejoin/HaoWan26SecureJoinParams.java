package edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.bitmatrix.dense.DenseBitMatrix;
import edu.alibaba.mpc4j.common.tool.bitmatrix.dense.DenseBitMatrixFactory;
import edu.alibaba.mpc4j.common.tool.bitmatrix.dense.DenseBitMatrixFactory.DenseBitMatrixType;
import edu.alibaba.mpc4j.common.tool.galoisfield.Z3ByteField;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrix;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrixFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfMatrixFactory.F32WprfMatrixType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads Hao–Wan / secure-join AltMod public parameters.
 * <p>
 * HaoWan {@code ePSU-from-ssPMT} {@code setup.sh} pins {@code Th0masAndy/secure-join} at
 * {@code 4a23526f4b3a8432f7fb12d54b9865e95faedcf4}. Vectors under
 * {@code classpath:haowan26/secure-join-4a23526-epsu-fast/} were dumped from the original
 * {@code ePSU_fast/ePSU} executable at {@code CryptMatrix/ePSU-from-ssPMT@255bf1e}, linked with
 * that secure-join revision (see resource {@code MANIFEST.md} / {@code PROVENANCE.txt}).
 * {@code ladnir/secure-join@1e1dddf} has an identical {@code Prf} subtree and is only a
 * secondary diagnostic mirror—not the authoritative dump path.
 * </p>
 * <p>
 * Bit strings in the resource files use cryptoTools little-endian bit packing within each byte.
 * {@link edu.alibaba.mpc4j.common.tool.utils.BinaryUtils} use MSB-first bit indices; this loader
 * converts when building {@code B} so logical bit index {@code i} is preserved.
 * </p>
 */
public final class HaoWan26SecureJoinParams {
    public static final String RESOURCE_ROOT = "haowan26/secure-join-4a23526-epsu-fast/";
    private static final String G_BASIS = RESOURCE_ROOT + "g_basis_images.txt";
    private static final String A_BASIS = RESOURCE_ROOT + "a_basis_images.txt";
    private static final String B_BASIS = RESOURCE_ROOT + "b_basis_images.txt";

    private static final int G_INPUT_BITS = CommonConstants.BLOCK_BIT_LENGTH;
    private static final int G_OUTPUT_BYTES = F32Wprf.N_BYTE_LENGTH;
    private static final Object LOCK = new Object();

    private static volatile byte[][] gBasisImages;
    private static volatile byte[][] aElements;
    private static volatile DenseBitMatrix matrixB;

    private HaoWan26SecureJoinParams() {
        // empty
    }

    /**
     * Expands a 128-bit public representative with the secure-join linear code {@code G}.
     * Matches reference {@code G(0) = 0} (no reserved nonzero coordinate).
     *
     * @param bits128 16-byte little-endian bit string (cryptoTools {@code block} bytes).
     * @return length-{@link F32Wprf#N} array with entries in {@code {0,1} ⊂ F_3}.
     */
    public static byte[] expandG(byte[] bits128) {
        MathPreconditions.checkEqual("bits128.length", "16", bits128.length, CommonConstants.BLOCK_BYTE_LENGTH);
        ensureGLoaded();
        byte[] codeword = new byte[G_OUTPUT_BYTES];
        for (int i = 0; i < G_INPUT_BITS; i++) {
            if (getLeBit(bits128, i)) {
                BytesUtils.xori(codeword, gBasisImages[i]);
            }
        }
        byte[] expanded = new byte[F32Wprf.N];
        for (int i = 0; i < F32Wprf.N; i++) {
            expanded[i] = getLeBit(codeword, i) ? (byte) 1 : (byte) 0;
        }
        return expanded;
    }

    /**
     * Returns matrix {@code A} in MPC4J storage layout (512×256 = transpose of logical 256×512).
     */
    public static F32WprfMatrix matrixA(Z3ByteField z3Field, F32WprfMatrixType type) {
        ensureALoaded();
        return F32WprfMatrixFactory.createFromElements(z3Field, aElements, type);
    }

    /**
     * Returns systematic matrix {@code B} (256×128) for {@link DenseBitMatrix#leftMultiply}.
     */
    public static DenseBitMatrix matrixB() {
        ensureBLoaded();
        return matrixB;
    }

    /**
     * Builds an {@link F32Wprf} with secure-join public {@code A}/{@code B}.
     */
    public static F32Wprf createF32Wprf(Z3ByteField z3Field, F32WprfMatrixType type) {
        return new F32Wprf(z3Field, matrixA(z3Field, type), matrixB());
    }

    /**
     * Evaluates {@code F(k,x)} with secure-join public matrices using cryptoTools little-endian
     * key and input blocks (same as {@code AltModPrf::eval} / {@code f_fixed_key.txt}).
     * <p>
     * Converts LE ↔ MSB packing at the boundary; production protocol keys remain MSB-packed
     * Java random keys.
     * </p>
     *
     * @param keyLe64 64-byte LE key ({@code KeyType} as contiguous {@code block::data()}).
     * @param xLe16   16-byte LE input block.
     * @param type    matrix encoding.
     * @return 16-byte LE PRF output.
     */
    public static byte[] evaluateFLe(byte[] keyLe64, byte[] xLe16, F32WprfMatrixType type) {
        MathPreconditions.checkEqual("keyLe64.length", "64", keyLe64.length, F32Wprf.N_BYTE_LENGTH);
        MathPreconditions.checkEqual("xLe16.length", "16", xLe16.length, CommonConstants.BLOCK_BYTE_LENGTH);
        Z3ByteField field = new Z3ByteField();
        F32Wprf wprf = createF32Wprf(field, type);
        wprf.init(lePackedToBinaryUtilsPacked(keyLe64));
        byte[] yMsb = wprf.prf(expandG(xLe16));
        return binaryUtilsPackedToLePacked(yMsb);
    }

    /**
     * Builds the dump_altmod_basis fixed test key: {@code key[i] = block(i+1, i+2)} for {@code i∈[0,4)}.
     */
    public static byte[] fixedTestKeyLe() {
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


    /**
     * Converts cryptoTools / secure-join little-endian packed bits to {@link BinaryUtils} MSB-first packing
     * while preserving logical bit indices.
     */
    public static byte[] lePackedToBinaryUtilsPacked(byte[] lePacked) {
        byte[] out = new byte[lePacked.length];
        for (int i = 0; i < lePacked.length; i++) {
            out[i] = reverseBits(lePacked[i]);
        }
        return out;
    }

    /**
     * Inverse of {@link #lePackedToBinaryUtilsPacked(byte[])}.
     */
    public static byte[] binaryUtilsPackedToLePacked(byte[] msbPacked) {
        return lePackedToBinaryUtilsPacked(msbPacked);
    }

    static boolean getLeBit(byte[] bytes, int bitIndex) {
        return ((bytes[bitIndex >> 3] >> (bitIndex & 7)) & 1) != 0;
    }

    private static byte reverseBits(byte value) {
        int x = value & 0xFF;
        x = Integer.reverse(x) >>> 24;
        return (byte) x;
    }

    private static void ensureGLoaded() {
        if (gBasisImages != null) {
            return;
        }
        synchronized (LOCK) {
            if (gBasisImages != null) {
                return;
            }
            byte[][] images = new byte[G_INPUT_BITS][];
            parseIndexedHexLines(G_BASIS, G_INPUT_BITS, G_OUTPUT_BYTES * 2, (index, payload) -> {
                byte[] image = hexToBytes(payload);
                MathPreconditions.checkEqual("g_basis[" + index + "].length", "64", image.length, G_OUTPUT_BYTES);
                images[index] = image;
            });
            for (int i = 0; i < G_INPUT_BITS; i++) {
                Preconditions.checkNotNull(images[i], "missing G basis image " + i);
            }
            gBasisImages = images;
        }
    }

    private static void ensureALoaded() {
        if (aElements != null) {
            return;
        }
        synchronized (LOCK) {
            if (aElements != null) {
                return;
            }
            byte[][] elements = new byte[F32Wprf.N][F32Wprf.M];
            parseIndexedHexLines(A_BASIS, F32Wprf.N, F32Wprf.M * 2, (index, payload) -> {
                byte[] image = hexToBytes(payload);
                MathPreconditions.checkEqual("a_basis[" + index + "].length", "256", image.length, F32Wprf.M);
                for (int j = 0; j < F32Wprf.M; j++) {
                    byte v = image[j];
                    Preconditions.checkArgument(v == 0 || v == 1 || v == 2, "A entry not in F_3");
                    // Logical A is 256×512; MPC4J stores transpose: elements[input][output] = A(e_input)[output].
                    elements[index][j] = v;
                }
            });
            aElements = elements;
        }
    }

    private static void ensureBLoaded() {
        if (matrixB != null) {
            return;
        }
        synchronized (LOCK) {
            if (matrixB != null) {
                return;
            }
            // Prefer full B(e_i) images (exact encode path) over reconstructing from parity.
            byte[][] rowsMsb = new byte[F32Wprf.M][];
            parseIndexedHexLines(B_BASIS, F32Wprf.M, CommonConstants.BLOCK_BYTE_LENGTH * 2, (index, payload) -> {
                byte[] rowLe = hexToBytes(payload);
                MathPreconditions.checkEqual("b_basis[" + index + "].length", "16", rowLe.length, CommonConstants.BLOCK_BYTE_LENGTH);
                rowsMsb[index] = lePackedToBinaryUtilsPacked(rowLe);
            });
            for (int i = 0; i < F32Wprf.M; i++) {
                Preconditions.checkNotNull(rowsMsb[i], "missing B basis image " + i);
            }
            matrixB = DenseBitMatrixFactory.createFromDense(DenseBitMatrixType.BYTE_MATRIX, F32Wprf.T, rowsMsb);
        }
    }

    @FunctionalInterface
    private interface IndexedHexConsumer {
        void accept(int index, String hexPayload);
    }

    private static void parseIndexedHexLines(String resource, int expectedCount, int expectedHexLen, IndexedHexConsumer consumer) {
        int seen = 0;
        try (InputStream in = openResource(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int sp = line.indexOf(' ');
                Preconditions.checkArgument(sp > 0, "bad resource line in " + resource + ": " + line);
                int index = Integer.parseInt(line.substring(0, sp));
                String hex = line.substring(sp + 1).trim();
                // Allow trailing comments / extra tokens by taking the first hex token only.
                int end = hex.indexOf(' ');
                if (end >= 0) {
                    hex = hex.substring(0, end);
                }
                MathPreconditions.checkEqual("hex.length", "expected", hex.length(), expectedHexLen);
                MathPreconditions.checkNonNegativeInRange("index", index, expectedCount);
                consumer.accept(index, hex);
                seen++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + resource, e);
        }
        MathPreconditions.checkEqual("lines", "expectedCount", seen, expectedCount);
    }

    private static InputStream openResource(String resource) {
        InputStream in = HaoWan26SecureJoinParams.class.getClassLoader().getResourceAsStream(resource);
        Preconditions.checkNotNull(in, "Missing classpath resource: " + resource);
        return in;
    }

    private static byte[] hexToBytes(String hex) {
        MathPreconditions.checkEqual("hex.length even", "even", hex.length() & 1, 0);
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
