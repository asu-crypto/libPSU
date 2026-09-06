package edu.alibaba.mpc4j.s2pc.pso.psu.dc17;

import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory.HashType;
import edu.alibaba.mpc4j.common.tool.utils.IntUtils;

/**
 * ACISP_DavCid17 Bloom-filter hashing: k independent indices in [0, B).
 * <p>
 * We model "public hash functions" as SHAKE-based hashes keyed by public seeds.
 * </p>
 */
class Dc17BloomFilterHash {
    private static final int INDEX_BYTE_LENGTH = Integer.BYTES;
    private final int binNum;
    private final Hash[] hashes;

    Dc17BloomFilterHash(int binNum, byte[][] seeds) {
        this.binNum = binNum;
        hashes = new Hash[seeds.length];
        for (int i = 0; i < seeds.length; i++) {
            final byte[] seed = seeds[i];
            final Hash h = HashFactory.createInstance(HashType.BC_SHAKE_128, INDEX_BYTE_LENGTH);
            // emulate a keyed hash by prefixing the seed in each digest
            hashes[i] = new Hash() {
                @Override
                public byte[] digestToBytes(byte[] message) {
                    byte[] in = new byte[seed.length + message.length];
                    System.arraycopy(seed, 0, in, 0, seed.length);
                    System.arraycopy(message, 0, in, seed.length, message.length);
                    return h.digestToBytes(in);
                }

                @Override
                public int getOutputByteLength() {
                    return h.getOutputByteLength();
                }

                @Override
                public HashType getHashType() {
                    return h.getHashType();
                }
            };
        }
    }

    int k() {
        return hashes.length;
    }

    int[] positions(byte[] element) {
        int[] pos = new int[hashes.length];
        for (int i = 0; i < hashes.length; i++) {
            byte[] d = hashes[i].digestToBytes(element);
            int v = IntUtils.byteArrayToInt(d);
            // Java % can be negative
            int p = Math.floorMod(v, binNum);
            pos[i] = p;
        }
        return pos;
    }
}

