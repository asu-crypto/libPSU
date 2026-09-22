package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.MpcAbortPreconditions;

import java.util.List;

/**
 * Shared transfer-payload validation for Hao–Wan ssOTd.
 */
final class HaoWan26SsOtdPayloadChecks {
    private HaoWan26SsOtdPayloadChecks() {
        // empty
    }

    /**
     * Validates the server transfer list: length {@code 2n}, masked element length, share encoding.
     *
     * @param payload           received payload.
     * @param n                 number of positions.
     * @param elementByteLength expected masked-element length.
     * @throws MpcAbortException on any malformation.
     */
    static void checkTransferPayload(List<byte[]> payload, int n, int elementByteLength) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(payload != null && payload.size() == n * 2);
        for (int i = 0; i < n; i++) {
            byte[] masked = payload.get(2 * i);
            byte[] shareBytes = payload.get(2 * i + 1);
            MpcAbortPreconditions.checkArgument(masked != null && masked.length == elementByteLength);
            MpcAbortPreconditions.checkArgument(shareBytes != null && shareBytes.length == 1);
            byte shareByte = shareBytes[0];
            MpcAbortPreconditions.checkArgument(shareByte == 0 || shareByte == 1);
        }
    }

    /**
     * Validates a one-byte finish ack / confirm payload.
     *
     * @param payload received payload.
     * @throws MpcAbortException on malformation.
     */
    static void checkFinishToken(List<byte[]> payload) throws MpcAbortException {
        MpcAbortPreconditions.checkArgument(payload != null && payload.size() == 1);
        MpcAbortPreconditions.checkArgument(payload.get(0) != null && payload.get(0).length == 1 && payload.get(0)[0] == 1);
    }
}
