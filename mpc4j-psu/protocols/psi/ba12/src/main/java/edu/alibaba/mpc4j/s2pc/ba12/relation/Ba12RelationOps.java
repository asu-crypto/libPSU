package edu.alibaba.mpc4j.s2pc.ba12.relation;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12MpcEngine;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12TupleShare;

/**
 * BA12 subset / superset / equality (Protocols 3 and 7).
 */
public class Ba12RelationOps {
  private Ba12RelationOps() {
    // empty
  }

  /**
   * Protocol 3: subset for non-padded inputs (public sizes).
   */
  public static MpcZ2Vector subsetNonPadded(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b, int m1)
    throws MpcAbortException {
    if (m1 > b.length) {
      return eng.publicBit(false);
    }
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    Ba12Share t = eng.zeroShare();
    for (int i = 1; i < x.length; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i - 1]);
      t = eng.addBit(t, u);
    }
    return eng.eq(t, m1);
  }

  /**
   * Protocol 7: length-hiding subset for zero-padded inputs.
   */
  public static MpcZ2Vector subsetPadded(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b, int m1)
    throws MpcAbortException {
    int m = a.length + b.length;
    Ba12TupleShare[] tuples = new Ba12TupleShare[m];
    for (int i = 0; i < a.length; i++) {
      tuples[i] = new Ba12TupleShare(a[i], eng.publicBit(true));
    }
    for (int i = 0; i < b.length; i++) {
      tuples[a.length + i] = new Ba12TupleShare(b[i], eng.publicBit(false));
    }
    eng.sortT(tuples);
    MpcZ2Vector[] u = new MpcZ2Vector[m];
    Ba12Share[] x = new Ba12Share[m];
    MpcZ2Vector[] y = new MpcZ2Vector[m];
    for (int i = 0; i < m; i++) {
      x[i] = tuples[i].getValue();
      y[i] = tuples[i].getTag();
      if (i == 0) {
        u[i] = eng.eq(x[i], 0L);
      } else {
        u[i] = eng.eq(x[i], x[i - 1]);
      }
    }
    MpcZ2Vector[] v = eng.preAnd(u);
    Ba12Share t1 = eng.zeroShare();
    Ba12Share t2 = eng.zeroShare();
    for (int i = 0; i < m; i++) {
      MpcZ2Vector fromA = eng.getZ2c().and(y[i], eng.getZ2c().not(v[i]));
      t1 = eng.addBit(t1, fromA);
      MpcZ2Vector match = eng.getZ2c().xor(u[i], v[i]);
      t2 = eng.addBit(t2, match);
    }
    return eng.eq(t1, t2);
  }

  public static MpcZ2Vector supersetPadded(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b, int m2)
    throws MpcAbortException {
    return subsetPadded(eng, b, a, m2);
  }

  public static MpcZ2Vector equalityPadded(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b, int m1, int m2)
    throws MpcAbortException {
    if (m1 != m2) {
      return eng.publicBit(false);
    }
    MpcZ2Vector s1 = subsetPadded(eng, a, b, m1);
    MpcZ2Vector s2 = subsetPadded(eng, b, a, m2);
    return eng.getZ2c().and(s1, s2);
  }
}
