package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.core.set.SetElementUtils;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;

/**
 * abstract Offline/Online PSU client.
 *
 * @author Feng Han
 * @date 2024/12/9
 */
public abstract class AbstractOoPsuClient extends AbstractPsuClient implements OoPsuClient {

    protected AbstractOoPsuClient(PtoDesc ptoDesc, Rpc clientRpc, Party serverParty, OoPsuConfig config) {
        super(ptoDesc, clientRpc, serverParty, config);
    }

    protected void checkPrecomputeInput(int clientElementSize, int serverElementSize, int elementByteLength) {
        checkInitialized();
        SetElementUtils.checkElementSizeInRange("clientElementSize", clientElementSize, maxClientElementSize, 2);
        SetElementUtils.checkElementSizeInRange("serverElementSize", serverElementSize, maxServerElementSize, 2);
        SetElementUtils.validateProtocolElementByteLength(elementByteLength);
    }
}
