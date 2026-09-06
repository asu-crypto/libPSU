package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26FeistelPrp256;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26InvertibleMap;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

/**
 * HashDH map for Small-EC/Elligator PSU.
 * <p>
 * Strict invertible image: {@code M2P(Π(enc(x)))} on the full Ed25519 group (cofactor 8).
 * HashDH wire points are the prime-order subgroup image {@code [8]·M2P(...)} via
 * {@link EcGroupOps#clearCofactor}. Recovery applies {@link EcGroupOps#removeCofactor}
 * once before {@code M2P⁻¹ ∘ Π⁻¹}.
 * </p>
 */
public final class ElligatorCodec {
    private final Pgt26FeistelPrp256 perm;

    public ElligatorCodec(Pgt26FeistelPrp256 perm) {
        this.perm = perm;
    }

    public static ElligatorCodec protocolInstance() {
        Blake2s256 h = new Blake2s256();
        h.update(FeistelIdealPermutation.PERM_DOMAIN_V1.getBytes(StandardCharsets.US_ASCII));
        byte[] key = Pgt26FeistelPrp256.deriveProtocolKey(h.digest());
        return new ElligatorCodec(new Pgt26FeistelPrp256(key));
    }

    public byte[] encodeItem(byte[] item) {
        Preconditions.checkArgument(item.length == SmallEcConstants.ITEM_BYTE_LENGTH);
        return Pgt26InvertibleMap.padItem(item, SmallEcConstants.ITEM_BYTE_LENGTH);
    }

    /**
     * Maps an item to the prime-order subgroup HashDH point {@code [8]·M2P(Π(enc(x)))}.
     */
    public byte[] mapToPoint(byte[] item) {
        Preconditions.checkArgument(item.length == SmallEcConstants.ITEM_BYTE_LENGTH);
        byte[] raw = Pgt26InvertibleMap.mapToPointStrict(item, perm, SmallEcConstants.ITEM_BYTE_LENGTH);
        EcGroupOps.validatePoint(raw);
        // clearCofactor validates non-identity subgroup image.
        return EcGroupOps.clearCofactor(raw);
    }

    /**
     * Inverse of {@link #mapToPoint}: remove cofactor representation, then strict M2P⁻¹.
     */
    public Optional<byte[]> inversePointToItem(byte[] point) {
        EcGroupOps.validatePoint(point);
        byte[] raw = EcGroupOps.removeCofactor(point);
        return Pgt26InvertibleMap.recoverFromPointStrict(
            raw, perm, SmallEcConstants.ITEM_BYTE_LENGTH
        );
    }

    public byte[] inversePointToItemStrict(byte[] point) throws MpcAbortException {
        Optional<byte[]> item = inversePointToItem(point);
        if (item.isEmpty()) {
            throw new MpcAbortException("H_EC inverse failed: point is not in the strict H image");
        }
        return item.get();
    }

    public boolean hasValidPadding(byte[] item) {
        return Pgt26InvertibleMap.hasValidPadding(item, SmallEcConstants.ITEM_BYTE_LENGTH);
    }

    /** Test helper: strict M2P image without cofactor clearing. */
    byte[] mapToPointRawForTest(byte[] item) {
        Preconditions.checkArgument(item.length == SmallEcConstants.ITEM_BYTE_LENGTH);
        byte[] point = Pgt26InvertibleMap.mapToPointStrict(item, perm, SmallEcConstants.ITEM_BYTE_LENGTH);
        EcGroupOps.validatePoint(point);
        return point;
    }

    /** Test helper: strict inverse without cofactor removal. */
    Optional<byte[]> inversePointToItemRawForTest(byte[] point) {
        EcGroupOps.validatePoint(point);
        return Pgt26InvertibleMap.recoverFromPointStrict(
            point, perm, SmallEcConstants.ITEM_BYTE_LENGTH
        );
    }
}
