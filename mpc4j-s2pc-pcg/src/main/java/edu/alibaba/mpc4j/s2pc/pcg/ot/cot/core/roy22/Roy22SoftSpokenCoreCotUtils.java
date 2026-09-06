package edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

/**
 * Roy22 SoftSpoken core COT utilities.
 *
 * <p><strong>Scope (Component 1 of 2):</strong> these utilities implement the subspace-VOLE
 * arithmetic at the heart of Roy22's semi-honest construction (Fig. 12), specialized to {@code k = 2}
 * (= libOTe's {@code FIELD_BITS = 2}) and using the naive {@code 2^k} base-OTs-per-block layout.
 * They <strong>do not yet</strong> include the subspace-code linear amortization (Component 2 of 2)
 * that gives Roy22 its bandwidth advantage over IKNP03 — wire bandwidth in Component 1 is identical
 * to IKNP03. See the package {@code README.md} for the upgrade plan.</p>
 *
 * <h3>Conventions</h3>
 * <ul>
 *     <li>{@code k = 2}: the small-field exponent. Field {@code F = GF(2^2) = GF(4)}, four elements
 *         encoded as integers {@code 0, 1, 2, 3} with the irreducible polynomial
 *         {@code x^2 + x + 1}: {@code 0 = 0}, {@code 1 = 1}, {@code 2 = x}, {@code 3 = x + 1}.</li>
 *     <li>κ = {@link CommonConstants#BLOCK_BIT_LENGTH} = 128 bits.</li>
 *     <li>NUM_BLOCKS = κ / k = 64. Each block holds one F-element of Δ.</li>
 *     <li>F-element packing (little-endian within byte): byte {@code i} packs F-elements
 *         {@code [4i, 4i+1, 4i+2, 4i+3]} in bit positions {@code [0..1, 2..3, 4..5, 6..7]}. A
 *         κ-bit value (16 bytes) thus holds 64 F-elements — exactly one per block.</li>
 * </ul>
 *
 * @author audit follow-up #5 (May 26, 2026)
 */
final class Roy22SoftSpokenCoreCotUtils {
    /**
     * Small-field exponent for this Component 1 implementation. Hard-coded to {@code k = 2} (libOTe
     * default). Generalizing to {@code k ∈ {1, 3, 4}} is a follow-up; the algebra below assumes
     * {@code k = 2} and indexes into a 4×4 GF(4) multiplication table.
     */
    static final int FIELD_BITS = 2;
    /**
     * |F| = 2^k.
     */
    static final int FIELD_SIZE = 1 << FIELD_BITS;
    /**
     * Number of F-elements per κ-bit block, i.e. NUM_BLOCKS = κ / k. At k=2: 128/2 = 64.
     */
    static final int NUM_BLOCKS = CommonConstants.BLOCK_BIT_LENGTH / FIELD_BITS;
    /**
     * Number of base OT instances per Roy22 init = FIELD_SIZE × NUM_BLOCKS. At k=2: 4 × 64 = 256.
     * Compare with IKNP03's κ = 128 base OTs — the trade-off is doubled base-OT count for the
     * (currently absent) subspace-code bandwidth win.
     */
    static final int TOTAL_BASE_OTS = FIELD_SIZE * NUM_BLOCKS;
    /**
     * Number of F-elements per F-row when expressed as bytes. At k=2 we pack 4 F-elements per byte,
     * so a row of {@code num} F-elements occupies {@code ceil(num * k / 8) = ceil(num / 4)} bytes.
     */
    static int fRowByteLength(int num) {
        MathPreconditions.checkPositive("num", num);
        return (num + (Byte.SIZE / FIELD_BITS) - 1) / (Byte.SIZE / FIELD_BITS);
    }

    /**
     * GF(4) multiplication table over the irreducible polynomial {@code x^2 + x + 1}, with the
     * encoding {@code 0=0, 1=1, 2=x, 3=x+1}.
     * <pre>
     *           b=0  b=1  b=2  b=3
     *   a=0  [   0    0    0    0  ]
     *   a=1  [   0    1    2    3  ]
     *   a=2  [   0    2    3    1  ]   (x*x = x+1, x*(x+1) = 1)
     *   a=3  [   0    3    1    2  ]   ((x+1)*x = 1, (x+1)*(x+1) = x)
     * </pre>
     */
    private static final byte[][] MUL_TABLE = {
        {0, 0, 0, 0},
        {0, 1, 2, 3},
        {0, 2, 3, 1},
        {0, 3, 1, 2},
    };

    /**
     * GF(4) multiplication. Inputs and outputs are in {@code [0, 4)}.
     */
    static int gfMul(int a, int b) {
        return MUL_TABLE[a & (FIELD_SIZE - 1)][b & (FIELD_SIZE - 1)] & (FIELD_SIZE - 1);
    }

    /**
     * Extracts F-element {@code idx} from a packed F-row stored in {@code bytes}. Each byte holds
     * {@code Byte.SIZE / FIELD_BITS = 4} F-elements at k=2.
     */
    static int extractField(byte[] bytes, int idx) {
        int byteIdx = idx / (Byte.SIZE / FIELD_BITS);
        int shift = (idx % (Byte.SIZE / FIELD_BITS)) * FIELD_BITS;
        return (bytes[byteIdx] >>> shift) & (FIELD_SIZE - 1);
    }

