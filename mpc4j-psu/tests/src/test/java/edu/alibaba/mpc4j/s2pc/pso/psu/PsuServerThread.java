package edu.alibaba.mpc4j.s2pc.pso.psu;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PSU protocol server test thread. Failures are stored and must be rethrown via
 * {@link #rethrowIfFailed()} after {@link #join()}.
 */
class PsuServerThread extends Thread {
    private final PsuServer server;
    private final Set<ByteBuffer> serverElementSet;
    private final int clientElementSize;
    private final int elementByteLength;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    PsuServerThread(PsuServer server, Set<ByteBuffer> serverElementSet, int clientElementSize,
                    int elementByteLength) {
        this.server = server;
        this.serverElementSet = serverElementSet;
        this.clientElementSize = clientElementSize;
        this.elementByteLength = elementByteLength;
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
        throw new AssertionError("PSU server thread failed", t);
    }

    @Override
    public void run() {
        try {
            server.init(serverElementSet.size(), clientElementSize);
            server.psu(serverElementSet, clientElementSize, elementByteLength);
        } catch (Throwable e) {
            failure.set(e);
        }
    }
}
