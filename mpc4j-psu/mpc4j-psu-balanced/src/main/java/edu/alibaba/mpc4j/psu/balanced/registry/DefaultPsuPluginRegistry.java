package edu.alibaba.mpc4j.psu.balanced.registry;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.psu.api.PluginId;
import edu.alibaba.mpc4j.psu.api.PsuPluginRegistry;
import edu.alibaba.mpc4j.psu.api.plugin.PsuMembershipPlugin;
import edu.alibaba.mpc4j.psu.api.plugin.PsuPlugin;
import edu.alibaba.mpc4j.psu.api.plugin.PsuUnionDeliveryPlugin;
import edu.alibaba.mpc4j.psu.plugin.cot.CotXorUnionDeliveryPlugin;
import edu.alibaba.mpc4j.psu.plugin.mqrpmt.gmr21.Gmr21MqRpmtMembershipPlugin;
import edu.alibaba.mpc4j.s2pc.opf.mqrpmt.gmr21.Gmr21MqRpmtConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;

import java.nio.ByteBuffer;

/**
 * Default plugin registry for balanced PSU presets.
 */
public class DefaultPsuPluginRegistry implements PsuPluginRegistry {
    private final EnvType envType;
    private final ByteBuffer botElement;

    public DefaultPsuPluginRegistry(EnvType envType, ByteBuffer botElement) {
        this.envType = envType;
        this.botElement = botElement;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends PsuPlugin> T create(PluginId id, Rpc rpc, Party otherParty, Object config) {
        switch (id) {
            case MQRPMT_GMR21:
                return (T) new Gmr21MqRpmtMembershipPlugin(rpc, otherParty, (Gmr21MqRpmtConfig) config);
            case COT_XOR_UNION:
                return (T) new CotXorUnionDeliveryPlugin(
                    rpc, otherParty, (CoreCotConfig) config, envType, botElement
                );
            default:
                throw new IllegalArgumentException("Unsupported plugin: " + id);
        }
    }

    @Override
    public PsuMembershipPlugin createMembership(PluginId id, Rpc rpc, Party otherParty, Object config) {
        return create(id, rpc, otherParty, config);
    }

    @Override
    public PsuUnionDeliveryPlugin createUnionDelivery(PluginId id, Rpc rpc, Party otherParty, Object config) {
        return create(id, rpc, otherParty, config);
    }

    public static Gmr21PresetConfigs gmr21Preset(boolean silent) {
        return new Gmr21PresetConfigs(
            new Gmr21MqRpmtConfig.Builder(silent).build(),
            CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST)
        );
    }

    public static final class Gmr21PresetConfigs {
        public final Gmr21MqRpmtConfig mqRpmtConfig;
        public final CoreCotConfig coreCotConfig;

        public Gmr21PresetConfigs(Gmr21MqRpmtConfig mqRpmtConfig, CoreCotConfig coreCotConfig) {
            this.mqRpmtConfig = mqRpmtConfig;
            this.coreCotConfig = coreCotConfig;
        }
    }
}
