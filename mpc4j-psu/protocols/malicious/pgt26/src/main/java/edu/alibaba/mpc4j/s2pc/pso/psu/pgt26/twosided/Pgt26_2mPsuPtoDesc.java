package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * PGT26-2M protocol steps (reference {@code channel.rs}, {@code twosided.rs}).
 */
public class Pgt26_2mPsuPtoDesc implements PtoDesc {
  private static final int PTO_ID = Math.abs((int) 2026052813L);
  private static final String PTO_NAME = "PGT26_2M_PSU";

  private static final Pgt26_2mPsuPtoDesc INSTANCE = new Pgt26_2mPsuPtoDesc();

  private Pgt26_2mPsuPtoDesc() {
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
    /** Step 1*: σ commitments */
    ROUND1_SIGMA,
    /** Step 2*: public key + blinded encoded points */
    ROUND2_PK_POINTS,
    /** Step 3a: adapted shuffle AoK (variable-size blob; separate from points for Netty RPC). */
    ROUND3_SHUFFLE_PROOF,
    /** Step 3b: shuffled blinded peer points (equal 32-byte encoding). */
    ROUND3_SHUFFLED_POINTS,
    /** Step 4*: unblinded difference + indices + batched RDDH AoK */
    ROUND4_UNBLIND,
  }
}
