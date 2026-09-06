package edu.alibaba.mpc4j.psu.balanced;

import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.psu.protocol.gmr21.Gmr21PsuPipelineClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig;

/**
 * Public entry for balanced PSU protocols.
 * <p>
 * For {@link PsuType#PKC_GMRSS21}, use {@link #usePluginPipeline()} or set
 * {@code -Dmpc4j.psu.usePluginPipeline=true} to obtain {@link Gmr21PsuPipelineClient}.
 * Other types delegate to {@link PsuFactory}.
 * </p>
 */
public final class PsuLibrary {
    private static final String PLUGIN_PIPELINE_PROPERTY = "mpc4j.psu.usePluginPipeline";

    private PsuLibrary() {
    }

    public static boolean usePluginPipeline() {
        return Boolean.getBoolean(PLUGIN_PIPELINE_PROPERTY);
    }

    public static PsuServer createServer(Rpc serverRpc, Party clientParty, PsuConfig config) {
        return PsuFactory.createServer(serverRpc, clientParty, config);
    }

    public static PsuClient createClient(Rpc clientRpc, Party serverParty, PsuConfig config) {
        if (usePluginPipeline() && config.getPtoType() == PsuType.PKC_GMRSS21 && config instanceof Gmr21PsuConfig) {
            return new Gmr21PsuPipelineClient(clientRpc, serverParty, (Gmr21PsuConfig) config);
        }
        return PsuFactory.createClient(clientRpc, serverParty, config);
    }
}
