package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Malformed ssOTd transfer / finish payloads must abort.
 */
public class HaoWan26SsOtdMalformedTest {
    private static final int N = 4;
    private static final int ELEMENT_BYTE_LENGTH = 16;

    @Test
    public void testWrongListLengthAborts() {
        List<byte[]> payload = new ArrayList<>();
        payload.add(new byte[ELEMENT_BYTE_LENGTH]);
        assertAbort(() -> HaoWan26SsOtdPayloadChecks.checkTransferPayload(payload, N, ELEMENT_BYTE_LENGTH));
    }

    @Test
    public void testWrongMaskedLengthAborts() {
        List<byte[]> payload = honestSkeleton();
        payload.set(0, new byte[ELEMENT_BYTE_LENGTH - 1]);
        assertAbort(() -> HaoWan26SsOtdPayloadChecks.checkTransferPayload(payload, N, ELEMENT_BYTE_LENGTH));
    }

    @Test
    public void testInvalidShareByteAborts() {
        List<byte[]> payload = honestSkeleton();
        payload.set(1, new byte[]{2});
        assertAbort(() -> HaoWan26SsOtdPayloadChecks.checkTransferPayload(payload, N, ELEMENT_BYTE_LENGTH));
    }

    @Test
    public void testWrongShareEncodingLengthAborts() {
        List<byte[]> payload = honestSkeleton();
        payload.set(1, new byte[]{0, 1});
        assertAbort(() -> HaoWan26SsOtdPayloadChecks.checkTransferPayload(payload, N, ELEMENT_BYTE_LENGTH));
    }

    @Test
    public void testHonestPayloadAccepted() throws MpcAbortException {
        HaoWan26SsOtdPayloadChecks.checkTransferPayload(honestSkeleton(), N, ELEMENT_BYTE_LENGTH);
        HaoWan26SsOtdPayloadChecks.checkFinishToken(Collections.singletonList(new byte[]{1}));
    }

    @Test
    public void testBadFinishTokenAborts() {
        assertAbort(() -> HaoWan26SsOtdPayloadChecks.checkFinishToken(Collections.singletonList(new byte[]{0})));
        assertAbort(() -> HaoWan26SsOtdPayloadChecks.checkFinishToken(Collections.emptyList()));
    }

    private static List<byte[]> honestSkeleton() {
        List<byte[]> payload = new ArrayList<>(N * 2);
        for (int i = 0; i < N; i++) {
            payload.add(new byte[ELEMENT_BYTE_LENGTH]);
            payload.add(new byte[]{(byte) (i & 1)});
        }
        return payload;
    }

    private static void assertAbort(AbortRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("expected MpcAbortException");
        } catch (MpcAbortException expected) {
            // ok
        }
    }

    @FunctionalInterface
    private interface AbortRunnable {
        void run() throws MpcAbortException;
    }
}
