package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.libpsu.core.set.SetElementUtils;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * abstract UPSU receiver.
 *
 * @author Liqiang Peng
 * @date 2024/3/7
 */
public abstract class AbstractUpsuReceiver extends AbstractTwoPartyPto implements UpsuReceiver {
    /**
     * max sender element size
     */
    protected int maxSenderElementSize;
    /**
     * receiver element list
     */
    protected List<ByteBuffer> receiverElementList;
    /**
     * sender element size
     */
    protected int senderElementSize;
    /**
     * receiver element size
     */
    protected int receiverElementSize;
    /**
     * bot element bytebuffer
     */
    protected ByteBuffer botElementByteBuffer;
    /**
     * element byte length
     */
    protected int elementByteLength;

    protected AbstractUpsuReceiver(PtoDesc ptoDesc, Rpc receiverRpc, Party senderParty, UpsuConfig config) {
        super(ptoDesc, receiverRpc, senderParty, config);
    }

    protected void setInitInput(Set<ByteBuffer> receiverElementSet, int maxSenderElementSize, int elementByteLength) {
        MathPreconditions.checkPositive("max sender element size", maxSenderElementSize);
        this.maxSenderElementSize = maxSenderElementSize;
        SetElementUtils.validateProtocolElementByteLength(elementByteLength);
        this.botElementByteBuffer = SetElementUtils.createBotElement(elementByteLength);
        this.receiverElementList = SetElementUtils.normalizeProtocolElements(
            receiverElementSet, elementByteLength, botElementByteBuffer, "receiver element"
        );
        this.receiverElementSize = receiverElementList.size();
        MathPreconditions.checkPositive("max receiver element size", receiverElementSize);
        this.elementByteLength = elementByteLength;
        initState();
    }

    protected void setPtoInput(int senderElementSize) {
        checkInitialized();
        MathPreconditions.checkPositiveInRangeClosed("sender element size", senderElementSize, maxSenderElementSize);
        this.senderElementSize = senderElementSize;
        extraInfo++;
    }
}
