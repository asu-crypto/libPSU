package edu.alibaba.mpc4j.s2pc.ba12;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * BA12 private set / multiset operations (Blanton–Aguiar).
 */
public class Ba12PtoDesc implements PtoDesc {
  private static final int PTO_ID = Math.abs((int) 0x42413132);
  private static final String PTO_NAME = "BA12_SET_OPS";
  private static final Ba12PtoDesc INSTANCE = new Ba12PtoDesc();

  private Ba12PtoDesc() {
    // empty
  }

  public static PtoDesc getInstance() {
    return INSTANCE;
  }

  static {
    PtoDescManager.registerPtoDesc(getInstance());
  }

  @Override
  public int getPtoId() {
    return PTO_ID;
  }

  @Override
  public String getPtoName() {
    return PTO_NAME;
  }
}
