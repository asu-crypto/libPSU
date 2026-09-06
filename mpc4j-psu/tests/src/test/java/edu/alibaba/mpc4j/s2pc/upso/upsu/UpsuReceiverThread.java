package edu.alibaba.mpc4j.s2pc.upso.upsu;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UPSU receiver thread. Failures must be rethrown via {@link #rethrowIfFailed()} after join.
 */
public class UpsuReceiverThread extends Thread {
    private final UpsuReceiver receiver;
    private final Set<ByteBuffer> receiverElementSet;
    private final int senderElementSize;
    private final int elementByteLength;
    private UpsuReceiverOutput receiverOutput;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    UpsuReceiverThread(UpsuReceiver receiver, int senderElementSize, Set<ByteBuffer> receiverElementSet,
                       int elementByteLength) {
        this.receiver = receiver;
        this.senderElementSize = senderElementSize;
        this.receiverElementSet = receiverElementSet;
        this.elementByteLength = elementByteLength;
    }

    UpsuReceiverOutput getReceiverOutput() {
        return receiverOutput;
    }

    public void rethrowIfFailed() {
        Throwable t = failure.get();
        if (t == null) {
            return;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new AssertionError("UPSU receiver thread failed", t);
    }

    @Override
    public void run() {
        try {
            receiver.init(receiverElementSet, senderElementSize, elementByteLength);
            receiverOutput = receiver.psu(senderElementSize);
        } catch (Throwable e) {
            failure.set(e);
        }
    }
}
