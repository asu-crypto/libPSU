package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * PGT26 fully malicious two-sided PSU (Section 6). Not EUROCRYPT_PisTri26/IBLT.
 */
public class Pgt26_2mPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
  /** If true, skip verifying the adapted shuffle AoK (benchmark/debug only). */
  private final boolean skipShuffleProof;
  /** If true, skip verifying the batched RDDH AoK (benchmark/debug only). */
  private final boolean skipRddhProof;

  private Pgt26_2mPsuConfig(Builder builder) {
    super(SecurityModel.MALICIOUS);
    skipShuffleProof = builder.skipShuffleProof;
    skipRddhProof = builder.skipRddhProof;
  }

  @Override
  public PsuType getPtoType() {
    return PsuType.EUROCRYPT_PuGaoTri26;
  }

  public boolean isSkipShuffleProof() {
    return skipShuffleProof;
  }

  public boolean isSkipRddhProof() {
    return skipRddhProof;
  }

  public static class Builder implements org.apache.commons.lang3.builder.Builder<Pgt26_2mPsuConfig> {
    /** Malicious-security default: always verify adapted shuffle AoK. */
    private boolean skipShuffleProof = false;
    /** Malicious-security default: always verify batched RDDH AoK. */
    private boolean skipRddhProof = false;

    public Builder setSkipShuffleProof(boolean skipShuffleProof) {
      this.skipShuffleProof = skipShuffleProof;
      return this;
    }

    public Builder setSkipRddhProof(boolean skipRddhProof) {
      this.skipRddhProof = skipRddhProof;
      return this;
    }

    @Override
    public Pgt26_2mPsuConfig build() {
      return new Pgt26_2mPsuConfig(this);
    }
  }
}
