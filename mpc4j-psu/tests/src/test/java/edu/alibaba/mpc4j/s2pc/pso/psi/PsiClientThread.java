package edu.alibaba.mpc4j.s2pc.pso.psi;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;

import java.nio.ByteBuffer;
import java.util.Set;

class PsiClientThread extends Thread {
    private final PsiClient client;
    private final Set<ByteBuffer> clientElementSet;
    private final int serverElementSize;
    private final int elementByteLength;
    private PsiClientOutput clientOutput;

    PsiClientThread(PsiClient client, Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength) {
        this.client = client;
        this.clientElementSet = clientElementSet;
        this.serverElementSize = serverElementSize;
        this.elementByteLength = elementByteLength;
    }

    PsiClientOutput getClientOutput() {
        return clientOutput;
    }

    @Override
    public void run() {
        try {
            client.init(clientElementSet.size(), serverElementSize);
            clientOutput = client.psi(clientElementSet, serverElementSize, elementByteLength);
        } catch (MpcAbortException e) {
            e.printStackTrace();
        }
    }
}
