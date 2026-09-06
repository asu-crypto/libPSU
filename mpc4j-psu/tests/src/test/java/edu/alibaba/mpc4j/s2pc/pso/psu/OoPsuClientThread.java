package edu.alibaba.mpc4j.s2pc.pso.psu;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Offline/Online PSU client test thread.
 */
public class OoPsuClientThread extends Thread {
    private final OoPsuClient client;
    private final Set<ByteBuffer> clientElementSet;
    private final int serverElementSize;
    private final int elementByteLength;
    private PsuClientOutput clientOutput;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    OoPsuClientThread(OoPsuClient client, Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength) {
        this.client = client;
        this.clientElementSet = clientElementSet;
        this.serverElementSize = serverElementSize;
        this.elementByteLength = elementByteLength;
    }

    PsuClientOutput getClientOutput() {
        return clientOutput;
    }

    void rethrowIfFailed() {
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
        throw new AssertionError("OoPSU client thread failed", t);
    }

    @Override
    public void run() {
        try {
            client.init(clientElementSet.size(), serverElementSize);
            clientOutput = client.psu(clientElementSet, serverElementSize, elementByteLength);
        } catch (Throwable e) {
            failure.set(e);
        }
    }
}
