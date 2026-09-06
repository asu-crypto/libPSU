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
    /**
     * Client learns the union while the server additionally learns a membership /
     * difference pattern (Small-EC/Elligator leakage baseline — not standard one-sided PSU).
     */
    LEAKAGE_BASELINE,
}
