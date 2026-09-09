package edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory;
import edu.alibaba.mpc4j.common.tool.hashbin.object.cuckoo.CuckooHashBinFactory.CuckooHashBinType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * TCL23 UPSU params checker. Uses explicit exceptions (never Java {@code assert}).
 */
public final class Tcl23UpsuParamsChecker {

    private Tcl23UpsuParamsChecker() {
    }

    /**
     * Validate raw constructor arguments before native parameter generation.
     */
    public static void validateRaw(
        CuckooHashBinType type,
        int binNum,
        int maxPartitionSizePerBin,
        int itemEncodedSlotSize,
        int psLowDegree,
        int[] queryPowers,
        long plainModulus,
        int polyModulusDegree,
        int[] coeffModulusBits,
        int maxSenderSize
    ) {
        Objects.requireNonNull(type, "cuckooHashBinType");
        Preconditions.checkArgument(
            type == CuckooHashBinType.NAIVE_3_HASH || type == CuckooHashBinType.NO_STASH_ONE_HASH,
            "unsupported cuckoo hash type: %s", type
        );
        MathPreconditions.checkPositive("binNum", binNum);
        MathPreconditions.checkPositive("maxPartitionSizePerBin", maxPartitionSizePerBin);
        MathPreconditions.checkInRangeClosed("itemEncodedSlotSize", itemEncodedSlotSize, 2, 32);
        MathPreconditions.checkInRangeClosed("psLowDegree", psLowDegree, 0, maxPartitionSizePerBin);
        Objects.requireNonNull(queryPowers, "queryPowers");
        checkQueryPowers(queryPowers, psLowDegree);

        MathPreconditions.checkPositive("polyModulusDegree", polyModulusDegree);
        Preconditions.checkArgument(
            (polyModulusDegree & (polyModulusDegree - 1)) == 0,
            "polyModulusDegree is not a power of two: %s", polyModulusDegree
        );
        // Safe arithmetic for 2 * polyModulusDegree
        Preconditions.checkArgument(
            polyModulusDegree <= Integer.MAX_VALUE / 2,
            "polyModulusDegree too large for 2*degree: %s", polyModulusDegree
        );
        long twiceDegree = 2L * polyModulusDegree;
        MathPreconditions.checkPositive("plainModulus", plainModulus);
        Preconditions.checkArgument(
            plainModulus % twiceDegree == 1L,
            "plainModulus should satisfy plainModulus %% (2 * polyModulusDegree) == 1"
        );

        Objects.requireNonNull(coeffModulusBits, "coeffModulusBits");
        Preconditions.checkArgument(coeffModulusBits.length > 0, "coeffModulusBits must be non-empty");
        for (int bits : coeffModulusBits) {
            MathPreconditions.checkPositive("coeffModulusBits entry", bits);
        }

        int plainModulusBitLength = Long.SIZE - Long.numberOfLeadingZeros(plainModulus);
        long encodedBitLengthLong = (long) itemEncodedSlotSize * plainModulusBitLength;
        Preconditions.checkArgument(
            encodedBitLengthLong >= 80L && encodedBitLengthLong <= 256L,
            "encoded bits should be in [80, 256]: %s", encodedBitLengthLong
        );

        MathPreconditions.checkPositive("itemEncodedSlotSize for division", itemEncodedSlotSize);
        int slotsPerCiphertext = polyModulusDegree / itemEncodedSlotSize;
        MathPreconditions.checkPositive("slotsPerCiphertext", slotsPerCiphertext);
        Preconditions.checkArgument(
            binNum % slotsPerCiphertext == 0,
            "binNum should be a multiple of polyModulusDegree / itemEncodedSlotSize"
        );

        MathPreconditions.checkPositive("maxSenderSize", maxSenderSize);
        int maxItemSize = CuckooHashBinFactory.getMaxItemSize(type, binNum);
        MathPreconditions.checkLessOrEqual("maxSenderSize", maxSenderSize, maxItemSize);
    }

    /**
     * Check already-constructed params (post-native). Throws on invalid values.
     */
    public static void checkValid(Tcl23UpsuParams params) {
        Objects.requireNonNull(params, "params");
        validateRaw(
            params.getCuckooHashBinType(),
            params.getBinNum(),
            params.getMaxPartitionSizePerBin(),
            params.getItemEncodedSlotSize(),
            params.getPsLowDegree(),
            params.getQueryPowers(),
            params.getPlainModulus(),
            params.getPolyModulusDegree(),
            params.getCoeffModulusBits(),
            params.maxSenderElementSize()
        );
    }

    private static void checkQueryPowers(int[] sourcePowers, int psLowDegree) {
        Preconditions.checkArgument(sourcePowers.length > 0, "query powers must be non-empty");
        Set<Integer> seen = new HashSet<>(sourcePowers.length);
        boolean hasOne = false;
        for (int sourcePower : sourcePowers) {
            MathPreconditions.checkPositive("query power", sourcePower);
            Preconditions.checkArgument(seen.add(sourcePower), "query powers must be distinct: %s", sourcePower);
            if (sourcePower == 1) {
                hasOne = true;
            }
            Preconditions.checkArgument(
                sourcePower <= psLowDegree || sourcePower % (psLowDegree + 1) == 0,
                "query power %s must be <= psLowDegree or divisible by psLowDegree+1", sourcePower
            );
        }
        Preconditions.checkArgument(hasOne, "query powers must contain 1");
        // Keep sorted uniqueness check for clarity against duplicates that HashSet already rejects.
        int[] sorted = Arrays.stream(sourcePowers).distinct().sorted().toArray();
        Preconditions.checkArgument(sorted.length == sourcePowers.length, "query powers must be distinct");
    }
}
