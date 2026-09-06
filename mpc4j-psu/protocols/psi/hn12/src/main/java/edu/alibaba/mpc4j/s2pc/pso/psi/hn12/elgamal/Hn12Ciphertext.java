package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * ElGamal ciphertext (α, β) = (g^r, h^r · m) (HN12 §2).
 */
public final class Hn12Ciphertext {
    private final BigInteger alpha;
    private final BigInteger beta;

    public Hn12Ciphertext(BigInteger alpha, BigInteger beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public BigInteger getAlpha() {
        return alpha;
    }

    public BigInteger getBeta() {
        return beta;
    }

    public void validate(Hn12DdhGroup group, boolean requireNonIdentityAlpha) {
        group.validateMember(alpha);
        group.validateMember(beta);
        if (requireNonIdentityAlpha) {
            group.validateNonIdentity(alpha);
        }
    }

    public byte[] serialize(Hn12DdhGroup group) {
        byte[] a = group.encodeElement(alpha);
        byte[] b = group.encodeElement(beta);
        byte[] out = new byte[4 + a.length + 4 + b.length];
        int o = 0;
        o = writeLen(out, o, a.length);
        System.arraycopy(a, 0, out, o, a.length);
        o += a.length;
        o = writeLen(out, o, b.length);
        System.arraycopy(b, 0, out, o, b.length);
        return out;
    }

    public static Hn12Ciphertext deserialize(byte[] raw, Hn12DdhGroup group) {
        int o = 0;
        int la = readLen(raw, o);
        o += 4;
        BigInteger alpha = group.decodeElement(Arrays.copyOfRange(raw, o, o + la));
        o += la;
        int lb = readLen(raw, o);
        o += 4;
        BigInteger beta = group.decodeElement(Arrays.copyOfRange(raw, o, o + lb));
        Hn12Ciphertext ct = new Hn12Ciphertext(alpha, beta);
        ct.validate(group, false);
        return ct;
    }

    private static int writeLen(byte[] out, int offset, int len) {
        out[offset] = (byte) (len >>> 24);
        out[offset + 1] = (byte) (len >>> 16);
        out[offset + 2] = (byte) (len >>> 8);
        out[offset + 3] = (byte) len;
        return offset + 4;
    }

    private static int readLen(byte[] raw, int offset) {
        return ((raw[offset] & 0xFF) << 24) | ((raw[offset + 1] & 0xFF) << 16)
            | ((raw[offset + 2] & 0xFF) << 8) | (raw[offset + 3] & 0xFF);
    }
}
