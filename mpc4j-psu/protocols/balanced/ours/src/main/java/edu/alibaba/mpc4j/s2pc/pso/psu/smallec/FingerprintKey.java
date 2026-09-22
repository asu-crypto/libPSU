package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import java.util.Arrays;

/**
 * Compact set key for truncated W / u' fingerprints.
 */
final class FingerprintKey {
    private final byte[] bytes;

    FingerprintKey(byte[] bytes) {
        this.bytes = bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FingerprintKey that && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
