package edu.alibaba.mpc4j.s2pc.upso.upsu;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UPSU sender thread. Failures must be rethrown via {@link #rethrowIfFailed()} after join.
 */
public class UpsuSenderThread extends Thread {
    private final UpsuSender sender;
    private final Set<ByteBuffer> senderElementSet;
    private final int receiverElementSize;
    private final int elementByteLength;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    UpsuSenderThread(UpsuSender sender, int receiverElementSize, Set<ByteBuffer> senderElementSet,
                     int elementByteLength) {
        this.sender = sender;
        this.receiverElementSize = receiverElementSize;
        this.senderElementSet = senderElementSet;
        this.elementByteLength = elementByteLength;
    }

    public Throwable getFailure() {
        return failure.get();
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
        throw new AssertionError("UPSU sender thread failed", t);
    }

    @Override
    public void run() {
        try {
            sender.init(senderElementSet.size(), receiverElementSize);
            sender.psu(senderElementSet, elementByteLength);
        } catch (Throwable e) {
            failure.set(e);
        }
    }
}
