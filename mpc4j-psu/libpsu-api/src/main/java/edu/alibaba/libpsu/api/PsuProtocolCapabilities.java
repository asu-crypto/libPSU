package edu.alibaba.libpsu.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable capability snapshot for a PSU protocol, derived from {@link ProtocolInfo}.
 */
public record PsuProtocolCapabilities(
    String protocolId,
    OutputModel outputModel,
    LibPsuSecurityModel securityModel,
    int minimumInputSetSize
) {
    public PsuProtocolCapabilities {
        Objects.requireNonNull(protocolId, "protocolId");
        Objects.requireNonNull(outputModel, "outputModel");
        Objects.requireNonNull(securityModel, "securityModel");
    }

    /**
     * Both parties are intended to learn the union (including malicious two-sided).
     */
    public boolean isTwoSided() {
        return OutputModel.isTwoSided(outputModel);
    }

    public static PsuProtocolCapabilities from(ProtocolInfo info) {
        Objects.requireNonNull(info, "info");
        return new PsuProtocolCapabilities(
            info.getProtocolName(),
            info.getOutputModel(),
            info.getSecurityModel(),
            info.getMinimumInputSetSize()
        );
    }

    public static Optional<PsuProtocolCapabilities> findPsu(String protocolNameOrAlias) {
        return ProtocolMetadataRegistry.findByNameAndFunctionality(
                protocolNameOrAlias, ProtocolFunctionality.PSU
            )
            .or(() -> ProtocolMetadataRegistry.findByName(protocolNameOrAlias)
                .filter(info -> info.getFunctionality() == ProtocolFunctionality.PSU))
            .map(PsuProtocolCapabilities::from);
    }
}
