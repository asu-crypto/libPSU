package edu.alibaba.libpsu.factory;

import edu.alibaba.libpsu.api.ProtocolInfo;
import edu.alibaba.libpsu.api.ProtocolNames;
import edu.alibaba.libpsu.api.ProtocolMetadataRegistry;
import edu.alibaba.libpsu.spi.ProtocolDescriptor;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.PsuPaperFidelity;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.PsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Central registry: benchmark config name → type enum → config → runtime party.
 */
public final class ProtocolRegistry {
    private static final Map<String, ProtocolDescriptor> PSU_DESCRIPTORS_BY_NAME = createPsuDescriptors();

    private ProtocolRegistry() {
    }

    public static Optional<ProtocolDescriptor> findDescriptor(String protocolName) {
        if (protocolName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PSU_DESCRIPTORS_BY_NAME.get(ProtocolNames.canonicalize(protocolName)));
    }

    public static Collection<ProtocolDescriptor> allPsuDescriptors() {
        return Collections.unmodifiableCollection(new LinkedHashSet<>(PSU_DESCRIPTORS_BY_NAME.values()));
    }

    public static Collection<ProtocolDescriptor> defaultPsuDescriptors() {
        Collection<ProtocolDescriptor> defaultDescriptors = new LinkedHashSet<>();
        for (ProtocolDescriptor descriptor : allPsuDescriptors()) {
            if (descriptor.isDefaultEnabled()) {
                defaultDescriptors.add(descriptor);
            }
        }
        return Collections.unmodifiableCollection(defaultDescriptors);
    }

    public static PsuType resolvePsuType(String configName) {
        return PsuPaperFidelity.resolvePsuType(configName);
    }

    public static PsuConfig createPsuConfig(Properties properties) {
        return PsuConfigUtils.createConfig(properties);
    }

    public static PsuConfig createPsuConfig(String protocolName, Properties properties) {
        ProtocolDescriptor descriptor = findDescriptor(protocolName)
            .orElseThrow(() -> new IllegalArgumentException("Unknown PSU protocol: " + protocolName));
        MultiPartyPtoConfig config = descriptor.createConfig(properties);
        if (!(config instanceof PsuConfig)) {
            throw new IllegalStateException(protocolName + " descriptor did not produce a PSU config");
        }
        return (PsuConfig) config;
    }

    public static PsuServer createPsuServer(Rpc serverRpc, Party clientParty, PsuConfig config) {
        return PsuFactory.createServer(serverRpc, clientParty, config);
    }

    public static PsuClient createPsuClient(Rpc clientRpc, Party serverParty, PsuConfig config) {
        return PsuFactory.createClient(clientRpc, serverParty, config);
    }

    public static boolean isKnownBenchmarkProtocol(String name) {
        return ProtocolMetadataRegistry.isKnownBenchmarkName(name);
    }