    /**
     * Sets F-element {@code idx} of a packed F-row to {@code value}. Other elements in the same byte
     * are preserved (read-modify-write).
     */
    static void setField(byte[] bytes, int idx, int value) {
        int byteIdx = idx / (Byte.SIZE / FIELD_BITS);
        int shift = (idx % (Byte.SIZE / FIELD_BITS)) * FIELD_BITS;
        int mask = (FIELD_SIZE - 1) << shift;
        bytes[byteIdx] = (byte) ((bytes[byteIdx] & ~mask) | ((value & (FIELD_SIZE - 1)) << shift));
    }

    /**
     * Decomposes a κ-bit value (= 16-byte big-endian byte array) into NUM_BLOCKS F-elements. The
     * mpc4j convention is that {@code value[0]} is the most-significant byte (matching
     * {@code BinaryUtils.byteArrayToBinary} / {@code BlockUtils}). Block {@code b} occupies bits
     * {@code [b*k, (b+1)*k)} of the κ-bit value, counted from the LSB.
     */
    static int[] decomposeBlock(byte[] value) {
        MathPreconditions.checkEqual("value.length", "κ/8", value.length, CommonConstants.BLOCK_BYTE_LENGTH);
        int[] digits = new int[NUM_BLOCKS];
        for (int b = 0; b < NUM_BLOCKS; b++) {
            int bitOffset = b * FIELD_BITS;
            int byteIdx = (CommonConstants.BLOCK_BYTE_LENGTH - 1) - (bitOffset / Byte.SIZE);
            int shift = bitOffset % Byte.SIZE;
            digits[b] = (value[byteIdx] >>> shift) & (FIELD_SIZE - 1);
        }
        return digits;
    }

    /**
     * Inverse of {@link #decomposeBlock(byte[])}.
     */
    static byte[] composeBlock(int[] digits) {
        MathPreconditions.checkEqual("digits.length", "NUM_BLOCKS", digits.length, NUM_BLOCKS);
        byte[] block = new byte[CommonConstants.BLOCK_BYTE_LENGTH];
        for (int b = 0; b < NUM_BLOCKS; b++) {
            int bitOffset = b * FIELD_BITS;
            int byteIdx = (CommonConstants.BLOCK_BYTE_LENGTH - 1) - (bitOffset / Byte.SIZE);
            int shift = bitOffset % Byte.SIZE;
            block[byteIdx] |= (byte) ((digits[b] & (FIELD_SIZE - 1)) << shift);
        }
        return block;
    }

    /**
     * In-place XOR-and-multiply: {@code acc[i] ^= a * src[i]} for every F-element {@code i} in the
     * packed F-row. When {@code a = 0} this is a no-op; when {@code a = 1} this is plain XOR. For
     * {@code a ∈ {2, 3}} it walks element-by-element through the byte array using the multiplication
     * table.
     *
     * @param acc destination F-row, in place.
     * @param src source F-row.
     * @param a   GF(4) scalar.
     * @param numFields number of F-elements packed in each row.
     */
    static void axpyFieldRow(byte[] acc, byte[] src, int a, int numFields) {
        if (a == 0) {
            return;
        }
        if (a == 1) {
            for (int i = 0; i < acc.length; i++) {
                acc[i] ^= src[i];
            }
            return;
        }
        // a ∈ {2, 3}: per-element multiply via the table. Walks 4 F-elements per byte at k=2.
        for (int i = 0; i < numFields; i++) {
            int product = gfMul(a, extractField(src, i));
            // XOR into acc[i]
            int byteIdx = i / (Byte.SIZE / FIELD_BITS);
            int shift = (i % (Byte.SIZE / FIELD_BITS)) * FIELD_BITS;
            acc[byteIdx] ^= (byte) ((product & (FIELD_SIZE - 1)) << shift);
        }
    }

    /**
     * For each F-element index {@code i ∈ [0, numFields)}, computes
     * {@code dest[i] := scalar * src[i]} into a packed F-row.
     *
     * @param dest destination F-row, fully overwritten.
     * @param src  source F-row.
     * @param scalar GF(4) scalar in {@code [0, 4)}.
     * @param numFields number of F-elements.
     */
    static void scaleFieldRow(byte[] dest, byte[] src, int scalar, int numFields) {
        if (scalar == 0) {
            java.util.Arrays.fill(dest, (byte) 0);
            return;
        }
        if (scalar == 1) {
            System.arraycopy(src, 0, dest, 0, dest.length);
            return;
        }
        java.util.Arrays.fill(dest, (byte) 0);
        for (int i = 0; i < numFields; i++) {
            int product = gfMul(scalar, extractField(src, i));
            int byteIdx = i / (Byte.SIZE / FIELD_BITS);
            int shift = (i % (Byte.SIZE / FIELD_BITS)) * FIELD_BITS;
            dest[byteIdx] |= (byte) ((product & (FIELD_SIZE - 1)) << shift);
        }
    }

    /**
     * Embeds a boolean COT-choice array as a packed F-row, with {@code true ↦ 1_F} and
     * {@code false ↦ 0_F}.
     */
    static byte[] embedChoicesAsFieldRow(boolean[] choices) {
        byte[] row = new byte[fRowByteLength(choices.length)];
        for (int i = 0; i < choices.length; i++) {
            if (choices[i]) {
                int byteIdx = i / (Byte.SIZE / FIELD_BITS);
                int shift = (i % (Byte.SIZE / FIELD_BITS)) * FIELD_BITS;
                row[byteIdx] |= (byte) (1 << shift);
            }
        }
        return row;
    }

    private Roy22SoftSpokenCoreCotUtils() {
        // no instances
    }
}
