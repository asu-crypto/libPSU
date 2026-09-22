package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType;

import java.util.Arrays;

/**
 * Truncation helpers for Hao–Wan ssPMT-fast comparison length
 * {@code ellBits = 40 + ceil(log2(queryCount))}, capped at the F32 output bit length.
 * <p>
 * Secure-join matches the reference {@code memcpy(output, &prfValue, ceil(ell/8))} on the
 * little-endian PRF block (first raw bytes, no high-bit mask). {@link F32WprfPublicParamsType#MPC4J_NATIVE}
 * retains {@link BytesUtils#createReduceByteArray} semantics.
 * </p>
 */
public final class HaoWan26Truncate {
    private HaoWan26Truncate() {
        // empty
    }

    /**
     * Computes {@code ellBits} for a public query count (server-set size / membership queries).
     *
     * @param queryCount number of membership queries ({@code |X|} / {@code serverN}).
     * @return truncated comparison bit length.
     */
    public static int ellBits(int queryCount) {
        MathPreconditions.checkPositive("queryCount", queryCount);
        int bits = CommonConstants.STATS_BIT_LENGTH + LongUtils.ceilLog2(queryCount);
        int maxBits = F32Wprf.getOutputByteLength() * Byte.SIZE;
        return Math.min(bits, maxBits);
    }

    /**
     * Byte length of a truncated comparison string.
     *
     * @param queryCount number of membership queries.
     * @return {@code ceil(ellBits / 8)}.
     */
    public static int ellBytes(int queryCount) {
        return CommonUtils.getByteLength(ellBits(queryCount));
    }

    /**
     * Truncates a PRF / SOW share according to the public-parameter profile.
     * <p>
     * For {@link F32WprfPublicParamsType#HAO_WAN_SECURE_JOIN}, {@code canonicalPrfOutput} must be
     * in the same external little-endian byte order as secure-join {@code AltModPrf} / KAT vectors.
     * Callers that hold MSB-packed Java bit-matrix outputs must convert with
     * {@link edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams#binaryUtilsPackedToLePacked}
     * before truncating under the secure-join profile.
     * </p>
     *
     * @param canonicalPrfOutput full-length PRF/share bytes.
     * @param bitLength          target bit length {@code ell}.
     * @param publicParamsType   G/A/B profile.
     * @return truncated bytes of length {@code ceil(bitLength/8)}.
     */
    public static byte[] truncate(
        byte[] canonicalPrfOutput,
        int bitLength,
        F32WprfPublicParamsType publicParamsType
    ) {
        MathPreconditions.checkPositive("bitLength", bitLength);
        int byteLength = CommonUtils.getByteLength(bitLength);
        MathPreconditions.checkGreaterOrEqual(
            "canonicalPrfOutput.length", canonicalPrfOutput.length, byteLength
        );
        return switch (publicParamsType) {
            case HAO_WAN_SECURE_JOIN -> Arrays.copyOfRange(canonicalPrfOutput, 0, byteLength);
            case MPC4J_NATIVE -> BytesUtils.createReduceByteArray(canonicalPrfOutput, bitLength);
        };
    }

    /**
     * Truncates a Java MSB-packed F32 / SOW share (or local {@link F32Wprf#prf} output) for OKVS/PEQT.
     * <p>
     * Secure-join path:
     * <ol>
     *   <li>convert MSB packing → cryptoTools little-endian bytes;</li>
     *   <li>take the first {@code ceil(ell/8)} bytes ({@code memcpy} as in SoOPPRF);</li>
     *   <li>clear unused high bits of the last LE byte (same mask as HaoWan {@code RPMT}/PEQT);</li>
     *   <li>reverse to MPC4J big-endian {@code isFixedReduceByteArray} layout required by DOKVS/PEQT.</li>
     * </ol>
     * Steps (1)–(2) are the reference byte prefix; (3)–(4) are a GF(2)-linear repack so Java OKVS/PEQT
     * accept the same {@code ell} bits. {@link #truncate} itself remains an unmasked LE prefix for KATs.
     * Native mode uses {@link BytesUtils#createReduceByteArray} on the MSB-packed input.
     * </p>
     */
    public static byte[] truncateJavaMsbShare(
        byte[] javaMsbPacked,
        int bitLength,
        F32WprfPublicParamsType publicParamsType
    ) {
        if (publicParamsType == F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN) {
            byte[] le = edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.securejoin.HaoWan26SecureJoinParams
                .binaryUtilsPackedToLePacked(javaMsbPacked);
            byte[] lePrefix = truncate(le, bitLength, publicParamsType);
            return secureJoinLePrefixToFixedReduce(lePrefix, bitLength);
        }
        return truncate(javaMsbPacked, bitLength, publicParamsType);
    }

    /**
     * Repacks a secure-join LE truncation prefix into MPC4J fixed-reduce form for OKVS/PEQT.
     * Masks the last LE byte to exactly {@code bitLength} bits (HaoWan PEQT), then byte-reverses
     * so unused bits sit in the high end of the first byte ({@link BytesUtils#isFixedReduceByteArray}).
     */
    static byte[] secureJoinLePrefixToFixedReduce(byte[] lePrefix, int bitLength) {
        int byteLength = CommonUtils.getByteLength(bitLength);
        MathPreconditions.checkEqual("lePrefix.length", "ceil(bitLength/8)", lePrefix.length, byteLength);
        byte[] le = Arrays.copyOf(lePrefix, byteLength);
        int rem = bitLength & 7;
        if (rem != 0) {
            le[byteLength - 1] &= (1 << rem) - 1;
        }
        byte[] fixed = new byte[byteLength];
        for (int i = 0; i < byteLength; i++) {
            fixed[i] = le[byteLength - 1 - i];
        }
        assert BytesUtils.isFixedReduceByteArray(fixed, byteLength, bitLength);
        return fixed;
    }

    /**
     * @deprecated Prefer {@link #truncate(byte[], int, F32WprfPublicParamsType)} or
     *             {@link #truncateJavaMsbShare(byte[], int, F32WprfPublicParamsType)}.
     */
    @Deprecated
    public static byte[] truncate(byte[] full, int ellBits) {
        return truncate(full, ellBits, F32WprfPublicParamsType.MPC4J_NATIVE);
    }
}
