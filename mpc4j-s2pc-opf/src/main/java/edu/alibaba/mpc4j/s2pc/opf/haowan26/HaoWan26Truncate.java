package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.common.tool.utils.CommonUtils;
import edu.alibaba.mpc4j.common.tool.utils.LongUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;

/**
 * Truncation helpers for Hao–Wan ssPMT-fast comparison length
 * {@code ellBits = 40 + ceil(log2(queryCount))}, capped at the F32 output bit length.
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
     * Truncates an F32 / SOW share (or PRF output) to {@code ellBits}, masking unused high bits.
     *
     * @param full     full-length bit string (typically 16 bytes).
     * @param ellBits  target bit length.
     * @return reduced byte array of length {@code ceil(ellBits / 8)}.
     */
    public static byte[] truncate(byte[] full, int ellBits) {
        MathPreconditions.checkPositive("ellBits", ellBits);
        MathPreconditions.checkGreaterOrEqual("full.bitLength", full.length * Byte.SIZE, ellBits);
        return BytesUtils.createReduceByteArray(full, ellBits);
    }
}
