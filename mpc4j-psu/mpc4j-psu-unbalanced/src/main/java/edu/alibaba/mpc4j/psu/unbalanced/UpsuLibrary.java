package edu.alibaba.mpc4j.psu.unbalanced;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuSender;

/**
 * Public entry for unbalanced UPSU protocols (delegates to {@link UpsuFactory} during migration).
 */
public final class UpsuLibrary {
    private UpsuLibrary() {
    }

    public static UpsuSender createSender(Rpc senderRpc, Party receiverParty, UpsuConfig config) {
        return UpsuFactory.createSender(senderRpc, receiverParty, config);
    }

    public static UpsuReceiver createReceiver(Rpc receiverRpc, Party senderParty, UpsuConfig config) {
        return UpsuFactory.createReceiver(receiverRpc, senderParty, config);
    }
}
