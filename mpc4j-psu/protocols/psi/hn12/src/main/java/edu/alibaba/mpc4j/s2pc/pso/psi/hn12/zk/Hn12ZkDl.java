package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * π_DL: proof of knowledge of x with h = g^x (HN12 §2).
 */
public final class Hn12ZkDl {
    private Hn12ZkDl() {
    }

    public static final class Proof {
        public final BigInteger t;
        public final BigInteger s;

        public Proof(BigInteger t, BigInteger s) {
            this.t = t;
            this.s = s;
        }
    }

    public static Proof prove(
        Hn12DdhGroup group, BigInteger g, BigInteger h, BigInteger x,
        Hn12Transcript transcript, SecureRandom random
    ) {
        BigInteger q = group.getQ();
        BigInteger v = group.sampleScalar(random);
        BigInteger t = group.pow(g, v);
        transcript.appendBigInteger(q, g, h, t);
        BigInteger c = transcript.challenge(q);
        BigInteger s = v.subtract(c.multiply(x)).mod(q);
        return new Proof(t, s);
    }

    public static boolean verify(
        Hn12DdhGroup group, BigInteger g, BigInteger h, Proof proof, Hn12Transcript transcript
    ) {
        BigInteger q = group.getQ();
        group.validateMember(g);
        group.validateNonIdentity(h);
        group.validateMember(proof.t);
        transcript.appendBigInteger(q, g, h, proof.t);
        BigInteger c = transcript.challenge(q);
        BigInteger lhs = group.mul(group.pow(g, proof.s), group.pow(h, c));
        return lhs.equals(proof.t);
    }
}
