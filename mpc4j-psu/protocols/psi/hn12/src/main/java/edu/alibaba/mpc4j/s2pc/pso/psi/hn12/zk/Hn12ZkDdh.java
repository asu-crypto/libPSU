package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * π_DDH: g1 = g^x and g3 = g2^x (HN12 §2).
 */
public final class Hn12ZkDdh {
    private Hn12ZkDdh() {
    }

    public static final class Proof {
        public final BigInteger a;
        public final BigInteger b;
        public final BigInteger z;

        Proof(BigInteger a, BigInteger b, BigInteger z) {
            this.a = a;
            this.b = b;
            this.z = z;
        }
    }

    public static Proof prove(
        Hn12DdhGroup group, BigInteger g, BigInteger g1, BigInteger g2, BigInteger g3, BigInteger x,
        Hn12Transcript transcript, SecureRandom random
    ) {
        BigInteger q = group.getQ();
        BigInteger v = group.sampleScalar(random);
        BigInteger w = group.sampleScalar(random);
        BigInteger a = group.mul(group.pow(g, v), group.pow(g2, w));
        BigInteger b = group.pow(g1, w);
        transcript.appendBigInteger(q, g, g1, g2, g3, a, b);
        BigInteger c = transcript.challenge(q);
        BigInteger z = v.add(c.multiply(x)).add(w.multiply(c)).mod(q);
        return new Proof(a, b, z);
    }

    public static boolean verify(
        Hn12DdhGroup group, BigInteger g, BigInteger g1, BigInteger g2, BigInteger g3,
        Proof proof, Hn12Transcript transcript
    ) {
        BigInteger q = group.getQ();
        transcript.appendBigInteger(q, g, g1, g2, g3, proof.a, proof.b);
        BigInteger c = transcript.challenge(q);
        BigInteger lhs1 = group.mul(group.pow(g, proof.z), group.pow(g2, c.negate()));
        BigInteger rhs1 = proof.a;
        BigInteger lhs2 = group.pow(g1, proof.z);
        BigInteger rhs2 = group.mul(proof.b, group.pow(g3, c));
        return lhs1.equals(rhs1) && lhs2.equals(rhs2);
    }
}
