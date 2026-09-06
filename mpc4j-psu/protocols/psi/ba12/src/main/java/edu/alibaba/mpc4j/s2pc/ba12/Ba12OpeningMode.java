package edu.alibaba.mpc4j.s2pc.ba12;

/**
 * Whether final values are opened to parties.
 */
public enum Ba12OpeningMode {
    /**
     * Parties keep secret shares only.
     */
    SHARED_OUTPUT,
    /**
     * Open after approved sort/compaction (length-preserving path).
     */
    OPENED_OUTPUT,
}
