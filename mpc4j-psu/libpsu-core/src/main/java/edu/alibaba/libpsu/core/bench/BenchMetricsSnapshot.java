package edu.alibaba.libpsu.core.bench;

/**
 * Immutable communication and timing counters for fair benchmarks.
 */
public final class BenchMetricsSnapshot {
    private final long initTimeMs;
    private final long ptoTimeMs;
    private final long payloadBytes;
    private final long sendBytes;

    public BenchMetricsSnapshot(long initTimeMs, long ptoTimeMs, long payloadBytes, long sendBytes) {
        this.initTimeMs = initTimeMs;
        this.ptoTimeMs = ptoTimeMs;
        this.payloadBytes = payloadBytes;
        this.sendBytes = sendBytes;
    }

    public long getInitTimeMs() {
        return initTimeMs;
    }

    public long getPtoTimeMs() {
        return ptoTimeMs;
    }

    public long getPayloadBytes() {
        return payloadBytes;
    }

    public long getSendBytes() {
        return sendBytes;
    }
}
