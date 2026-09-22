package edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23;

import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Raw TCL23 parameter validation must run without native FHE and without {@code -ea}.
 */
public class Tcl23UpsuParamsCheckerTest {
    private static final int[] VALID_POWERS = {1, 3, 11, 18, 45, 225};
    private static final int[] VALID_COEFF = {56, 56, 56, 50};

    @Test
    public void acceptsKnownValidRawArgs() {
        Tcl23UpsuParamsChecker.validateRaw(
            CuckooHashBinType.NAIVE_3_HASH, 1638, 1304, 5, 44, VALID_POWERS,
            4079617L, 8192, VALID_COEFF, 1024
        );
    }

    @Test
    public void rejectsInvalidBinNumWithoutNative() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
            Tcl23UpsuParamsChecker.validateRaw(
                CuckooHashBinType.NAIVE_3_HASH, 0, 1304, 5, 44, VALID_POWERS,
                4079617L, 8192, VALID_COEFF, 1024
            )
        );
    }

    @Test
    public void rejectsMissingQueryPowerOne() {
        Assert.assertThrows(RuntimeException.class, () ->
            Tcl23UpsuParamsChecker.validateRaw(
                CuckooHashBinType.NAIVE_3_HASH, 1638, 1304, 5, 44, new int[]{3, 11},
                4079617L, 8192, VALID_COEFF, 1024
            )
        );
    }

    @Test
    public void rejectsNonPowerOfTwoDegree() {
        Assert.assertThrows(IllegalArgumentException.class, () ->
            Tcl23UpsuParamsChecker.validateRaw(
                CuckooHashBinType.NAIVE_3_HASH, 1638, 1304, 5, 44, VALID_POWERS,
                4079617L, 8000, VALID_COEFF, 1024
            )
        );
    }
}
