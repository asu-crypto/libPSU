package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.core.set.SetElementUtils;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;

/**
 * abstract Offline/Online PSU server.
 *
 * @author Feng Han
 * @date 2024/12/9
 */
public abstract class AbstractOoPsuServer extends AbstractPsuServer implements OoPsuServer {

    protected AbstractOoPsuServer(PtoDesc ptoDesc, Rpc serverRpc, Party clientParty, OoPsuConfig config) {
        super(ptoDesc, serverRpc, clientParty, config);
    }

    protected void checkPrecomputeInput(int serverElementSize, int clientElementSize, int elementByteLength) {
        checkInitialized();
        SetElementUtils.checkElementSizeInRange("serverElementSize", serverElementSize, maxServerElementSize, 2);
        SetElementUtils.checkElementSizeInRange("clientElementSize", clientElementSize, maxClientElementSize, 2);
        SetElementUtils.validateProtocolElementByteLength(elementByteLength);
    }
}
