package edu.alibaba.libpsu.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stable library-level options for selecting and running a PSU protocol.
 */
public final class PsuRunOptions {
    private final String protocolName;
    private final boolean parallel;
    private final boolean silent;
    private final Map<String, String> properties;

    private PsuRunOptions(Builder builder) {
        protocolName = Objects.requireNonNull(builder.protocolName, "protocolName").trim();
        if (protocolName.isEmpty()) {
            throw new IllegalArgumentException("protocolName must not be empty");
        }
        parallel = builder.parallel;
        silent = builder.silent;
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
    }

    public static Builder builder(String protocolName) {
        return new Builder(protocolName);
    }

    public String protocolName() {
        return protocolName;
    }

    public boolean parallel() {
        return parallel;
    }

    public boolean silent() {
        return silent;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public static final class Builder {
        private final String protocolName;
        private boolean parallel;
        private boolean silent = true;
        private final Map<String, String> properties = new LinkedHashMap<>();

        private Builder(String protocolName) {
            this.protocolName = protocolName;
        }

        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        public Builder property(String key, String value) {
            properties.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder properties(Map<String, String> values) {
            Objects.requireNonNull(values, "values");
            for (Map.Entry<String, String> entry : values.entrySet()) {
                property(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public PsuRunOptions build() {
            return new PsuRunOptions(this);
        }
    }
}
