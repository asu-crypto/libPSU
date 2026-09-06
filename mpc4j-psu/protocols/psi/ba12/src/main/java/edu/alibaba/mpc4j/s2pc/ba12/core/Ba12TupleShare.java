package edu.alibaba.mpc4j.s2pc.ba12.core;

import edu.alibaba.mpc4j.common.circuit.z2.MpcZ2Vector;

/**
 * Tuple {@code <[x], [tag]>} for {@code SortT} (Protocol 4 / 7).
 */
public class Ba12TupleShare {
  private final Ba12Share value;
  /**
   * Origin / auxiliary bit(s); for set difference this is the A-origin flag.
   */
  private final MpcZ2Vector tag;

  public Ba12TupleShare(Ba12Share value, MpcZ2Vector tag) {
    this.value = value;
    this.tag = tag;
  }

  public Ba12Share getValue() {
    return value;
  }

  public MpcZ2Vector getTag() {
    return tag;
  }
}
