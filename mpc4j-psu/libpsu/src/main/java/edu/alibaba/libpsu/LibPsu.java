package edu.alibaba.libpsu;

import edu.alibaba.libpsu.api.ProtocolInfo;
import edu.alibaba.libpsu.api.ProtocolMetadataRegistry;
import edu.alibaba.libpsu.api.ProtocolFunctionality;
import edu.alibaba.libpsu.api.PsuProtocolCapabilities;
import edu.alibaba.libpsu.factory.ProtocolRegistry;
import edu.alibaba.libpsu.spi.ProtocolDescriptor;
import edu.alibaba.mpc4j.common.rpc.Party;
import edu.alibaba.mpc4j.common.rpc.Rpc;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Config;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12Operation;
import edu.alibaba.mpc4j.s2pc.ba12.Ba12SetOpsParty;
import edu.alibaba.mpc4j.s2pc.pso.main.ba12.Ba12ConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psi.PsiConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.main.psu.OoPsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiClient;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiConfig;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiFactory;
import edu.alibaba.mpc4j.s2pc.pso.psi.PsiServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.OoPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.OoPsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.OoPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuTwoSidedServer;
import edu.alibaba.mpc4j.s2pc.upso.main.upsu.UpsuConfigUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuFactory;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuSender;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Single public entry point for libPSU.
 *
 * <p>The lower-level {@code libpsu-api}, {@code libpsu-spi}, {@code libpsu-core}, and
 * {@code libpsu-factory} artifacts are implementation layers. Applications should depend on
 * {@code edu.alibaba:libpsu} and use this class unless they are implementing a protocol plugin.</p>
 */
public final class LibPsu {
    private LibPsu() {
    }

    public static List<ProtocolInfo> protocols() {
        return ProtocolMetadataRegistry.allEntries();
    }

    /** Returns the registered metadata for a functionality, including experimental entries. */
    public static List<ProtocolInfo> protocols(ProtocolFunctionality functionality) {
        Objects.requireNonNull(functionality, "functionality");
        return protocols().stream().filter(info -> info.getFunctionality() == functionality).toList();
    }

    /** Resolves names within a functionality, since PSU and UPSU may share a paper name. */
    public static Optional<ProtocolInfo> findProtocol(String protocolName, ProtocolFunctionality functionality) {
        return ProtocolMetadataRegistry.findByNameAndFunctionality(protocolName, functionality);
    }

    public static Collection<ProtocolDescriptor> psuProtocols() {
        return ProtocolRegistry.allPsuDescriptors();
    }

    public static Collection<ProtocolDescriptor> defaultPsuProtocols() {
        return ProtocolRegistry.defaultPsuDescriptors();
    }

    /** Reads the existing {@code psu_pto_name} and protocol-specific configuration keys. */
    public static PsuConfig createPsuConfig(Properties properties) {
        return ProtocolRegistry.createPsuConfig(Objects.requireNonNull(properties, "properties"));
    }

    public static PsuConfig createPsuConfig(String protocolName) {
        return createPsuConfig(protocolName, new Properties());
    }

    public static PsuConfig createPsuConfig(String protocolName, Properties properties) {
        return ProtocolRegistry.createPsuConfig(protocolName, properties);
    }

    public static PsuProtocolCapabilities capabilitiesOf(PsuConfig config) {
        return ProtocolRegistry.capabilitiesOf(config);
    }

    /** Selects the factory matching the protocol's declared output disclosure. */
    public static boolean usesTwoSidedPublicFactory(PsuConfig config) {
        return ProtocolRegistry.usesTwoSidedPublicFactory(config);
    }

    public static PsuConfig createDefaultTwoSidedConfig(SecurityModel securityModel) {
        return ProtocolRegistry.createDefaultTwoSidedConfig(securityModel);
    }

    public static PsuServer createServer(Rpc rpc, Party clientParty, PsuConfig config) {
        return ProtocolRegistry.createPsuServer(rpc, clientParty, config);
    }

    public static PsuClient createClient(Rpc rpc, Party serverParty, PsuConfig config) {
        return ProtocolRegistry.createPsuClient(rpc, serverParty, config);
    }

