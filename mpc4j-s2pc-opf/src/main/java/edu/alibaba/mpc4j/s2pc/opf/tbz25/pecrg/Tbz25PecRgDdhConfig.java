package edu.alibaba.mpc4j.s2pc.opf.tbz25.pecrg;

import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;

/**
 * Configuration for {@link Tbz25PecRgDdhServer} / {@link Tbz25PecRgDdhClient}.
 */
public class Tbz25PecRgDdhConfig extends AbstractMultiPartyPtoConfig {
    private Tbz25PecRgDdhConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST);
    }

    public static class Builder implements org.apache.commons.lang3.builder.Builder<Tbz25PecRgDdhConfig> {
        @Override
        public Tbz25PecRgDdhConfig build() {
            return new Tbz25PecRgDdhConfig(this);
        }
    }
}
