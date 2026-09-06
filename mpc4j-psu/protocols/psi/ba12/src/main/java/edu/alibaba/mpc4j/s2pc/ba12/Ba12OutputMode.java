package edu.alibaba.mpc4j.s2pc.ba12;

/**
 * BA12 output layout mode.
 */
public enum Ba12OutputMode {
    /**
     * Fixed-length shared array; zeros mark erased slots (Protocol default).
     */
    LENGTH_HIDING,
    /**
     * Output size may be revealed after sort/compaction (Protocol §length-preserving).
     */
    LENGTH_PRESERVING,
}