    public static PsuTwoSidedServer createTwoSidedServer(Rpc rpc, Party clientParty, PsuConfig config) {
        return ProtocolRegistry.createTwoSidedPsuServer(rpc, clientParty, config);
    }

    public static PsuTwoSidedClient createTwoSidedClient(Rpc rpc, Party serverParty, PsuConfig config) {
        return ProtocolRegistry.createTwoSidedPsuClient(rpc, serverParty, config);
    }

    /** Reads offline/online PSU configuration, preserving its required ROSN selection. */
    public static OoPsuConfig createOoPsuConfig(Properties properties) {
        return OoPsuConfigUtils.createConfig(Objects.requireNonNull(properties, "properties"));
    }

    public static OoPsuServer createOoPsuServer(Rpc rpc, Party clientParty, OoPsuConfig config) {
        return PsuFactory.createOoPsuServer(rpc, clientParty, config);
    }

    public static OoPsuClient createOoPsuClient(Rpc rpc, Party serverParty, OoPsuConfig config) {
        return PsuFactory.createOoPsuClient(rpc, serverParty, config);
    }

    /** Reads legacy PSI configuration from {@code psi_pto_name}. */
    public static PsiConfig createPsiConfig(Properties properties) {
        return PsiConfigUtils.createConfig(Objects.requireNonNull(properties, "properties"));
    }

    public static PsiConfig createPsiConfig(String protocolName) {
        return createPsiConfig(protocolName, new Properties());
    }

    public static PsiConfig createPsiConfig(String protocolName, Properties properties) {
        return createPsiConfig(withProtocolName(properties, PsiConfigUtils.PSI_PTO_NAME_KEY, protocolName));
    }

    public static PsiServer createPsiServer(Rpc rpc, Party clientParty, PsiConfig config) {
        return PsiFactory.createServer(rpc, clientParty, config);
    }

    public static PsiClient createPsiClient(Rpc rpc, Party serverParty, PsiConfig config) {
        return PsiFactory.createClient(rpc, serverParty, config);
    }

    /** Reads {@code upsu_pto_name}, including the existing {@code psu_pto_name} fallback. */
    public static UpsuConfig createUpsuConfig(Properties properties) {
        return UpsuConfigUtils.createConfig(Objects.requireNonNull(properties, "properties"));
    }

    public static UpsuConfig createUpsuConfig(String protocolName) {
        return createUpsuConfig(protocolName, new Properties());
    }

    public static UpsuConfig createUpsuConfig(String protocolName, Properties properties) {
        return createUpsuConfig(withProtocolName(properties, UpsuConfigUtils.UPSU_PTO_NAME_KEY, protocolName));
    }

    public static UpsuSender createUpsuSender(Rpc rpc, Party receiverParty, UpsuConfig config) {
        return UpsuFactory.createSender(rpc, receiverParty, config);
    }

    public static UpsuReceiver createUpsuReceiver(Rpc rpc, Party senderParty, UpsuConfig config) {
        return UpsuFactory.createReceiver(rpc, senderParty, config);
    }

    /** Creates a BA12 configuration using the same element-width limits as the driver. */
    public static Ba12Config createBa12Config(Properties properties, int elementByteLength) {
        return Ba12ConfigUtils.createConfig(Objects.requireNonNull(properties, "properties"), elementByteLength);
    }

    public static Ba12Operation readBa12Operation(Properties properties) {
        return Ba12ConfigUtils.readOperation(Objects.requireNonNull(properties, "properties"));
    }

    public static String readBa12ProtocolName(Properties properties) {
        return Ba12ConfigUtils.readBa12PtoName(Objects.requireNonNull(properties, "properties"));
    }

    public static Ba12SetOpsParty createBa12Party(Rpc rpc, Party otherParty, Ba12Config config) {
        return new Ba12SetOpsParty(rpc, otherParty, config);
    }

    private static Properties withProtocolName(Properties properties, String key, String protocolName) {
        // Flatten inherited defaults as well as explicit keys, without mutating the caller's properties.
        Properties copy = new Properties();
        if (properties != null) {
            for (String name : properties.stringPropertyNames()) {
                copy.setProperty(name, properties.getProperty(name));
            }
        }
        copy.setProperty(key, Objects.requireNonNull(protocolName, "protocolName"));
        return copy;
    }
}
