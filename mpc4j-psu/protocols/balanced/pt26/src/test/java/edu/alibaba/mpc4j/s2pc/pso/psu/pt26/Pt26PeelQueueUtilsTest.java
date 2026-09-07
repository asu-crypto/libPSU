package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests EUROCRYPT_PisTri26 peel-frontier queue behavior.
 */
public class Pt26PeelQueueUtilsTest {
    @Test
    public void testAllBinsFirstRoundCanSpanMultipleBatches() {
        int subtableSize = Pt26PeelQueueUtils.MAX_PEEL_BATCH / Pt26IbltParams.DEFAULT_K + 17;
        Pt26IbltParams params = new Pt26IbltParams(
            Pt26IbltParams.DEFAULT_K,
            subtableSize,
            Pt26Zm.modulusFor(9),
            9,
            hashKeys(Pt26IbltParams.DEFAULT_K)
        );

        Set<Pt26BinIndex> queue = Pt26Iblt.allBins(params);
        Assert.assertEquals(params.totalBins(), queue.size());
        Assert.assertTrue(queue.size() > Pt26PeelQueueUtils.MAX_PEEL_BATCH);

        Set<Pt26BinIndex> drained = new HashSet<Pt26BinIndex>();
        int batches = 0;
        while (!queue.isEmpty()) {
            List<Pt26BinIndex> batch = Pt26PeelQueueUtils.takePeelBatch(queue);
            Assert.assertFalse(batch.isEmpty());
            Assert.assertTrue(batch.size() <= Pt26PeelQueueUtils.MAX_PEEL_BATCH);
            drained.addAll(batch);
            batches++;
        }

        Assert.assertTrue(batches > 1);
        Assert.assertEquals(params.totalBins(), drained.size());
    }

    private static byte[][] hashKeys(int k) {
        byte[][] keys = new byte[k][16];
        for (int i = 0; i < k; i++) {
            keys[i][0] = (byte) i;
        }
        return keys;
    }
}
