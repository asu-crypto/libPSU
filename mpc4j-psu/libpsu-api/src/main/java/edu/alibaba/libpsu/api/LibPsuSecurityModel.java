package edu.alibaba.libpsu.api;

/**
 * Security model label for libPSU metadata (distinct from {@code edu.alibaba.mpc4j.common.rpc.desc.SecurityModel}).
 */
public enum LibPsuSecurityModel {
    SEMI_HONEST,
    AON,
    MALICIOUS,
}
