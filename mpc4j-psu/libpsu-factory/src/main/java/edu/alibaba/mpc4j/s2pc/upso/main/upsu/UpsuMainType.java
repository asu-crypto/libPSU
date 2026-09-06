package edu.alibaba.mpc4j.s2pc.upso.main.upsu;

/**
 * UPSU type.
 *
 * @author Liqiang Peng
 * @date 2024/3/29
 */
public enum UpsuMainType {
    /**
     * TCL23 Byte Ecc DDH Permute Matrix PEQT
     */
    TCL23_BYTE_ECC_DDH,
    /**
     * TCL23 Ecc DDH Permute Matrix PEQT
     */
    TCL23_ECC_DDH,
    /**
     * TCL23 Permute + Share and OPRF Permute Matrix PEQT
     */
    TCL23_PS_OPRF_GMR21,
    /**
     * TCL23 Permute + Share and OPRF Permute Matrix PEQT
     */
    TCL23_PS_OPRF_MS13,
    /**
     * TCL23 FHE PSU (BYTE_ECC_DDH pm-PEQT), fair-bench style.
     */
    TCL23,
    /**
     * USENIX_BinYujConYanYu25 enhanced UPSU (pnMCRG + OTP; linear unbalanced via balanced ePSU core).
     */
    USENIX_BinYujConYanYu25,
}
