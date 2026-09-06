package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

import java.nio.ByteBuffer;
import java.util.Set;

class PsiServerThread extends Thread {
    private final PsiServer server;
    private final Set<ByteBuffer> serverElementSet;
    private final int clientElementSize;
    private final int elementByteLength;

    PsiServerThread(PsiServer server, Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) {
        this.server = server;
        this.serverElementSet = serverElementSet;
        this.clientElementSize = clientElementSize;
        this.elementByteLength = elementByteLength;
    }

    @Override
    public void run() {
        try {
            server.init(serverElementSet.size(), clientElementSize);
            server.psi(serverElementSet, clientElementSize, elementByteLength);
        } catch (MpcAbortException e) {
            e.printStackTrace();
        }
    }
}
