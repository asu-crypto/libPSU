package edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuClient;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiverOutput;

import java.nio.ByteBuffer;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * USENIX_BinYujConYanYu25 UPSU receiver — delegates to {@link Tbz25PsuClient} (simple hash + pnMCRG + OTP union).
 */
public class Tbz25UpsuReceiver extends AbstractUpsuReceiver {
    private final Tbz25PsuClient delegate;
    private Set<ByteBuffer> receiverElementSet;

    public Tbz25UpsuReceiver(Rpc receiverRpc, Party senderParty, Tbz25UpsuConfig config) {
        super(Tbz25UpsuPtoDesc.getInstance(), receiverRpc, senderParty, config);
        delegate = new Tbz25PsuClient(receiverRpc, senderParty, config.getPsuConfig());
    }

    @Override
    public void setParallel(boolean parallel) {
        super.setParallel(parallel);
        delegate.setParallel(parallel);
    }

    @Override
    public void setTaskId(int taskId) {
        super.setTaskId(taskId);
        delegate.setTaskId(taskId);
    }

    @Override
    public void init(Set<ByteBuffer> receiverElementSet, int maxSenderElementSize, int elementByteLength)
        throws MpcAbortException {
        setInitInput(receiverElementSet, maxSenderElementSize, elementByteLength);
        this.receiverElementSet = new LinkedHashSet<>(receiverElementList);
        delegate.init(receiverElementSize, maxSenderElementSize);
    }

    @Override
    public UpsuReceiverOutput psu(int senderElementSize) throws MpcAbortException {
        setPtoInput(senderElementSize);
        PsuClientOutput clientOutput = delegate.psu(receiverElementSet, senderElementSize, elementByteLength);
        return new UpsuReceiverOutput(clientOutput.getUnion(), clientOutput.getPsiCa());
    }
}
