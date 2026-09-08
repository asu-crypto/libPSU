package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.core.set.SetElementUtils;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyPto;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;

/**
 * Two-sided PSU server base (both parties learn the union).
 */
public abstract class AbstractPsuTwoSidedServer extends AbstractTwoPartyPto implements PsuTwoSidedServer {
    protected int maxClientElementSize;
    protected int maxServerElementSize;
    protected ArrayList<ByteBuffer> serverElementArrayList;
    protected int serverElementSize;
    protected int clientElementSize;
    protected int elementByteLength;
    protected ByteBuffer botElementByteBuffer;

    protected AbstractPsuTwoSidedServer(PtoDesc ptoDesc, Rpc serverRpc, Party clientParty, PsuConfig config) {
        super(ptoDesc, serverRpc, clientParty, config);
    }

    /**
     * Server-first init contract matching {@link PsuTwoSidedServer#init(int, int)} and {@code PsuMain}.
     */
    protected void setInitInput(int maxServerElementSize, int maxClientElementSize) {
        MathPreconditions.checkGreater("maxServerElementSize", maxServerElementSize, 1);
        this.maxServerElementSize = maxServerElementSize;
        MathPreconditions.checkGreater("maxClientElementSize", maxClientElementSize, 1);
        this.maxClientElementSize = maxClientElementSize;
        initState();
    }

    protected void setPtoInput(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength) {
        checkInitialized();
        SetElementUtils.validateProtocolElementByteLength(elementByteLength);
        this.elementByteLength = elementByteLength;
        botElementByteBuffer = SetElementUtils.createBotElement(elementByteLength);
        serverElementArrayList = SetElementUtils.normalizeProtocolElements(
            serverElementSet, elementByteLength, botElementByteBuffer, "server element"
        );
        serverElementSize = serverElementArrayList.size();
        SetElementUtils.checkElementSizeInRange("serverElementSize", serverElementSize, maxServerElementSize, 2);
        MathPreconditions.checkGreater("clientElementSize", clientElementSize, 1);
        MathPreconditions.checkLessOrEqual("clientElementSize", clientElementSize, maxClientElementSize);
        this.clientElementSize = clientElementSize;
        extraInfo++;
    }
}
