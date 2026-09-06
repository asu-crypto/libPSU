package edu.alibaba.mpc4j.s2pc.ba12.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plaintext reference implementations for BA12 {@code 2^5} tests.
 */
public class Ba12Plaintext {
  private Ba12Plaintext() {
    // empty
  }

  public static long[] padTo(int len, long[] values) {
    long[] out = new long[len];
    System.arraycopy(values, 0, out, 0, values.length);
    return out;
  }

  public static long[] union(long[] a, long[] b) {
    long[] x = concat(a, b);
    Arrays.sort(x);
    long[] c = new long[x.length];
    int w = 0;
    for (int i = 0; i < x.length; i++) {
      if (x[i] == 0) {
        continue;
      }
      if (i + 1 < x.length && x[i] == x[i + 1]) {
        continue;
      }
      c[w++] = x[i];
    }
    return eraseToFixed(c, x.length);
  }

  public static long[] intersection(long[] a, long[] b) {
    return elementReduction(concat(a, b));
  }

  public static long[] symmetricDifference(long[] a, long[] b) {
    long[] x = concat(a, b);
    Arrays.sort(x);
    int m = x.length;
    long[] c = new long[m];
    boolean uPrev = false;
    for (int i = 0; i < m; i++) {
      boolean u = i + 1 < m && x[i] == x[i + 1];
      boolean erase;
      if (i == 0) {
        erase = u;
      } else if (i == m - 1) {
        erase = uPrev;
      } else {
        erase = uPrev || u;
      }
      if (x[i] != 0 && !erase) {
        c[i] = x[i];
      }
      uPrev = u;
    }
    return c;
  }

  public static long[] difference(long[] a, long[] b) {
    int m = a.length + b.length;
    long[] x = new long[m];
    int p = 0;
    for (long v : a) {
      x[p++] = v;
    }
    for (long v : b) {
      x[p++] = v;
    }
    // tag: 0 for A, 1 for B — sort tuples by value
    Tuple[] t = new Tuple[m];
    for (int i = 0; i < a.length; i++) {
      t[i] = new Tuple(a[i], 0);
    }
    for (int i = 0; i < b.length; i++) {
      t[a.length + i] = new Tuple(b[i], 1);
    }
    Arrays.sort(t, (u, v) -> Long.compare(u.value, v.value));
    long[] sortedVal = new long[m];
    int[] tag = new int[m];
    for (int i = 0; i < m; i++) {
      sortedVal[i] = t[i].value;
      tag[i] = t[i].tag;
    }
    long[] afterDup = new long[m];
    int w = 0;
    for (int i = 0; i < m; i++) {
      boolean adj = (i + 1 < m && sortedVal[i] == sortedVal[i + 1]);
      long val = sortedVal[i];
      if (val == 0) {
        afterDup[w++] = 0;
        continue;
      }
      if (i == 0) {
        afterDup[w++] = adj ? 0 : val;
      } else if (i == m - 1) {
        afterDup[w++] = (sortedVal[i] == sortedVal[i - 1]) ? 0 : val;
      } else {
        boolean dup = adj || (sortedVal[i] == sortedVal[i - 1]);
        afterDup[w++] = dup ? 0 : val;
      }
    }
    // remove B-origin
    int wi = 0;
    long[] c = new long[m];
    for (int i = 0; i < m; i++) {
      if (afterDup[i] != 0 && tag[i] == 0) {
        c[wi++] = afterDup[i];
      }
    }
    while (wi < m) {
      c[wi++] = 0;
    }
    return c;
  }

  public static long[] elementReduction(long[] a) {
    long[] x = Arrays.copyOf(a, a.length);
    Arrays.sort(x);
    long[] c = new long[x.length];
    c[0] = 0;
    for (int i = 0; i < x.length - 1; i++) {
      c[i + 1] = (x[i] != 0 && x[i] == x[i + 1]) ? x[i + 1] : 0;
    }
    return c;
  }

  public static boolean subsetNonPadded(long[] a, long[] b) {
    if (a.length > b.length) {
      return false;
    }
    return new HashSet<>(asSet(b)).containsAll(asSet(a));
  }

  public static boolean equality(long[] a, long[] b) {
    return asSet(a).equals(asSet(b));
  }

  public static Set<Long> asSet(long[] xs) {
    Set<Long> s = new HashSet<>();
    for (long x : xs) {
      if (x != 0) {
        s.add(x);
      }
    }
    return s;
  }

  public static long[] asSortedNonZero(long[] xs) {
    return asSet(xs).stream().sorted().mapToLong(Long::longValue).toArray();
  }

  private static long[] concat(long[] a, long[] b) {
    long[] x = new long[a.length + b.length];
    System.arraycopy(a, 0, x, 0, a.length);
    System.arraycopy(b, 0, x, a.length, b.length);
    return x;
  }

  private static long[] eraseToFixed(long[] c, int len) {
    long[] out = new long[len];
    System.arraycopy(c, 0, out, 0, Math.min(c.length, len));
    return out;
  }

  private static final class Tuple {
    final long value;
    final int tag;

    Tuple(long value, int tag) {
      this.value = value;
      this.tag = tag;
    }
  }
}
