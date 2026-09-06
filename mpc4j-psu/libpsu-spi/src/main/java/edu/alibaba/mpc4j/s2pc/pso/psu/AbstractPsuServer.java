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
 * PSU协议服务端。
 *
 * @author Weiran Liu
 * @date 2022/02/15
 */
public abstract class AbstractPsuServer extends AbstractTwoPartyPto implements PsuServer {
    /**
     * 服务端最大元素数量
     */
    protected int maxServerElementSize;
    /**
     * 客户端最大元素数量
     */
    protected int maxClientElementSize;
    /**
     * 服务端元素数组
     */
    protected ArrayList<ByteBuffer> serverElementArrayList;
    /**
     * 服务端元素数量
     */
    protected int serverElementSize;
    /**
     * 客户端元素数量
     */
    protected int clientElementSize;
    /**
     * 元素字节长度
     */
    protected int elementByteLength;
    /**
     * 特殊空元素字节缓存区
     */
    protected ByteBuffer botElementByteBuffer;

    protected AbstractPsuServer(PtoDesc ptoDesc, Rpc serverRpc, Party clientParty, PsuConfig config) {
        super(ptoDesc, serverRpc, clientParty, config);
    }

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
