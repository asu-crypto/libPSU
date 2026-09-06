package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf;

import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Ideal PRF F(k, s) for {@code 2^5} tests only (HN12 §2); not for production malicious mode.
 */
public final class Hn12IdealPrf {
    private Hn12IdealPrf() {
    }

    public static byte[] eval(byte[] key, BigInteger s, String purpose) {
        Hash hash = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, 32);
        byte[] digest = hash.digestToBytes(concat(key, s.toByteArray(), purpose.getBytes(StandardCharsets.UTF_8)));
        byte[] out = new byte[128];
        System.arraycopy(digest, 0, out, 0, 32);
        for (int slot = 1; slot < 3; slot++) {
            byte[] slotDigest = hash.digestToBytes(concat(digest, new byte[] {(byte) slot}, purpose.getBytes(StandardCharsets.UTF_8)));
            System.arraycopy(slotDigest, 0, out, slot * 32, 32);
        }
        byte[] sBytes = s.toByteArray();
        System.arraycopy(sBytes, 0, out, 96, Math.min(32, sBytes.length));
        return out;
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
