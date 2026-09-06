package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * PGT26-1M protocol description (not EUROCRYPT_PisTri26).
 */
public class Pgt26_1mPsuPtoDesc implements PtoDesc {
  private static final int PTO_ID = Math.abs((int) 2026052812L);
  private static final String PTO_NAME = "PGT26_1M_PSU";

  private static final Pgt26_1mPsuPtoDesc INSTANCE = new Pgt26_1mPsuPtoDesc();

  private Pgt26_1mPsuPtoDesc() {
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

  enum PtoStep {
    /** P0 → P1: blinded x_i = H(v_i)^k */
    CLIENT_SEND_BLINDED_X,
    /** P1 → P0: y_j = H(w_j)^s */
    SERVER_SEND_BLINDED_Y,
    /** P1 → P0: shuffled e_i = x_{π(i)}^s */
    SERVER_SEND_SHUFFLED_E,
    /** P1 → P0: OT ciphertexts (w_j || φ_j) */
    SERVER_SEND_OT_PAYLOAD,
  }
}
