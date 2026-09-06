package edu.alibaba.mpc4j.s2pc.pso.psu;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Offline/Online PSU server test thread.
 */
public class OoPsuServerThread extends Thread {
    private final OoPsuServer server;
    private final Set<ByteBuffer> serverElementSet;
    private final int clientElementSize;
    private final int elementByteLength;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    OoPsuServerThread(OoPsuServer server, Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) {
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
        throw new AssertionError("OoPSU server thread failed", t);
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
