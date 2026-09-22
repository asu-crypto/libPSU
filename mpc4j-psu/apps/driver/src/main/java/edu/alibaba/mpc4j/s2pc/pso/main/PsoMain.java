package edu.alibaba.mpc4j.s2pc.pso.main;

import edu.alibaba.libpsu.cli.LibPsuMain;

/**
 * Compatibility entry point. Use {@link LibPsuMain} for all libPSU protocol families.
 */
@Deprecated
public class PsoMain {
    public static void main(String[] args) throws Exception {
        LibPsuMain.main(args);
    }
}
