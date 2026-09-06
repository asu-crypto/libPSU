package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.PtoFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuSender;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuSender;

/**
 * UPSU factory.
 *
 * @author Liqiang Peng
 * @date 2024/3/7
 */
public class UpsuFactory implements PtoFactory {
    /**
     * private constructor.
     */
    private UpsuFactory() {
        // empty
    }

    /**
     * create a sender.
     *
     * @param senderRpc     sender rpc.
     * @param receiverParty receiver party.
     * @param config      config.
     * @return a sender.
     */
    public static UpsuSender createSender(Rpc senderRpc, Party receiverParty, UpsuConfig config) {
        UpsuType type = config.getPtoType();
        switch (type) {
            case CCS_TCLZ23:
                return new Tcl23UpsuSender(senderRpc, receiverParty, (Tcl23UpsuConfig) config);
            case USENIX_BinYujConYanYu25:
                return new Tbz25UpsuSender(senderRpc, receiverParty, (Tbz25UpsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + UpsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }

    /**
     * create a receiver.
     *
     * @param receiverRpc receiver rpc.
     * @param senderParty sender party.
     * @param config      config.
     * @return a receiver.
     */
    public static UpsuReceiver createReceiver(Rpc receiverRpc, Party senderParty, UpsuConfig config) {
        UpsuType type = config.getPtoType();
        switch (type) {
            case CCS_TCLZ23:
                return new Tcl23UpsuReceiver(receiverRpc, senderParty, (Tcl23UpsuConfig) config);
            case USENIX_BinYujConYanYu25:
                return new Tbz25UpsuReceiver(receiverRpc, senderParty, (Tbz25UpsuConfig) config);
            default:
                throw new IllegalArgumentException("Invalid " + UpsuType.class.getSimpleName() + ": " + type.protocolId());
        }
    }
}
