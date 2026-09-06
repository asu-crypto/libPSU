package edu.alibaba.mpc4j.psu.plugin.mqrpmt.gmr21;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.psu.api.PluginId;
import edu.alibaba.mpc4j.psu.api.plugin.MembershipInput;
import edu.alibaba.mpc4j.psu.api.plugin.MembershipOutput;
import edu.alibaba.mpc4j.psu.api.plugin.PsuMembershipPlugin;
import edu.alibaba.mpc4j.psu.api.plugin.PsuPluginInit;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtClient;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtConfig;

/**
 * Membership phase via PKC_GMRSS21 mqRPMT (engineering proxy).
 */
public class Gmr21MqRpmtMembershipPlugin implements PsuMembershipPlugin {
    private final Gmr21MqRpmtClient mqRpmtClient;
    private PsuPluginInit init;

    public Gmr21MqRpmtMembershipPlugin(Rpc rpc, Party otherParty, Gmr21MqRpmtConfig config) {
        mqRpmtClient = new Gmr21MqRpmtClient(rpc, otherParty, config);
    }

    @Override
    public PluginId getPluginId() {
        return PluginId.MQRPMT_GMR21;
    }

    @Override
    public void init(PsuPluginInit init) throws MpcAbortException {
        this.init = init;
        mqRpmtClient.init(init.getMaxSmallSetSize(), init.getMaxLargeSetSize());
    }

    @Override
    public MembershipOutput runMembership(MembershipInput input) throws MpcAbortException {
        boolean[] choices = mqRpmtClient.mqRpmt(input.getSmallSet(), input.getLargeSetSize());
        return new MembershipOutput(choices);
    }

    public Gmr21MqRpmtClient getMqRpmtClient() {
        return mqRpmtClient;
    }
}
