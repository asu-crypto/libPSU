package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import org.bouncycastle.util.Arrays;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Public 128-bit → 512-{@code F_3} expansion for Hao–Wan ssPMT-fast (Figure 17 / AltMod domain).
 * <p>
 * Default profiles ({@link ExpandProfile#SECURE_JOIN_COMPAT}, {@link ExpandProfile#MPC4J_NATIVE}) use the same
 * deterministic public systematic linear code: identity on the first 128 bits plus 384 public parity checks.
 * Full secure-join {@code mGCode} KAT bit-compatibility is pending until the exact matrices are vendored in-repo.
 * </p>
 * <p>
 * Output coordinates lie in {@code {0,1} ⊂ F_3}. F32WPRF rejects the all-zero input; if expansion would be zero,
 * a reserved coordinate is set so {@code prf} remains well-defined.
 * </p>
 */
public final class HaoWan26AltModExpand {
    /**
     * Expansion / matrix profile.
     */
    public enum ExpandProfile {
        /**
         * Intended cross-language default; currently uses the public systematic stand-in (mGCode KAT pending).
         */
        SECURE_JOIN_COMPAT,
        /**
         * MPC4J-native AltMod path; same systematic stand-in until secure-join matrices are available.
         */
        MPC4J_NATIVE,
    }

    /**
     * Reserved F3 coordinate forced to 1 when the systematic codeword would otherwise be all-zero.
     */
    public static final int RESERVED_NONZERO_INDEX = F32Wprf.N - 1;

    private static final int INPUT_BITS = CommonConstants.BLOCK_BIT_LENGTH;
    private static final int PARITY_BITS = F32Wprf.N - INPUT_BITS;
    /**
     * Public seed for the parity-check stream (not a secret key).
     */
    private static final byte[] PUBLIC_PARITY_SEED;
    /**
     * Packed parity rows: {@code PARITY_BITS} rows × 16 bytes each.
     */
    private static final byte[] PARITY_MATRIX_BYTES;

    static {
        PUBLIC_PARITY_SEED = BlockUtils.zeroBlock();
        PUBLIC_PARITY_SEED[0] = (byte) 0x48;
        PUBLIC_PARITY_SEED[1] = (byte) 0x57;
        PUBLIC_PARITY_SEED[2] = (byte) 0x32;
        PUBLIC_PARITY_SEED[3] = (byte) 0x36;
        PUBLIC_PARITY_SEED[4] = (byte) 0x6D;
        PUBLIC_PARITY_SEED[5] = (byte) 0x47;
        Prg prg = PrgFactory.createInstance(EnvType.STANDARD, PARITY_BITS * CommonConstants.BLOCK_BYTE_LENGTH);
        PARITY_MATRIX_BYTES = prg.extendToBytes(PUBLIC_PARITY_SEED);
    }

    private HaoWan26AltModExpand() {
        // empty
    }

    /**
     * Expands a set element to an F32WPRF input under the default profile.
     *
     * @param item set element (hashed to 128 bits if not already 16 bytes).
     * @return length-{@link F32Wprf#N} array with entries in {@code {0,1}}.
     */
    public static byte[] expand(byte[] item) {
        return expand(item, ExpandProfile.SECURE_JOIN_COMPAT);
    }

    /**
     * Expands a set element to an F32WPRF input.
     *
     * @param item    set element.
     * @param profile expansion profile.
     * @return length-{@link F32Wprf#N} array with entries in {@code {0,1}}.
     */
    public static byte[] expand(byte[] item, ExpandProfile profile) {
        MathPreconditions.checkPositive("item.length", item.length);
        // Profiles currently share the systematic stand-in; SECURE_JOIN_COMPAT will switch to mGCode when vendored.
        if (profile != ExpandProfile.SECURE_JOIN_COMPAT && profile != ExpandProfile.MPC4J_NATIVE) {
            throw new IllegalArgumentException("Unsupported expand profile: " + profile);
        }
        byte[] bits128 = to128BitPublic(item);
        byte[] expanded = new byte[F32Wprf.N];
        for (int i = 0; i < INPUT_BITS; i++) {
            expanded[i] = BinaryUtils.getBoolean(bits128, i) ? (byte) 1 : (byte) 0;
        }
        for (int p = 0; p < PARITY_BITS; p++) {
            int xor = 0;
            int rowOffset = p * CommonConstants.BLOCK_BYTE_LENGTH;
            for (int i = 0; i < INPUT_BITS; i++) {
                if (BinaryUtils.getBoolean(PARITY_MATRIX_BYTES, rowOffset * Byte.SIZE + i)
                    && BinaryUtils.getBoolean(bits128, i)) {
                    xor ^= 1;
                }
            }
            expanded[INPUT_BITS + p] = (byte) xor;
        }
        if (Arrays.areAllZeroes(expanded, 0, expanded.length)) {
            expanded[RESERVED_NONZERO_INDEX] = 1;
        }
        return expanded;
    }

    /**
     * Maps an arbitrary-length item to a public 128-bit representative (identity when already 16 bytes).
     */
    static byte[] to128BitPublic(byte[] item) {
        if (item.length == CommonConstants.BLOCK_BYTE_LENGTH) {
            return java.util.Arrays.copyOf(item, item.length);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((byte) 0x48);
            digest.update((byte) 0x57);
            digest.update((byte) 0x45);
            digest.update((byte) 0x58);
            digest.update(item);
            byte[] hash = digest.digest();
            return java.util.Arrays.copyOf(hash, CommonConstants.BLOCK_BYTE_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
