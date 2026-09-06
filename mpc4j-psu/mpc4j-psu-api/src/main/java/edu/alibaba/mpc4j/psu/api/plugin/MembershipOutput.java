package edu.alibaba.mpc4j.psu.api.plugin;

/**
 * Result of membership phase: COT choice bits for union delivery.
 */
public class MembershipOutput {
    private final boolean[] cotChoices;

    public MembershipOutput(boolean[] cotChoices) {
        this.cotChoices = cotChoices;
    }

    public boolean[] getCotChoices() {
        return cotChoices;
    }

    public int getCotNum() {
        return cotChoices.length;
    }
}
