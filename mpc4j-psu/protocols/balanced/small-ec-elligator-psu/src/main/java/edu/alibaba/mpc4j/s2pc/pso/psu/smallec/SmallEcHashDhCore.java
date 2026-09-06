package edu.alibaba.mpc4j.s2pc.pso.psu.smallec;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.EcGroupOps;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto.ElligatorCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Figure 8 HashDH core for Ours (semi-honest, no ZK/AoK/OT).
 */
public final class SmallEcHashDhCore {
    private static final Logger LOG = LoggerFactory.getLogger(SmallEcHashDhCore.class);
    private static final ThreadLocal<ElligatorCodec> THREAD_LOCAL_CODEC =
        ThreadLocal.withInitial(ElligatorCodec::protocolInstance);

    private SmallEcHashDhCore() {
        // empty
    }

    static byte[] hashToBlindPoint(byte[] item, ElligatorCodec codec) {
        return codec.mapToPoint(item);
    }

    public static byte[][] blindItems(byte[][] items, byte[] scalar, ElligatorCodec codec) {
        byte[][] blinded = new byte[items.length][];
        for (int i = 0; i < items.length; i++) {
            byte[] h = hashToBlindPoint(items[i], codec);
            blinded[i] = EcGroupOps.scalarMul(h, scalar);
        }
        return blinded;
    }

    public static byte[][] blindItemsOptimized(
        byte[][] items,
        byte[] scalar,
        SmallEcElligatorPsuConfig config
    ) {
        return blindItemsOptimized(items, scalar, config, ElligatorCodec.protocolInstance());
    }

    public static byte[][] blindItemsOptimized(
        byte[][] items,
        byte[] scalar,
        SmallEcElligatorPsuConfig config,
        ElligatorCodec codec
    ) {
        if (!config.isParallelEc() || items.length < config.getParallelThreshold()) {
            return blindItems(items, scalar, codec);
        }

        LOG.info("SMALL_EC parallel blindItems active: n={}, threshold={}", items.length, config.getParallelThreshold());
        byte[][] out = new byte[items.length][];

        IntStream.range(0, items.length).parallel().forEach(i -> {
            ElligatorCodec localCodec = THREAD_LOCAL_CODEC.get();
            byte[] h = hashToBlindPoint(items[i], localCodec);
            out[i] = EcGroupOps.scalarMul(h, scalar);
        });

        return out;
    }

    public static byte[][] blindPeer(byte[][] peerBlinded, byte[] scalar) {
        byte[][] out = new byte[peerBlinded.length][];
        for (int i = 0; i < peerBlinded.length; i++) {
            EcGroupOps.validatePoint(peerBlinded[i]);
            out[i] = EcGroupOps.scalarMul(peerBlinded[i], scalar);
        }
        return out;
    }

    public static byte[][] shuffleBlind(byte[][] peerBlinded, byte[] scalar, int[] permutation) {
        byte[][] shuffled = new byte[permutation.length][];
        for (int i = 0; i < permutation.length; i++) {
            shuffled[i] = EcGroupOps.scalarMul(peerBlinded[permutation[i]], scalar);
        }
        return shuffled;
    }

    public static byte[][] shuffleBlindOptimized(
        byte[][] peerBlinded,
        byte[] scalar,
        int[] permutation,
        SmallEcElligatorPsuConfig config
    ) {
        if (!config.isParallelEc() || permutation.length < config.getParallelThreshold()) {
            return shuffleBlind(peerBlinded, scalar, permutation);
        }

        LOG.info(
            "SMALL_EC parallel shuffleBlind active: n={}, threshold={}",
            permutation.length, config.getParallelThreshold()
        );
        byte[][] shuffled = new byte[permutation.length][];

        IntStream.range(0, permutation.length).parallel().forEach(i -> {
            shuffled[i] = EcGroupOps.scalarMul(peerBlinded[permutation[i]], scalar);
        });

        return shuffled;
    }

    /**
     * Server filter: U'_j = u_j^{k0^{-1}} for u_j ∉ W (Figure 8 step 4).
     */
    public static List<byte[]> filterDifference(
        byte[][] shuffledU,
        byte[] invK0,
        Set<EcGroupOps.CanonicalPoint> receiverWKeys
    ) {
        List<byte[]> diff = new ArrayList<>();
        for (byte[] u : shuffledU) {
            EcGroupOps.validatePoint(u);
            byte[] uPrime = EcGroupOps.scalarMul(u, invK0);
            if (!EcGroupOps.containsCanonical(receiverWKeys, uPrime)) {
                diff.add(EcGroupOps.encodePoint(uPrime));
            }
        }
        return diff;
    }

