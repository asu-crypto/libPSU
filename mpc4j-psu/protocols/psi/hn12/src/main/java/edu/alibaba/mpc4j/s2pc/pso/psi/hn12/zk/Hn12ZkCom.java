package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.commit.Hn12Pedersen;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * π_COM: proof of knowledge of Pedersen opening (HN12 §2).
 */
public final class Hn12ZkCom {
    private Hn12ZkCom() {
    }

    public static final class Proof {
        public final BigInteger t;
        public final BigInteger sm;
        public final BigInteger sr;

        public Proof(BigInteger t, BigInteger sm, BigInteger sr) {
            this.t = t;
            this.sm = sm;
            this.sr = sr;
        }
    }

    public static Proof prove(
        Hn12DdhGroup group, Hn12Pedersen pedersen, BigInteger commitment, BigInteger m, BigInteger r,
        Hn12Transcript transcript, SecureRandom random
    ) {
        BigInteger q = group.getQ();
        BigInteger vm = group.sampleScalar(random);
        BigInteger vr = group.sampleScalar(random);
        BigInteger t = pedersen.commit(vm, vr);
        transcript.appendBigInteger(q, pedersen.getH(), commitment, t);
        BigInteger c = transcript.challenge(q);
        BigInteger sm = vm.add(c.multiply(m)).mod(q);
        BigInteger sr = vr.add(c.multiply(r)).mod(q);
        return new Proof(t, sm, sr);
    }

    public static boolean verify(
        Hn12DdhGroup group, Hn12Pedersen pedersen, BigInteger commitment, Proof proof, Hn12Transcript transcript
    ) {
        BigInteger q = group.getQ();
        transcript.appendBigInteger(q, pedersen.getH(), commitment, proof.t);
        BigInteger c = transcript.challenge(q);
        BigInteger lhs = pedersen.commit(proof.sm, proof.sr);
        BigInteger rhs = group.mul(proof.t, group.pow(commitment, c));
        return lhs.equals(rhs);
    }
}
