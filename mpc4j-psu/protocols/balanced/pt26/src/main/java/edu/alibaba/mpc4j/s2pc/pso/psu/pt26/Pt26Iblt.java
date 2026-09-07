package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory.HashType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invertible Bloom Lookup Table (§3): {@code cnt[k][ℓ]} and {@code sum[k][ℓ]} over Z_M.
 */
public class Pt26Iblt {
    private final Pt26IbltParams params;
    private final int[][] cnt;
    private final byte[][][] sum;

    public Pt26Iblt(Pt26IbltParams params) {
        this.params = params;
        cnt = new int[params.getK()][params.getSubtableSize()];
        sum = new byte[params.getK()][params.getSubtableSize()][];
        for (int i = 0; i < params.getK(); i++) {
            for (int j = 0; j < params.getSubtableSize(); j++) {
                sum[i][j] = Pt26Zm.zero(params);
            }
        }
    }

    public static Pt26Iblt encode(Collection<ByteBuffer> set, Pt26IbltParams params) {
        Pt26Iblt iblt = new Pt26Iblt(params);
        for (ByteBuffer element : set) {
            iblt.insert(element);
        }
        return iblt;
    }

    public void insert(ByteBuffer element) {
        byte[] encoded = Pt26Zm.encodeElement(element, params);
        for (int i = 0; i < params.getK(); i++) {
            int j = hashToBin(i, element);
            sum[i][j] = Pt26Zm.add(sum[i][j], encoded, params);
            cnt[i][j]++;
        }
    }

    public void delete(ByteBuffer element) {
        byte[] encoded = Pt26Zm.encodeElement(element, params);
        for (int i = 0; i < params.getK(); i++) {
            int j = hashToBin(i, element);
            sum[i][j] = Pt26Zm.subtract(sum[i][j], encoded, params);
            cnt[i][j]--;
        }
    }

    public void deleteSet(Collection<ByteBuffer> elements) {
        for (ByteBuffer element : elements) {
            delete(element);
        }
    }

    public int getCnt(int i, int j) {
        return cnt[i][j];
    }

    public byte[] getSum(int i, int j) {
        return sum[i][j];
    }

    public Pt26IbltParams getParams() {
        return params;
    }

    /**
     * After a successful peel cascade every local bin must satisfy {@code cnt == 0} and
     * {@code sum == 0}. Residual state means listing failure — callers must abort rather than
     * return a partial union.
     */
    public boolean isFullyPeeled() {
        for (int i = 0; i < params.getK(); i++) {
            for (int j = 0; j < params.getSubtableSize(); j++) {
                if (cnt[i][j] != 0 || !Pt26Zm.isZero(sum[i][j], params)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Plaintext union peel (Definition 2).
     */
    public static byte[] uPeel(Pt26Iblt iblt0, Pt26Iblt iblt1, int i, int j) {
        int c0 = iblt0.getCnt(i, j);
        int c1 = iblt1.getCnt(i, j);
        byte[] s0 = iblt0.getSum(i, j);
        byte[] s1 = iblt1.getSum(i, j);
        if (c0 == 1 && c1 == 0) {
            return s0;
        }
        if (c0 == 0 && c1 == 1) {
            return s1;
        }
        if (c0 == 1 && c1 == 1 && Pt26Zm.equals(s0, s1)) {
            return s0;
        }
        return null;
    }

    public int hashToBin(int hashIndex, ByteBuffer element) {
        Hash hash = HashFactory.createInstance(HashType.JDK_SHA256, CommonConstants.BLOCK_BYTE_LENGTH);
        byte[] key = params.getHashKey(hashIndex);
        byte[] elem = elementBytes(element);
        byte[] message = new byte[key.length + elem.length];
        System.arraycopy(key, 0, message, 0, key.length);
        System.arraycopy(elem, 0, message, key.length, elem.length);
        byte[] digest = hash.digestToBytes(message);
        int value = 0;
        for (int b = 0; b < Integer.BYTES; b++) {
            value = (value << 8) | (digest[b] & 0xFF);
        }
        return Math.floorMod(value, params.getSubtableSize());
    }

    public static Set<Pt26BinIndex> nextQueue(Pt26IbltParams params, Collection<ByteBuffer> peeledElements, Pt26Iblt localIblt) {
        Set<Pt26BinIndex> next = new HashSet<Pt26BinIndex>();
        for (ByteBuffer element : peeledElements) {
            for (int i = 0; i < params.getK(); i++) {
                next.add(new Pt26BinIndex(i, localIblt.hashToBin(i, element)));
            }
        }
        return next;
    }

    /**
     * All IBLT bins. EUROCRYPT_PisTri26's first secure peel round probes the whole table; later rounds only
     * revisit hash locations affected by the previously peeled elements.
     */
    public static Set<Pt26BinIndex> allBins(Pt26IbltParams params) {
        Set<Pt26BinIndex> bins = new HashSet<Pt26BinIndex>(params.totalBins());
        for (int i = 0; i < params.getK(); i++) {
            for (int j = 0; j < params.getSubtableSize(); j++) {
                bins.add(new Pt26BinIndex(i, j));
            }
        }
        return bins;
    }

    private static byte[] elementBytes(ByteBuffer element) {
        byte[] raw = new byte[element.remaining()];
        int pos = element.position();
        element.get(raw);
        element.position(pos);
        return raw;
    }

    public static List<Pt26BinIndex> sortedBins(Set<Pt26BinIndex> bins) {
        ArrayList<Pt26BinIndex> list = new ArrayList<Pt26BinIndex>(bins);
        Collections.sort(list, new Comparator<Pt26BinIndex>() {
            @Override
            public int compare(Pt26BinIndex a, Pt26BinIndex b) {
                int iCompare = Integer.compare(a.getI(), b.getI());
                if (iCompare != 0) {
                    return iCompare;
                }
                return Integer.compare(a.getJ(), b.getJ());
            }
        });
        return list;
    }

}
