package edu.alibaba.mpc4j.s2pc.ba12;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cConfig;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.Z2cFactory;
import edu.alibaba.mpc4j.s2pc.aby.basics.z2.bea91.Bea91Z2cConfig;

/**
 * BA12 protocol configuration.
 */
public class Ba12Config extends AbstractMultiPartyPtoConfig {
  private final int ell;
  private final SecurityModel securityModel;
  private final Ba12OutputMode outputMode;
  private final Ba12OpeningMode openingMode;
  private final Ba12SortBackend sortBackend;
  private final boolean useCompaction;
  private final boolean inputAlreadySorted;
  private final Z2cConfig z2cConfig;

  private Ba12Config(Builder builder) {
    super(SecurityModel.SEMI_HONEST, builder.z2cConfig);
    this.ell = builder.ell;
    this.securityModel = builder.securityModel;
    this.outputMode = builder.outputMode;
    this.openingMode = builder.openingMode;
    this.sortBackend = builder.sortBackend;
    this.useCompaction = builder.useCompaction;
    this.inputAlreadySorted = builder.inputAlreadySorted;
    this.z2cConfig = builder.z2cConfig;
  }

  public int getEll() {
    return ell;
  }

  public SecurityModel getSecurityModel() {
    return securityModel;
  }

  public Ba12OutputMode getOutputMode() {
    return outputMode;
  }

  public Ba12OpeningMode getOpeningMode() {
    return openingMode;
  }

  public Ba12SortBackend getSortBackend() {
    return sortBackend;
  }

  public boolean isUseCompaction() {
    return useCompaction;
  }

  public boolean isInputAlreadySorted() {
    return inputAlreadySorted;
  }

  public Z2cConfig getZ2cConfig() {
    return z2cConfig;
  }

  public static class Builder {
    private int ell = 32;
    private SecurityModel securityModel = SecurityModel.SEMI_HONEST;
    private Ba12OutputMode outputMode = Ba12OutputMode.LENGTH_HIDING;
    private Ba12OpeningMode openingMode = Ba12OpeningMode.SHARED_OUTPUT;
    private Ba12SortBackend sortBackend = Ba12SortBackend.BATCHER_SORT;
    private boolean useCompaction = false;
    private boolean inputAlreadySorted = false;
    private Z2cConfig z2cConfig = new Bea91Z2cConfig.Builder(SecurityModel.SEMI_HONEST, true).build();

    public Builder() {
      // empty
    }

    public Builder setEll(int ell) {
      this.ell = ell;
      return this;
    }

    public Builder setSecurityModel(SecurityModel securityModel) {
      this.securityModel = securityModel;
      return this;
    }

    public Builder setOutputMode(Ba12OutputMode outputMode) {
      this.outputMode = outputMode;
      return this;
    }

    public Builder setOpeningMode(Ba12OpeningMode openingMode) {
      this.openingMode = openingMode;
      return this;
    }

    public Builder setSortBackend(Ba12SortBackend sortBackend) {
      this.sortBackend = sortBackend;
      return this;
    }

    public Builder setUseCompaction(boolean useCompaction) {
      this.useCompaction = useCompaction;
      return this;
    }

    public Builder setInputAlreadySorted(boolean inputAlreadySorted) {
      this.inputAlreadySorted = inputAlreadySorted;
      return this;
    }

    public Builder setZ2cConfig(Z2cConfig z2cConfig) {
      this.z2cConfig = z2cConfig;
      return this;
    }

    public Builder setSilent(boolean silent) {
      this.z2cConfig = new Bea91Z2cConfig.Builder(securityModel, silent).build();
      return this;
    }

    public Ba12Config build() {
      return new Ba12Config(this);
    }
  }
}
