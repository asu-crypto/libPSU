package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.balanced;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HN12/FNP balanced allocation: two-choice hashing with less-occupied bin (Protocol 5, Step 2).
 */
public final class Hn12BalancedAllocation {
    private final int binCount;
    private final int maxBinSize;
    private final byte[] seed0;
    private final byte[] seed1;

    public Hn12BalancedAllocation(int binCount, int maxBinSize, byte[] seed0, byte[] seed1) {
        MathPreconditions.checkGreater("binCount", binCount, 0);
        MathPreconditions.checkGreater("maxBinSize", maxBinSize, 0);
        this.binCount = binCount;
        this.maxBinSize = maxBinSize;
        this.seed0 = Arrays.copyOf(seed0, seed0.length);
        this.seed1 = Arrays.copyOf(seed1, seed1.length);
    }

    public static int recommendBinCount(int setSize) {
        int b = Math.max(4, (int) Math.ceil(setSize / 4.0));
        return Integer.highestOneBit(b) << 1;
    }

    public static int recommendMaxBinSize(int setSize, int binCount) {
        return Math.max(4, (int) Math.ceil(2.0 * setSize / binCount) + 2);
    }

    public int getBinCount() {
        return binCount;
    }

    public int getMaxBinSize() {
        return maxBinSize;
    }

    public byte[] getSeed0() {
        return Arrays.copyOf(seed0, seed0.length);
    }

    public byte[] getSeed1() {
        return Arrays.copyOf(seed1, seed1.length);
    }

    public int hash0(BigInteger element) {
        return hashToBin(seed0, element);
    }

    public int hash1(BigInteger element) {
        return hashToBin(seed1, element);
    }

    private int hashToBin(byte[] seed, BigInteger element) {
        Hash hash = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, 32);
        byte[] d = hash.digestToBytes(concat(seed, element.toByteArray(), new byte[] {1}));
        int v = ((d[0] & 0xFF) << 24) | ((d[1] & 0xFF) << 16) | ((d[2] & 0xFF) << 8) | (d[3] & 0xFF);
        return Math.floorMod(v, binCount);
    }

    /**
     * Place elements into bins; abort via exception on overflow.
     */
    @SuppressWarnings("unchecked")
    public List<BigInteger>[] allocate(List<BigInteger> elements) {
        List<BigInteger>[] bins = new List[binCount];
        int[] loads = new int[binCount];
        for (int i = 0; i < binCount; i++) {
            bins[i] = new ArrayList<>();
        }
        for (BigInteger x : elements) {
            int b0 = hash0(x);
            int b1 = hash1(x);
            int chosen;
            if (loads[b0] < loads[b1]) {
                chosen = b0;
            } else if (loads[b1] < loads[b0]) {
                chosen = b1;
            } else {
                chosen = b0 <= b1 ? b0 : b1;
            }
            bins[chosen].add(x);
            loads[chosen]++;
            if (loads[chosen] > maxBinSize) {
                throw new IllegalStateException("bin overflow at bin " + chosen + ", load=" + loads[chosen]);
            }
        }
        return bins;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
