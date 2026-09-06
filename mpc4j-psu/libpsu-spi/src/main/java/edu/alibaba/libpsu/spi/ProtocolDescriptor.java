package edu.alibaba.libpsu.spi;

import edu.alibaba.libpsu.api.ProtocolInfo;
import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

/**
 * Library-facing protocol descriptor: links SoK metadata to mpc4j PTO config type.
 */
public interface ProtocolDescriptor {
    /**
     * Benchmark / config enum name (e.g. {@code Ours}).
     */
    String getProtocolName();

    ProtocolInfo getProtocolInfo();

    default Collection<String> getProtocolAliases() {
        return Collections.singleton(getProtocolName());
    }

    default boolean supportsName(String protocolName) {
        if (protocolName == null) {
            return false;
        }
        String normalized = protocolName.trim();
        for (String alias : getProtocolAliases()) {
            if (alias.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    default boolean isDefaultEnabled() {
        return getProtocolInfo().isDefaultEnabled();
    }

    /**
     * Underlying mpc4j config class for this variant.
     */
    Class<? extends MultiPartyPtoConfig> getConfigType();

    /**
     * Parses a library/benchmark property set into the underlying mpc4j config.
     */
    default MultiPartyPtoConfig createConfig(Properties properties) {
        throw new UnsupportedOperationException(getProtocolName() + " does not expose config parsing");
    }
}
