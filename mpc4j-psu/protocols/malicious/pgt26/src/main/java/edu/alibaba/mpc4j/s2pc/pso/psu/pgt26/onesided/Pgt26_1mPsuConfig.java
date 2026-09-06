package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.onesided;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuType;

/**
 * PGT26 malicious-sender one-sided PSU config (Section 5 / Fig. 5).
 */
public class Pgt26_1mPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
  private final CoreCotConfig coreCotConfig;

  private Pgt26_1mPsuConfig(Builder builder) {
    super(SecurityModel.MALICIOUS);
    coreCotConfig = builder.coreCotConfig;
  }

  @Override
  public PsuType getPtoType() {
    return PsuType.EUROCRYPT_PuGaoTri26;
  }

  public CoreCotConfig getCoreCotConfig() {
    return coreCotConfig;
  }

  public static class Builder implements org.apache.commons.lang3.builder.Builder<Pgt26_1mPsuConfig> {
    private CoreCotConfig coreCotConfig;

    public Builder() {
      coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
    }

    public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
      this.coreCotConfig = coreCotConfig;
      return this;
    }

    @Override
    public Pgt26_1mPsuConfig build() {
      return new Pgt26_1mPsuConfig(this);
    }
  }
}
