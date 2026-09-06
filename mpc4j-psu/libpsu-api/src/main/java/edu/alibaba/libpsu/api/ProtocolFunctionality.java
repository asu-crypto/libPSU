package edu.alibaba.libpsu.api;

/**
 * High-level functionality of a set protocol (SoK PSU taxonomy).
 */
public enum ProtocolFunctionality {
    PSU,
    PSI,
    UPSU,
    /** BA12 secret-shared set operations (not standard two-party PSU output). */
    BA12,
}
