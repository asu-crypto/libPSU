package edu.alibaba.mpc4j.s2pc.pso.psu.smallec.crypto;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.s2pc.pso.psu.pgt26.mapping.Pgt26FeistelPrp256;
import edu.alibaba.mpc4j.s2pc.pso.psu.smallec.SmallEcConstants;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s256;

/**
 * 256-bit 3-round Feistel PRP (AES round function) implementing {@link IdealPermutation}.
 */
public final class FeistelIdealPermutation implements IdealPermutation {
    /** Domain separator for protocol-wide Π key derivation. */
    public static final String PERM_DOMAIN_V1 = "SMALL_EC_ELLIGATOR_PSU_PERM_V1";

    private final Pgt26FeistelPrp256 prp;

    public FeistelIdealPermutation(byte[] masterKey16) {
        Preconditions.checkArgument(masterKey16.length == 16);
        prp = new Pgt26FeistelPrp256(masterKey16);
    }

    /** Deterministic Π key shared by both parties (no per-session seed on the wire). */
    public static FeistelIdealPermutation protocolInstance() {
        Blake2s256 h = new Blake2s256();
        h.update(PERM_DOMAIN_V1.getBytes(StandardCharsets.US_ASCII));
        byte[] digest = h.digest();
        return new FeistelIdealPermutation(Pgt26FeistelPrp256.deriveProtocolKey(digest));
    }

    @Override
    public int domainByteLength() {
        return SmallEcConstants.DOMAIN_GAMMA_BYTES;
    }

    @Override
    public byte[] permute(byte[] bytesGamma) {
        byte[] block = copyDomain(bytesGamma);
        prp.encryptBlock(block);
        return block;
    }

    @Override
    public byte[] inversePermute(byte[] bytesGamma) {
        byte[] block = copyDomain(bytesGamma);
        prp.decryptBlock(block);
        return block;
    }

    private static byte[] copyDomain(byte[] bytesGamma) {
        Preconditions.checkArgument(bytesGamma.length == SmallEcConstants.DOMAIN_GAMMA_BYTES);
        return Arrays.copyOf(bytesGamma, SmallEcConstants.DOMAIN_GAMMA_BYTES);
    }
}
