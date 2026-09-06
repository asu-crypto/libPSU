package edu.alibaba.mpc4j.psu.common;

import edu.alibaba.mpc4j.common.rpc.Rpc;
import org.slf4j.Logger;

/**
 * Optional debug lines for fair-benchmark OT/COT accounting (Init vs Pto send bytes).
 * Does not change protocol behavior; logs only when the host PTO calls these helpers.
 */
public final class OtBenchmarkMetrics {
    private OtBenchmarkMetrics() {
        // empty
    }

    public static long sendBytesBaseline(Rpc rpc) {
        return rpc.getSendByteLength();
    }

    public static long sendBytesDelta(Rpc rpc, long baselineSendBytes) {
        return rpc.getSendByteLength() - baselineSendBytes;
    }

    /**
     * Logs a single {@code OT_BASE_DEBUG} line (INFO). {@code baseOtInitMs} is included inside
     * {@code cotInitMs} when Core COT init runs base OT inline (ALSZ13 / IKNP / Roy22, etc.).
     */
    public static void logInit(
        Logger logger, String protocol, long initTotalMs, long membershipInitMs, long cotInitMs,
        long initSendBytes
    ) {
        logger.info(
            "OT_BASE_DEBUG protocol={} phase=init initTotalMs={} membershipInitMs={} cotInitMs={} "
                + "baseOtInitMs=nested-in-cotInit initSendBytes={}",
            protocol, initTotalMs, membershipInitMs, cotInitMs, initSendBytes
        );
    }

    public static void logPto(
        Logger logger, String protocol, long ptoTotalMs, long membershipMs, long otOnlineMs,
        long payloadDeliveryMs, long outputMs, long ptoSendBytes
    ) {
        logger.info(
            "OT_BASE_DEBUG protocol={} phase=pto ptoTotalMs={} membershipMs={} otOnlineMs={} "
                + "payloadDeliveryMs={} outputMs={} ptoSendBytes={}",
            protocol, ptoTotalMs, membershipMs, otOnlineMs, payloadDeliveryMs, outputMs, ptoSendBytes
        );
    }
}
