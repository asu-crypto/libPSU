package edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuServer;
import edu.alibaba.mpc4j.s2pc.upso.upsu.AbstractUpsuSender;

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * USENIX_BinYujConYanYu25 UPSU sender — delegates to {@link Tbz25PsuServer} (cuckoo + pnMCRG + OTP).
 */
public class Tbz25UpsuSender extends AbstractUpsuSender {
    private final Tbz25PsuServer delegate;

    public Tbz25UpsuSender(Rpc senderRpc, Party receiverParty, Tbz25UpsuConfig config) {
        super(Tbz25UpsuPtoDesc.getInstance(), senderRpc, receiverParty, config);
        delegate = new Tbz25PsuServer(senderRpc, receiverParty, config.getPsuConfig());
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
    public void init(int maxSenderElementSize, int receiverElementSize) throws MpcAbortException {
        setInitInput(maxSenderElementSize, receiverElementSize);
        delegate.init(maxSenderElementSize, receiverElementSize);
    }

    @Override
    public void psu(Set<ByteBuffer> senderElementSet, int elementByteLength) throws MpcAbortException {
        setPtoInput(senderElementSet, elementByteLength);
        delegate.psu(senderElementSet, receiverElementSize, elementByteLength);
    }
}
