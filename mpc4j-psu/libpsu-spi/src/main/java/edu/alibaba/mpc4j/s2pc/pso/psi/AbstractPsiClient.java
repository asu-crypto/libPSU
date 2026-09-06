package edu.alibaba.mpc4j.s2pc.pso.psi;

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
 * PSI client base class.
 */
public abstract class AbstractPsiClient extends AbstractTwoPartyPto implements PsiClient {
    protected int maxClientElementSize;
    protected int maxServerElementSize;
    protected ArrayList<ByteBuffer> clientElementArrayList;
    protected int clientElementSize;
    protected int serverElementSize;
    protected int elementByteLength;
    protected ByteBuffer botElementByteBuffer;

    protected AbstractPsiClient(PtoDesc ptoDesc, Rpc clientRpc, Party serverParty, PsiConfig config) {
        super(ptoDesc, clientRpc, serverParty, config);
    }

    protected void setInitInput(int maxClientElementSize, int maxServerElementSize) {
        MathPreconditions.checkGreater("maxClientElementSize", maxClientElementSize, 1);
        this.maxClientElementSize = maxClientElementSize;
        MathPreconditions.checkGreater("maxServerElementSize", maxServerElementSize, 1);
        this.maxServerElementSize = maxServerElementSize;
        initState();
    }

    protected void setPtoInput(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength) {
        checkInitialized();
        SetElementUtils.validateProtocolElementByteLength(elementByteLength);
        this.elementByteLength = elementByteLength;
        botElementByteBuffer = SetElementUtils.createBotElement(elementByteLength);
        clientElementArrayList = SetElementUtils.normalizeProtocolElements(
            clientElementSet, elementByteLength, botElementByteBuffer, "client element"
        );
        clientElementSize = clientElementArrayList.size();
        SetElementUtils.checkElementSizeInRange("clientElementSize", clientElementSize, maxClientElementSize, 2);
        MathPreconditions.checkGreater("serverElementSize", serverElementSize, 1);
        MathPreconditions.checkLessOrEqual("serverElementSize", serverElementSize, maxServerElementSize);
        this.serverElementSize = serverElementSize;
        extraInfo++;
    }
}
