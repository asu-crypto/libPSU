package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Peel-queue helpers: cap OT batch size while preserving one logical peel round.
 */
final class Pt26PeelQueueUtils {
    /**
     * Max bins per UnionPeel OT batch (1-of-2 + 1-of-3). Full-table first rounds at 2^20
     * would allocate OT state for millions of rows and OOM; batch instead.
     */
    static final int MAX_PEEL_BATCH = 65_536;

    private Pt26PeelQueueUtils() {
        // empty
    }

    /**
     * Takes up to {@link #MAX_PEEL_BATCH} bins from {@code queue} (sorted order) for one OT batch.
     */
    static List<Pt26BinIndex> takePeelBatch(Set<Pt26BinIndex> queue) {
        List<Pt26BinIndex> sorted = Pt26Iblt.sortedBins(queue);
        int n = Math.min(MAX_PEEL_BATCH, sorted.size());
        List<Pt26BinIndex> batch = new ArrayList<>(n);
        for (int t = 0; t < n; t++) {
            Pt26BinIndex bin = sorted.get(t);
            batch.add(bin);
            queue.remove(bin);
        }
        return batch;
    }
}
