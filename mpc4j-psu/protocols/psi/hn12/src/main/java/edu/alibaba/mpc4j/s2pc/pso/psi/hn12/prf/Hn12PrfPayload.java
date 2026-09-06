package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.prf;

import edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common.Hn12DdhGroup;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Domain-separated PRG expansion from F_PRF(k, s) (HN12 Protocol 5, Step 6).
 */
public final class Hn12PrfPayload {
    public final BigInteger rerand0;
    public final BigInteger rerand1;
    public final byte[] maskXorBytes;
    public final BigInteger openingS;

    public Hn12PrfPayload(BigInteger rerand0, BigInteger rerand1, byte[] maskXorBytes, BigInteger openingS) {
        this.rerand0 = rerand0;
        this.rerand1 = rerand1;
        this.maskXorBytes = maskXorBytes;
        this.openingS = openingS;
    }

    public static Hn12PrfPayload parsePsi(byte[] prfOut, Hn12DdhGroup group, int index) {
        BigInteger q = group.getQ();
        BigInteger r0 = scalarAt(prfOut, 0, q, "psi_rerand0", index);
        BigInteger r1 = scalarAt(prfOut, 1, q, "psi_rerand1", index);
        byte[] mx = Arrays.copyOfRange(prfOut, 64, 96);
        byte[] sRaw = Arrays.copyOfRange(prfOut, 96, 128);
        BigInteger s = new BigInteger(1, sRaw).mod(q);
        return new Hn12PrfPayload(r0, r1, mx, s);
    }

    private static BigInteger scalarAt(byte[] seed, int slot, BigInteger q, String label, int index) {
        byte[] raw = Hn12IdealPrf.eval(seed, BigInteger.valueOf(slot), label + "_" + index);
        BigInteger x = new BigInteger(1, raw).mod(q);
        return x.signum() == 0 ? BigInteger.ONE : x;
    }

    public static byte[] sampleKey(SecureRandom random) {
        byte[] k = new byte[32];
        random.nextBytes(k);
        return k;
    }
}
