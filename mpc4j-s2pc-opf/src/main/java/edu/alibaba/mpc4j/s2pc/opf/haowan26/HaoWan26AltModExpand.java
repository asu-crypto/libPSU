package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.prg.Prg;
import edu.alibaba.mpc4j.common.tool.crypto.prg.PrgFactory;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams;
import org.bouncycastle.util.Arrays;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Public 128-bit → 512-{@code F_3} expansion for Hao–Wan ssPMT-fast (Figure 17 / AltMod domain).
 * <p>
 * {@link ExpandProfile#HAO_WAN_SECURE_JOIN} uses the exact secure-join G basis images
 * (ladnir/secure-join @ 1e1dddf). Zero inputs expand to the all-zero codeword (matching the
 * reference); {@link F32Wprf#prf} still rejects all-zero F3 inputs, so callers must not query
 * the zero representative unless they handle that precondition at the protocol boundary.
 * </p>
 * <p>
 * {@link ExpandProfile#MPC4J_NATIVE} retains the historical systematic stand-in used by
 * non–Hao-Wan MPC4J experiments (not byte-compatible with secure-join).
 * </p>
 */
public final class HaoWan26AltModExpand {
    public enum ExpandProfile {
        /** Exact secure-join G; selected by HaoWan26 defaults. */
        HAO_WAN_SECURE_JOIN,
        /** MPC4J-only systematic linear code (not secure-join byte-compatible). */
        MPC4J_NATIVE,
    }

    /**
     * Reserved F3 coordinate forced to 1 when the MPC4J-native systematic codeword would be zero.
     * Not used for {@link ExpandProfile#HAO_WAN_SECURE_JOIN}.
     */
    public static final int RESERVED_NONZERO_INDEX = F32Wprf.N - 1;

    private static final int INPUT_BITS = CommonConstants.BLOCK_BIT_LENGTH;
    private static final int PARITY_BITS = F32Wprf.N - INPUT_BITS;
    private static final byte[] PUBLIC_PARITY_SEED;
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
    }

    public static byte[] expand(byte[] item) {
        return expand(item, ExpandProfile.HAO_WAN_SECURE_JOIN);
    }

    public static byte[] expand(byte[] item, ExpandProfile profile) {
        MathPreconditions.checkPositive("item.length", item.length);
        byte[] bits128 = to128BitPublic(item);
        if (profile == ExpandProfile.HAO_WAN_SECURE_JOIN) {
            byte[] expanded = HaoWan26SecureJoinParams.expandG(bits128);
            // Match secure-join G(0)=0; do not silently remap. Reject at the protocol boundary.
            if (Arrays.areAllZeroes(expanded, 0, expanded.length)) {
                throw new IllegalArgumentException(
                    "HaoWan secure-join AltMod rejects G(x)=0 (all-zero public representative / kernel); "
                        + "callers must exclude the zero codeword at the public protocol boundary"
                );
            }
            return expanded;
        }
        if (profile != ExpandProfile.MPC4J_NATIVE) {
            throw new IllegalArgumentException("Unsupported expand profile: " + profile);
        }
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
