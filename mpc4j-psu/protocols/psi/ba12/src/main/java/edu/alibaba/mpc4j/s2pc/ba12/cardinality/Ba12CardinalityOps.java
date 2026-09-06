package edu.alibaba.mpc4j.s2pc.ba12.cardinality;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12MpcEngine;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import edu.alibaba.mpc4j.s2pc.ba12.set.Ba12SetOps;

/**
 * BA12 cardinality and threshold variants.
 */
public class Ba12CardinalityOps {
  private Ba12CardinalityOps() {
    // empty
  }

  public static Ba12Share unionCardinality(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    Ba12Share card = eng.publicShare((long) a.length + b.length);
    for (int i = 0; i < x.length - 1; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i + 1]);
      card = eng.sub(card, mulBitToInt(eng, u));
    }
    return card;
  }

  public static Ba12Share intersectionCardinality(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b)
    throws MpcAbortException {
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    Ba12Share card = eng.zeroShare();
    for (int i = 1; i + 1 < x.length; i += 2) {
      MpcZ2Vector u = eng.eq(x[i], x[i - 1]);
      MpcZ2Vector v = eng.eq(x[i], x[i + 1]);
      MpcZ2Vector uv = (MpcZ2Vector) eng.getZ2c().xor(u, v);
      card = eng.addBit(card, uv);
    }
    if (x.length >= 2 && x.length % 2 == 0) {
      MpcZ2Vector u = eng.eq(x[x.length - 1], x[x.length - 2]);
      card = eng.addBit(card, u);
    }
    return card;
  }

  public static Ba12Share differenceCardinality(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b)
    throws MpcAbortException {
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    Ba12Share card = eng.publicShare((long) a.length);
    for (int i = 0; i < x.length - 1; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i + 1]);
      card = eng.sub(card, mulBitToInt(eng, u));
    }
    return card;
  }

  public static Ba12Share symmetricDifferenceCardinality(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b)
    throws MpcAbortException {
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    Ba12Share dup = eng.zeroShare();
    for (int i = 0; i < x.length - 1; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i + 1]);
      dup = eng.addBit(dup, u);
    }
    Ba12Share card = eng.publicShare((long) a.length + b.length);
    Ba12Share twoDup = eng.add(dup, dup);
    return eng.sub(eng.sub(card, dup), dup);
  }

  public static Ba12Share elementReductionCardinality(Ba12MpcEngine eng, Ba12Share[] a) throws MpcAbortException {
    Ba12Share[] x = java.util.Arrays.copyOf(a, a.length);
    eng.sort(x);
    Ba12Share card = eng.zeroShare();
    for (int i = 0; i < x.length - 1; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i + 1]);
      card = eng.addBit(card, u);
    }
    return card;
  }

  public static MpcZ2Vector thresholdGe(Ba12MpcEngine eng, Ba12Share card, long threshold) throws MpcAbortException {
    return eng.ge(card, threshold);
  }

  private static Ba12Share mulBitToInt(Ba12MpcEngine eng, MpcZ2Vector bit) throws MpcAbortException {
    return eng.mulByBit(eng.publicShare(1L), bit);
  }
}
