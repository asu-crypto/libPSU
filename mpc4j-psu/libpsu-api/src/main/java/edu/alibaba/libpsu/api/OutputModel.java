package edu.alibaba.libpsu.api;

/**
 * Who learns the result and in what form.
 */
public enum OutputModel {
    /** Receiver (client) learns union or intersection; sender learns nothing extra. */
    ONE_SIDED,
    /** Both parties learn the union (or related output). */
    TWO_SIDED,
    /** All-or-nothing delivery of the final message. */
    AON,
    /** Malicious security with explicit two-sided union output. */
    MALICIOUS_TWO_SIDED,
    /** Sender-only or auxiliary output (e.g. BA12, Finished). */
    SENDER_AUX,
}
