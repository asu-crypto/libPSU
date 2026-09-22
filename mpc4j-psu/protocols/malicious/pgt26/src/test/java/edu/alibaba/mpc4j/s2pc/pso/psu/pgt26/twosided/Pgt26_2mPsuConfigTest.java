package edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.twosided;

import org.junit.Assert;
import org.junit.Test;

public class Pgt26_2mPsuConfigTest {
  @Test
  public void testDefaultConfigIsMaliciousWithoutBypassApi() {
    Pgt26_2mPsuConfig config = new Pgt26_2mPsuConfig.Builder().build();
    Assert.assertEquals(
        edu.alibaba.mpc4j.common.rpc.desc.SecurityModel.MALICIOUS,
        config.getSecurityModel()
    );
  }
}
