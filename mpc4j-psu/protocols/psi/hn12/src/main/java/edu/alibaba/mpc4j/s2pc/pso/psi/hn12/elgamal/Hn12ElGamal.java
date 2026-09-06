package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * ElGamal in the exponent: Enc(g^m) = (g^r, h^r · g^m) (HN12 §2).
 */
public final class Hn12ElGamal {
    private final Hn12DdhGroup group;
    private final BigInteger publicKey;
    private final BigInteger secretKey;

    public Hn12ElGamal(Hn12DdhGroup group, BigInteger publicKey, BigInteger secretKey) {
        this.group = group;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    public static Hn12ElGamal keyGen(Hn12DdhGroup group, SecureRandom random) {
        BigInteger sk = group.sampleScalar(random);
        BigInteger pk = group.pow(group.getG(), sk);
        return new Hn12ElGamal(group, pk, sk);
    }

    public BigInteger getPublicKey() {
        return publicKey;
    }

    public BigInteger getSecretKey() {
        return secretKey;
    }

    /** Step: encrypt exponent g^m. */
    public Hn12Ciphertext encryptExponent(BigInteger gm, SecureRandom random) {
        group.validateMember(gm);
        BigInteger r = group.sampleScalar(random);
        BigInteger alpha = group.pow(group.getG(), r);
        BigInteger beta = group.mul(group.pow(publicKey, r), gm);
        return new Hn12Ciphertext(alpha, beta);
    }

    /** Homomorphic multiply: Enc(m1) · Enc(m2) = Enc(m1·m2). */
    public Hn12Ciphertext multiply(Hn12Ciphertext a, Hn12Ciphertext b) {
        a.validate(group, false);
        b.validate(group, false);
        return new Hn12Ciphertext(group.mul(a.getAlpha(), b.getAlpha()), group.mul(a.getBeta(), b.getBeta()));
    }

    /** Exponentiate ciphertext by public scalar k: (α^k, β^k). */
    public Hn12Ciphertext powScalar(Hn12Ciphertext ct, BigInteger k) {
        ct.validate(group, false);
        return new Hn12Ciphertext(group.pow(ct.getAlpha(), k), group.pow(ct.getBeta(), k));
    }

    /** Multiply ciphertext by encryption of constant g^c (already in G). */
    public Hn12Ciphertext mulPlainExponent(Hn12Ciphertext ct, BigInteger gc) {
        ct.validate(group, false);
        group.validateMember(gc);
        return new Hn12Ciphertext(ct.getAlpha(), group.mul(ct.getBeta(), gc));
    }

    /** Re-randomize with fresh r. */
    public Hn12Ciphertext rerandomize(Hn12Ciphertext ct, SecureRandom random) {
        Hn12Ciphertext mask = encryptExponent(group.identity(), random);
        return multiply(ct, mask);
    }

    /** Decrypt to g^m (discrete log not recovered here). */
    public BigInteger decryptToExponent(Hn12Ciphertext ct) {
        ct.validate(group, false);
        BigInteger s = group.pow(ct.getAlpha(), secretKey);
        return group.mul(group.inv(s), ct.getBeta());
    }
}