    private static Map<String, ProtocolDescriptor> createPsuDescriptors() {
        Map<String, ProtocolDescriptor> descriptors = new LinkedHashMap<>();
        register(descriptors, ProtocolNames.AC_KRTW19, "edu.alibaba.mpc4j.s2pc.pso.psu.krtw19.Krtw19PsuConfig", "KRTW19");
        register(descriptors, ProtocolNames.PKC_GMRSS21, "edu.alibaba.mpc4j.s2pc.pso.psu.gmr21.Gmr21PsuConfig", "GMR21");
        register(descriptors, ProtocolNames.USENIX_JSZDG22, "edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfcPsuConfig", "JSZ22_SFC");
        register(descriptors, ProtocolNames.USENIX_JSZDG22_SFS, "edu.alibaba.mpc4j.s2pc.pso.psu.jsz22.Jsz22SfsPsuConfig", "JSZ22_SFS");
        register(descriptors, ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_PKE, "edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23PkePsuConfig", "ZCL23_PKE");
        register(descriptors, ProtocolNames.USENIX_CON_YU_WEIMIN_DON23_SKE, "edu.alibaba.mpc4j.s2pc.pso.psu.zcl23.Zcl23SkePsuConfig", "ZCL23_SKE");
        register(descriptors, ProtocolNames.PKC_CHE_ZHA_ZHA24, "edu.alibaba.mpc4j.s2pc.pso.psu.czz24.Czz24CwOprfPsuConfig", "CZZ24_CW_OPRF");
        register(descriptors, ProtocolNames.ASIACCS_CSSW25, "edu.alibaba.mpc4j.s2pc.pso.psu.css25.Css25PsuConfig", "CSS25");
        register(descriptors, ProtocolNames.EUROCRYPT_PIS_TRI26, "edu.alibaba.mpc4j.s2pc.pso.psu.pt26.Pt26PsuConfig", "PT26");
        register(descriptors, ProtocolNames.ACISP_DAV_CID17, "edu.alibaba.mpc4j.s2pc.pso.psu.dc17.Dc17PsuConfig", "DC17");
        register(descriptors, ProtocolNames.ACNS_FRIKKEN07, "edu.alibaba.mpc4j.s2pc.pso.psu.f07.F07PsuConfig", "F07");
        register(descriptors, ProtocolNames.USENIX_BIN_YUJ_CON_YAN_YU25, "edu.alibaba.mpc4j.s2pc.pso.psu.tbz25.Tbz25PsuConfig", "TBZ25");
        register(descriptors, ProtocolNames.USENIX_HAO_WAN26, "edu.alibaba.mpc4j.s2pc.pso.psu.haowan2026.HaoWan2026PsuConfig", "HAO_WAN2026");
        register(descriptors, ProtocolNames.USENIX_YAN_SHI_HON_DAW24, "edu.alibaba.mpc4j.s2pc.pso.psu.jszg24.Jszg24BecrgPsuConfig", "JSZG24_BECRG_PSU");
        register(descriptors, ProtocolNames.EUROCRYPT_PU_GAO_TRI26, "edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided.Pgt26_2mPsuConfig", "PGT26_2M");
        register(descriptors, ProtocolNames.OURS, "edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcElligatorPsuConfig", "SMALL_EC_ELLIGATOR_PSU");
        return Collections.unmodifiableMap(descriptors);
    }

    private static void register(
        Map<String, ProtocolDescriptor> descriptors, String protocolName, String configClassName, String... aliases
    ) {
        ProtocolDescriptor descriptor = new LegacyPsuProtocolDescriptor(protocolName, configClassName, aliases);
        descriptors.put(protocolName, descriptor);
        for (String alias : aliases) {
            descriptors.put(alias, descriptor);
        }
    }

    private static final class LegacyPsuProtocolDescriptor implements ProtocolDescriptor {
        private final String protocolName;
        private final String configClassName;
        private final Set<String> aliases;

        private LegacyPsuProtocolDescriptor(String protocolName, String configClassName, String... aliases) {
            this.protocolName = Objects.requireNonNull(protocolName, "protocolName");
            this.configClassName = Objects.requireNonNull(configClassName, "configClassName");
            Set<String> names = new LinkedHashSet<>();
            names.add(protocolName);
            Collections.addAll(names, aliases);
            this.aliases = Collections.unmodifiableSet(names);
        }

        @Override
        public String getProtocolName() {
            return protocolName;
        }

        @Override
        public ProtocolInfo getProtocolInfo() {
            return ProtocolMetadataRegistry.findByName(protocolName)
                .orElseThrow(() -> new IllegalStateException("Missing protocol metadata: " + protocolName));
        }

        @Override
        public Collection<String> getProtocolAliases() {
            return aliases;
        }

        @Override
        public Class<? extends MultiPartyPtoConfig> getConfigType() {
            try {
                return Class.forName(configClassName).asSubclass(MultiPartyPtoConfig.class);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Missing config class for " + protocolName + ": " + configClassName, e);
            }
        }

        @Override
        public MultiPartyPtoConfig createConfig(Properties properties) {
            Properties merged = new Properties();
            if (properties != null) {
                merged.putAll(properties);
            }
            merged.setProperty(PsuConfigUtils.PSU_PTO_NAME_KEY, protocolName);
            return PsuConfigUtils.createConfig(merged);
        }
    }
}
