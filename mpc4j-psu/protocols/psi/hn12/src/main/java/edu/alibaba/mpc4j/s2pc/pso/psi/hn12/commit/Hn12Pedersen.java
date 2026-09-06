package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.commit;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Pedersen commitment com(m; r) = h^m · g^r (HN12 §2, h from setup).
 */
public final class Hn12Pedersen {
    private final Hn12DdhGroup group;
    private final BigInteger h;

    public Hn12Pedersen(Hn12DdhGroup group, BigInteger h) {
        this.group = group;
        this.h = h;
        group.validateNonIdentity(h);
    }

    public BigInteger commit(BigInteger m, BigInteger r) {
        BigInteger hm = group.pow(h, m);
        BigInteger gr = group.pow(group.getG(), r);
        return group.mul(hm, gr);
    }

    public BigInteger commitRandomOpening(SecureRandom random) {
        BigInteger m = group.sampleScalar(random);
        BigInteger r = group.sampleScalar(random);
        return commit(m, r);
    }

    public BigInteger getH() {
        return h;
    }
}
