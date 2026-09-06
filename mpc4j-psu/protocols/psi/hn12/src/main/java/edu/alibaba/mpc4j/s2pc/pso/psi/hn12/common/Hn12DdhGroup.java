package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common;

import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Prime-order subgroup G of quadratic residues mod safe prime p' = 2q + 1 (HN12 §2).
 */
public final class Hn12DdhGroup {
    private final BigInteger pPrime;
    private final BigInteger q;
    private final BigInteger g;
    private final byte[] groupId;

    private Hn12DdhGroup(BigInteger pPrime, BigInteger q, BigInteger g, byte[] groupId) {
        this.pPrime = pPrime;
        this.q = q;
        this.g = g;
        this.groupId = groupId;
    }

    public static Hn12DdhGroup createForTests(int qBitLength, SecureRandom random) {
        MathPreconditions.checkGreaterOrEqual("qBitLength", qBitLength, 256);
        BigInteger q;
        BigInteger pPrime;
        do {
            q = BigInteger.probablePrime(qBitLength, random);
            pPrime = q.shiftLeft(1).add(BigInteger.ONE);
        } while (!pPrime.isProbablePrime(40));
        BigInteger g = findGenerator(pPrime, q, random);
        Hash hash = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, 32);
        byte[] groupId = hash.digestToBytes(pPrime.toByteArray());
        return new Hn12DdhGroup(pPrime, q, g, groupId);
    }

    private static BigInteger findGenerator(BigInteger pPrime, BigInteger q, SecureRandom random) {
        for (int attempt = 0; attempt < 512; attempt++) {
            BigInteger a = new BigInteger(pPrime.bitLength() - 2, random).mod(pPrime);
            if (a.signum() == 0 || a.equals(BigInteger.ONE)) {
                continue;
            }
            BigInteger g = a.modPow(BigInteger.TWO, pPrime);
            if (g.equals(BigInteger.ONE)) {
                continue;
            }
            if (g.modPow(q, pPrime).equals(BigInteger.ONE)) {
                return g;
            }
        }
        throw new IllegalStateException("failed to sample QR subgroup generator");
    }

    public byte[] getGroupId() {
        return Arrays.copyOf(groupId, groupId.length);
    }

    /** Sends group parameters to P2 (HN12 Step 1). */
    public byte[] serializeParameters() {
        byte[] pBytes = pPrime.toByteArray();
        byte[] qBytes = q.toByteArray();
        byte[] gBytes = encodeElement(g);
        byte[] out = new byte[12 + pBytes.length + qBytes.length + gBytes.length + groupId.length];
        int o = 0;
        o = writeInt(out, o, pBytes.length);
        System.arraycopy(pBytes, 0, out, o, pBytes.length);
        o += pBytes.length;
        o = writeInt(out, o, qBytes.length);
        System.arraycopy(qBytes, 0, out, o, qBytes.length);
        o += qBytes.length;
        o = writeInt(out, o, gBytes.length);
        System.arraycopy(gBytes, 0, out, o, gBytes.length);
        o += gBytes.length;
        System.arraycopy(groupId, 0, out, o, groupId.length);
        return out;
    }

    public static Hn12DdhGroup deserializeParameters(byte[] raw) {
        int o = 0;
        int pLen = readInt(raw, o);
        o += 4;
        BigInteger pPrime = new BigInteger(1, Arrays.copyOfRange(raw, o, o + pLen));
        o += pLen;
        int qLen = readInt(raw, o);
        o += 4;
        BigInteger q = new BigInteger(1, Arrays.copyOfRange(raw, o, o + qLen));
        o += qLen;
        int gLen = readInt(raw, o);
        o += 4;
        byte[] gEnc = Arrays.copyOfRange(raw, o, o + gLen);
        o += gLen;
        byte[] groupId = Arrays.copyOfRange(raw, o, raw.length);
        int elemLen = (pPrime.bitLength() + 7) / 8;
        BigInteger g = new BigInteger(1, Arrays.copyOfRange(gEnc, gEnc.length - elemLen, gEnc.length));
        Hn12DdhGroup group = new Hn12DdhGroup(pPrime, q, g, groupId);
        group.validateMember(g);
        return group;
    }

    private static int writeInt(byte[] out, int offset, int v) {
        out[offset] = (byte) (v >>> 24);
        out[offset + 1] = (byte) (v >>> 16);
        out[offset + 2] = (byte) (v >>> 8);
        out[offset + 3] = (byte) v;
        return offset + 4;
    }

    private static int readInt(byte[] raw, int offset) {
        return ((raw[offset] & 0xFF) << 24) | ((raw[offset + 1] & 0xFF) << 16)
            | ((raw[offset + 2] & 0xFF) << 8) | (raw[offset + 3] & 0xFF);
    }

    public BigInteger getQ() {
        return q;
    }

    public BigInteger getG() {
        return g;
    }

    public BigInteger identity() {
        return BigInteger.ONE;
    }

    public BigInteger pow(BigInteger base, BigInteger exp) {
        validateMember(base);
        return base.modPow(exp.mod(q), pPrime);
    }

    public BigInteger mul(BigInteger a, BigInteger b) {
        validateMember(a);
        validateMember(b);
        return a.multiply(b).mod(pPrime);
    }

    public BigInteger inv(BigInteger a) {
        validateMember(a);
        if (a.equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("inverse of identity");
        }
        return a.modInverse(pPrime);
    }

    public BigInteger sampleScalar(SecureRandom random) {
        BigInteger x;
        do {
            x = new BigInteger(q.bitLength(), random).mod(q);
        } while (x.signum() == 0);
        return x;
    }

    public BigInteger sampleNonIdentityElement(SecureRandom random) {
        BigInteger h;
        do {
            BigInteger x = sampleScalar(random);
            h = pow(g, x);
        } while (h.equals(BigInteger.ONE));
        return h;
    }

    public void validateMember(BigInteger h) {
        if (h == null || h.signum() <= 0 || h.compareTo(pPrime) >= 0) {
            throw new IllegalArgumentException("invalid group encoding");
        }
        if (!h.modPow(q, pPrime).equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("element not in subgroup G");
        }
    }

    public void validateNonIdentity(BigInteger h) {
        validateMember(h);
        if (h.equals(BigInteger.ONE)) {
            throw new IllegalArgumentException("unexpected identity");
        }
    }

    public byte[] encodeElement(BigInteger h) {
        validateMember(h);
        byte[] raw = h.toByteArray();
        byte[] out = new byte[(pPrime.bitLength() + 7) / 8];
        System.arraycopy(raw, Math.max(0, raw.length - out.length), out, Math.max(0, out.length - raw.length), Math.min(raw.length, out.length));
        return out;
    }

    public BigInteger decodeElement(byte[] encoded) {
        BigInteger h = new BigInteger(1, encoded);
        validateMember(h);
        return h;
    }
}
