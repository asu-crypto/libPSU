package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * PGT26 fully malicious two-sided PSU (Section 6). Not EUROCRYPT_PisTri26/IBLT.
 * <p>
 * Proofs are always verified; production configs never expose proof-bypass flags.
 */
public class Pgt26_2mPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
  private Pgt26_2mPsuConfig(Builder builder) {
    super(SecurityModel.MALICIOUS);
  }

  @Override
  public PsuType getPtoType() {
    return PsuType.EUROCRYPT_PuGaoTri26;
  }

  public static class Builder implements org.apache.commons.lang3.builder.Builder<Pgt26_2mPsuConfig> {
    @Override
    public Pgt26_2mPsuConfig build() {
      return new Pgt26_2mPsuConfig(this);
    }
  }
}
