package edu.alibaba.libpsu.api;

import java.util.Objects;

/**
 * Immutable metadata for a registered protocol variant (SoK-style descriptor).
 */
public final class ProtocolInfo {
    private final String protocolName;
    private final String citationKey;
    private final int year;
    private final ProtocolFunctionality functionality;
    private final ProtocolFamily family;
    private final OutputModel outputModel;
    private final LibPsuSecurityModel securityModel;
    private final SetSizeMode setSizeMode;
    private final PrimitiveKind primitiveKind;
    private final ProtocolReadiness readiness;
    private final int minimumInputSetSize;
    private final boolean smallSetOptimization;
    private final boolean benchmarked;
    private final String mavenModule;
    private final String configPropertyName;

    private ProtocolInfo(Builder builder) {
        protocolName = Objects.requireNonNull(builder.protocolName);
        citationKey = builder.citationKey;
        year = builder.year;
        functionality = Objects.requireNonNull(builder.functionality);
        family = Objects.requireNonNull(builder.family);
        outputModel = Objects.requireNonNull(builder.outputModel);
        securityModel = Objects.requireNonNull(builder.securityModel);
        setSizeMode = Objects.requireNonNull(builder.setSizeMode);
        primitiveKind = Objects.requireNonNull(builder.primitiveKind);
        readiness = Objects.requireNonNull(builder.readiness);
        minimumInputSetSize = builder.minimumInputSetSize;
        smallSetOptimization = builder.smallSetOptimization;
        benchmarked = builder.benchmarked;
        mavenModule = builder.mavenModule;
        configPropertyName = builder.configPropertyName;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public String getCitationKey() {
        return citationKey;
    }

    public int getYear() {
        return year;
    }

    public ProtocolFunctionality getFunctionality() {
        return functionality;
    }

    public ProtocolFamily getFamily() {
        return family;
    }

    public OutputModel getOutputModel() {
        return outputModel;
    }

    public LibPsuSecurityModel getSecurityModel() {
        return securityModel;
    }

    public SetSizeMode getSetSizeMode() {
        return setSizeMode;
    }

    public PrimitiveKind getPrimitiveKind() {
        return primitiveKind;
    }

    public ProtocolReadiness getReadiness() {
        return readiness;
    }

    public int getMinimumInputSetSize() {
        return minimumInputSetSize;
    }

    public boolean isDefaultEnabled() {
        return benchmarked && readiness == ProtocolReadiness.STABLE;
    }

    public boolean isSmallSetOptimization() {
        return smallSetOptimization;
    }

    public boolean isBenchmarked() {
        return benchmarked;
    }

    public String getMavenModule() {
        return mavenModule;
    }

    public String getConfigPropertyName() {
        return configPropertyName;
    }

    public static Builder builder(String protocolName) {
        return new Builder(protocolName);
    }

    public static final class Builder {
        private final String protocolName;
        private String citationKey = "";
        private int year;
        private ProtocolFunctionality functionality;
        private ProtocolFamily family;
        private OutputModel outputModel;
        private LibPsuSecurityModel securityModel;
        private SetSizeMode setSizeMode;
        private PrimitiveKind primitiveKind;
        private ProtocolReadiness readiness = ProtocolReadiness.STABLE;
        private int minimumInputSetSize;
        private boolean smallSetOptimization;
        private boolean benchmarked = true;
        private String mavenModule;
        private String configPropertyName;

        private Builder(String protocolName) {
            this.protocolName = protocolName;
        }

        public Builder citationKey(String citationKey) {
            this.citationKey = citationKey;
            return this;
        }

        public Builder year(int year) {
            this.year = year;
            return this;
        }

        public Builder functionality(ProtocolFunctionality functionality) {
            this.functionality = functionality;
            return this;
        }

        public Builder family(ProtocolFamily family) {
            this.family = family;
            return this;
        }

        public Builder outputModel(OutputModel outputModel) {
            this.outputModel = outputModel;
            return this;
        }

        public Builder securityModel(LibPsuSecurityModel securityModel) {
            this.securityModel = securityModel;
            return this;
        }

        public Builder setSizeMode(SetSizeMode setSizeMode) {
            this.setSizeMode = setSizeMode;
            return this;
        }

        public Builder primitiveKind(PrimitiveKind primitiveKind) {
            this.primitiveKind = primitiveKind;
            return this;
        }

        public Builder readiness(ProtocolReadiness readiness) {
            this.readiness = readiness;
            return this;
        }

        public Builder minimumInputSetSize(int minimumInputSetSize) {
            if (minimumInputSetSize < 0) {
                throw new IllegalArgumentException("minimumInputSetSize must be non-negative");
            }
            this.minimumInputSetSize = minimumInputSetSize;
            return this;
        }

        public Builder smallSetOptimization(boolean smallSetOptimization) {
            this.smallSetOptimization = smallSetOptimization;
            return this;
        }

        public Builder benchmarked(boolean benchmarked) {
            this.benchmarked = benchmarked;
            return this;
        }

        public Builder mavenModule(String mavenModule) {
            this.mavenModule = mavenModule;
            return this;
        }

        public Builder configPropertyName(String configPropertyName) {
            this.configPropertyName = configPropertyName;
            return this;
        }

        public ProtocolInfo build() {
            return new ProtocolInfo(this);
        }
    }
}
