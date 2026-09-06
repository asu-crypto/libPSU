package edu.alibaba.mpc4j.s2pc.ba12.core;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;

/**
 * Secret-shared field element {@code [x]} as {@code ℓ} XOR-shared bits (MSB-first partition).
 */
public class Ba12Share {
  private final MpcZ2Vector[] bits;

  public Ba12Share(MpcZ2Vector[] bits) {
    this.bits = bits;
  }

  public MpcZ2Vector[] getBits() {
    return bits;
  }

  public int getEll() {
    return bits.length;
  }
}
