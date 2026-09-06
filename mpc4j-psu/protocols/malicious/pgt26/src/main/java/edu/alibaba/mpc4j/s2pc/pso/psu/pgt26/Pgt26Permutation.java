package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26;

import java.security.SecureRandom;

/**
 * Random permutation with inverse (reference {@code aok.rs} {@code random_permutation}).
 */
public final class Pgt26Permutation {
  public final int[] perm;
  public final int[] inv;

  private Pgt26Permutation(int[] perm, int[] inv) {
    this.perm = perm;
    this.inv = inv;
  }

  public static Pgt26Permutation random(int n, SecureRandom random) {
    int[] perm = new int[n];
    int[] inv = new int[n];
    for (int i = 0; i < n; i++) {
      perm[i] = i;
      inv[i] = i;
    }
    for (int i = n - 1; i >= 1; i--) {
      int j = random.nextInt(i + 1);
      int tmp = perm[i];
      perm[i] = perm[j];
      perm[j] = tmp;
      inv[perm[i]] = i;
      inv[perm[j]] = j;
    }
    return new Pgt26Permutation(perm, inv);
  }
}
