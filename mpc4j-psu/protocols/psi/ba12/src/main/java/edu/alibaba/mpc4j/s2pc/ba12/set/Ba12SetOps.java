package edu.alibaba.mpc4j.s2pc.ba12.set;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12MpcEngine;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12Share;
import edu.alibaba.mpc4j.s2pc.ba12.core.Ba12TupleShare;

/**
 * BA12 set operations (Protocols 1–5).
 */
public class Ba12SetOps {
  private Ba12SetOps() {
    // empty
  }

  /**
   * Protocol 1: set union.
   */
  public static Ba12Share[] union(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    int m = x.length;
    Ba12Share[] c = new Ba12Share[m];
    for (int i = 0; i < m - 1; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i + 1]);
      c[i] = eng.mulByBitComplement(x[i], u);
    }
    c[m - 1] = x[m - 1];
    return c;
  }

  /**
   * Protocol 2: set intersection (length-hiding fixed array).
   */
  public static Ba12Share[] intersection(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    // Protocol 2 on sorted A||B matches Protocol 5 (element reduction) when each set has no duplicates.
    return elementReduction(eng, eng.concat(a, b));
  }

  /**
   * Protocol 4: set difference {@code A \\ B} (A tagged 0, B tagged 1).
   */
  public static Ba12Share[] difference(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    int m = a.length + b.length;
    Ba12TupleShare[] tuples = new Ba12TupleShare[m];
    // SortT payload bit order is reversed vs. paper labels; tag 1 marks A, 0 marks B.
    for (int i = 0; i < a.length; i++) {
      tuples[i] = new Ba12TupleShare(a[i], eng.publicBit(true));
    }
    for (int i = 0; i < b.length; i++) {
      tuples[a.length + i] = new Ba12TupleShare(b[i], eng.publicBit(false));
    }
    eng.sortT(tuples);
    Ba12Share[] x = new Ba12Share[m];
    MpcZ2Vector[] y = new MpcZ2Vector[m];
    for (int i = 0; i < m; i++) {
      x[i] = tuples[i].getValue();
      y[i] = tuples[i].getTag();
    }
    Ba12Share[] c = new Ba12Share[m];
    MpcZ2Vector uPrev = eng.publicBit(false);
    for (int i = 0; i < m; i++) {
      MpcZ2Vector u = (i + 1 < m) ? eng.eq(x[i], x[i + 1]) : eng.publicBit(false);
      MpcZ2Vector erase;
      if (i == 0) {
        erase = u;
      } else if (i == m - 1) {
        erase = uPrev;
      } else {
        erase = neighborEqual(eng, uPrev, u);
      }
      Ba12Share ci = eng.mulByBitComplement(x[i], erase);
      ci = eng.mulByBitComplement(ci, y[i]);
      c[i] = ci;
      uPrev = u;
    }
    return c;
  }

  /**
   * Symmetric difference: Protocol 4 without removing B-origin.
   */
  public static Ba12Share[] symmetricDifference(Ba12MpcEngine eng, Ba12Share[] a, Ba12Share[] b) throws MpcAbortException {
    Ba12Share[] x = eng.concat(a, b);
    eng.sort(x);
    int m = x.length;
    Ba12Share[] c = new Ba12Share[m];
    for (int i = 0; i < m; i++) {
      MpcZ2Vector dup;
      if (i == 0) {
        dup = eng.eq(x[i], x[i + 1]);
      } else if (i == m - 1) {
        dup = eng.eq(x[i], x[i - 1]);
      } else {
        dup = neighborEqual(eng, eng.eq(x[i], x[i - 1]), eng.eq(x[i], x[i + 1]));
      }
      c[i] = eng.mulByBitComplement(x[i], dup);
    }
    return c;
  }

  /**
   * Protocol 5: element reduction (multiset → set).
   */
  public static Ba12Share[] elementReduction(Ba12MpcEngine eng, Ba12Share[] a) throws MpcAbortException {
    Ba12Share[] x = java.util.Arrays.copyOf(a, a.length);
    eng.sort(x);
    int m = x.length;
    Ba12Share[] c = new Ba12Share[m];
    c[0] = eng.zeroShare();
    for (int i = 0; i < m - 1; i++) {
      MpcZ2Vector u = eng.eq(x[i], x[i + 1]);
      c[i + 1] = eng.mulByBit(x[i + 1], u);
    }
    return c;
  }

  /** {@code [b] = 1} iff either neighbor-equality bit is 1 (Protocol 4 middle term). */
  private static MpcZ2Vector neighborEqual(Ba12MpcEngine eng, MpcZ2Vector left, MpcZ2Vector right) throws MpcAbortException {
    return eng.getZ2c().not(eng.getZ2c().and(eng.getZ2c().not(left), eng.getZ2c().not(right)));
  }
}
