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
 * abstract UPSU sender.
 *
 * @author Liqiang Peng
 * @date 2024/3/7
 */
public abstract class AbstractUpsuSender extends AbstractTwoPartyPto implements UpsuSender {
    /**
     * max sender element size
     */
    protected int maxSenderElementSize;
    /**
     * sender element list
     */
    protected List<ByteBuffer> senderElementList;
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

    protected AbstractUpsuSender(PtoDesc ptoDesc, Rpc senderRpc, Party receiverParty, UpsuConfig config) {
        super(ptoDesc, senderRpc, receiverParty, config);
    }

    protected void setInitInput(int maxSenderElementSize, int receiverElementSize) {
        MathPreconditions.checkPositive("max sender element size", maxSenderElementSize);
        this.maxSenderElementSize = maxSenderElementSize;
        MathPreconditions.checkPositive("receiver element size", receiverElementSize);
        this.receiverElementSize = receiverElementSize;
        initState();
    }

    protected void setPtoInput(Set<ByteBuffer> senderElementSet, int elementByteLength) {
        checkInitialized();
        SetElementUtils.validateProtocolElementByteLength(elementByteLength);
        this.botElementByteBuffer = SetElementUtils.createBotElement(elementByteLength);
        this.senderElementList = SetElementUtils.normalizeProtocolElements(
            senderElementSet, elementByteLength, botElementByteBuffer, "sender element"
        );
        this.senderElementSize = senderElementList.size();
        MathPreconditions.checkPositiveInRangeClosed("sender element size", senderElementSize, maxSenderElementSize);
        this.elementByteLength = elementByteLength;
        extraInfo++;
    }
}
