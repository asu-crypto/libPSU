package edu.alibaba.mpc4j.psu.api;

/**
 * Balanced vs unbalanced set-union scenario.
 */
public enum PsuScenario {
    /** |X| ≈ |Y|, client learns union. */
    BALANCED,
    /** |X| ≪ |Y|, receiver learns union. */
    UNBALANCED,
}
