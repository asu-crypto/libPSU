package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.zk;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12Transcript;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12Ciphertext;
import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.elgamal.Hn12ElGamal;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * π_NZ: ciphertext encrypts non-zero exponent (HN12, via π_MULT structure).
 */
public final class Hn12ZkNz {
    private Hn12ZkNz() {
    }

    public static final class Proof {
        public final Hn12ZkDdh.Proof ddhProof;

        Proof(Hn12ZkDdh.Proof ddhProof) {
            this.ddhProof = ddhProof;
        }
    }

    public static Proof prove(
        Hn12DdhGroup group, Hn12ElGamal elGamal, Hn12Ciphertext ct, BigInteger m, BigInteger r,
        Hn12Transcript transcript, SecureRandom random
    ) {
        if (m.signum() == 0) {
            throw new IllegalArgumentException("witness exponent must be non-zero");
        }
        BigInteger g = group.getG();
        BigInteger h = elGamal.getPublicKey();
        BigInteger gm = group.pow(g, m);
        BigInteger g1 = ct.getAlpha();
        BigInteger g2 = group.pow(g, r);
        BigInteger g3 = group.mul(ct.getBeta(), group.inv(gm));
        Hn12Transcript sub = transcript;
        Hn12ZkDdh.Proof ddh = Hn12ZkDdh.prove(group, g, g1, g2, g3, r, sub, random);
        return new Proof(ddh);
    }

    public static boolean verify(
        Hn12DdhGroup group, Hn12ElGamal elGamal, Hn12Ciphertext ct, Proof proof, Hn12Transcript transcript
    ) {
        ct.validate(group, true);
        BigInteger g = group.getG();
        BigInteger h = elGamal.getPublicKey();
        BigInteger g1 = ct.getAlpha();
        BigInteger g2 = h;
        BigInteger g3 = ct.getBeta();
        return Hn12ZkDdh.verify(group, g, g1, g2, g3, proof.ddhProof, transcript);
    }
}
