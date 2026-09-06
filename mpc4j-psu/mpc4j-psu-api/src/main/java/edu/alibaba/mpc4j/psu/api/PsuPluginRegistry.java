package edu.alibaba.mpc4j.psu.api;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.psu.api.plugin.PsuMembershipPlugin;
import edu.alibaba.mpc4j.psu.api.plugin.PsuPlugin;
import edu.alibaba.mpc4j.psu.api.plugin.PsuUnionDeliveryPlugin;

/**
 * Factory for PSU pipeline plugins (implemented in plugin modules).
 */
public interface PsuPluginRegistry {
    <T extends PsuPlugin> T create(PluginId id, Rpc rpc, Party otherParty, Object config);

    PsuMembershipPlugin createMembership(PluginId id, Rpc rpc, Party otherParty, Object config);

    PsuUnionDeliveryPlugin createUnionDelivery(PluginId id, Rpc rpc, Party otherParty, Object config);
}
