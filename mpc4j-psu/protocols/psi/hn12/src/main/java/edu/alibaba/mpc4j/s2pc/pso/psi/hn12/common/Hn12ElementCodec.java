package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common;

import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * Maps set elements to Z_q scalars (HN12: elements from {0,1}^{p(n)}).
 */
public final class Hn12ElementCodec {
    private Hn12ElementCodec() {
    }

    public static BigInteger encodeToScalar(byte[] elementBytes, BigInteger q, byte[] domainTag) {
        Hash hash = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, 32);
        byte[] digest = hash.digestToBytes(concat(domainTag, elementBytes));
        BigInteger x = new BigInteger(1, digest).mod(q);
        if (x.signum() == 0) {
            x = BigInteger.ONE;
        }
        return x;
    }

    public static BigInteger encodeToScalar(ByteBuffer element, BigInteger q, byte[] domainTag) {
        byte[] raw = new byte[element.remaining()];
        element.duplicate().get(raw);
        return encodeToScalar(raw, q, domainTag);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