    public static List<byte[]> filterDifferenceOptimized(
        byte[][] shuffledU,
        byte[] invK0,
        Set<EcGroupOps.CanonicalPoint> receiverWKeys,
        SmallEcElligatorPsuConfig config
    ) {
        if (!config.isParallelEc() || shuffledU.length < config.getParallelThreshold()) {
            return filterDifference(shuffledU, invK0, receiverWKeys);
        }

        LOG.info(
            "SMALL_EC parallel filterDifference active: n={}, threshold={}",
            shuffledU.length, config.getParallelThreshold()
        );
        return IntStream.range(0, shuffledU.length).parallel()
            .mapToObj(i -> {
                byte[] u = shuffledU[i];
                EcGroupOps.validatePoint(u);
                byte[] uPrime = EcGroupOps.scalarMul(u, invK0);
                if (!EcGroupOps.containsCanonical(receiverWKeys, uPrime)) {
                    return EcGroupOps.encodePoint(uPrime);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static List<byte[]> filterDifferenceByFingerprint(
        byte[][] shuffledU,
        byte[] invK0,
        Set<FingerprintKey> receiverWFingerprints,
        int fingerprintBitLength,
        SmallEcElligatorPsuConfig.FingerprintMethod method
    ) {
        List<byte[]> diff = new ArrayList<>();
        int fpBytes = SmallEcFingerprintUtils.fingerprintByteLength(fingerprintBitLength);

        for (byte[] u : shuffledU) {
            EcGroupOps.validatePoint(u);
            byte[] uPrime = EcGroupOps.scalarMul(u, invK0);
            byte[] fp = SmallEcFingerprintUtils.fingerprint(uPrime, fingerprintBitLength, method);
            Preconditions.checkArgument(fp.length == fpBytes);

            if (!receiverWFingerprints.contains(new FingerprintKey(fp))) {
                diff.add(EcGroupOps.encodePoint(uPrime));
            }
        }

        return diff;
    }

    public static List<byte[]> filterDifferenceByFingerprint64(
        byte[][] shuffledU,
        byte[] invK0,
        Set<Long> receiverWFingerprints,
        SmallEcElligatorPsuConfig.FingerprintMethod method
    ) {
        List<byte[]> diff = new ArrayList<>();
        for (byte[] u : shuffledU) {
            EcGroupOps.validatePoint(u);
            byte[] uPrime = EcGroupOps.scalarMul(u, invK0);
            long fp = SmallEcFingerprintUtils.fingerprint64Long(uPrime, method);
            if (!receiverWFingerprints.contains(fp)) {
                diff.add(EcGroupOps.encodePoint(uPrime));
            }
        }
        return diff;
    }

    public static List<byte[]> filterDifferenceByFingerprint64Optimized(
        byte[][] shuffledU,
        byte[] invK0,
        Set<Long> receiverWFingerprints,
        SmallEcElligatorPsuConfig.FingerprintMethod method,
        SmallEcElligatorPsuConfig config
    ) {
        if (!config.isParallelEc() || shuffledU.length < config.getParallelThreshold()) {
            return filterDifferenceByFingerprint64(shuffledU, invK0, receiverWFingerprints, method);
        }

        LOG.info(
            "SMALL_EC parallel filterDifferenceByFingerprint64 active: n={}, threshold={}",
            shuffledU.length, config.getParallelThreshold()
        );
        return IntStream.range(0, shuffledU.length).parallel()
            .mapToObj(i -> {
                byte[] u = shuffledU[i];
                EcGroupOps.validatePoint(u);
                byte[] uPrime = EcGroupOps.scalarMul(u, invK0);
                long fp = SmallEcFingerprintUtils.fingerprint64Long(uPrime, method);
                if (!receiverWFingerprints.contains(fp)) {
                    return EcGroupOps.encodePoint(uPrime);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static List<byte[]> filterDifferenceByFingerprintOptimized(
        byte[][] shuffledU,
        byte[] invK0,
        Set<FingerprintKey> receiverWFingerprints,
        int fingerprintBitLength,
        SmallEcElligatorPsuConfig.FingerprintMethod method,
        SmallEcElligatorPsuConfig config
    ) {
        if (!config.isParallelEc() || shuffledU.length < config.getParallelThreshold()) {
            return filterDifferenceByFingerprint(
                shuffledU, invK0, receiverWFingerprints, fingerprintBitLength, method
            );
        }

        LOG.info(
            "SMALL_EC parallel filterDifferenceByFingerprint active: n={}, threshold={}",
            shuffledU.length, config.getParallelThreshold()
        );
        return IntStream.range(0, shuffledU.length).parallel()
            .mapToObj(i -> {
                byte[] u = shuffledU[i];
                EcGroupOps.validatePoint(u);
                byte[] uPrime = EcGroupOps.scalarMul(u, invK0);
                byte[] fp = SmallEcFingerprintUtils.fingerprint(uPrime, fingerprintBitLength, method);
                if (!receiverWFingerprints.contains(new FingerprintKey(fp))) {
                    return EcGroupOps.encodePoint(uPrime);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    public static void assertItemLength(int elementByteLength) {
        if (elementByteLength != SmallEcConstants.ITEM_BYTE_LENGTH) {
            throw new IllegalArgumentException(
                "Ours requires element_byte_length = " + SmallEcConstants.ITEM_BYTE_LENGTH
            );
        }
    }
}
