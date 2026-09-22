package edu.alibaba.mpc4j.s2pc.pso.main.psu;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PSU_BLACK_IP element encoding utilities.
 *
 * <p>IPv4 addresses are mapped into a fixed 16-byte domain-separated encoding so they satisfy
 * {@code elementByteLength >= CommonConstants.BLOCK_BYTE_LENGTH} / stats-length requirements.
 */
public final class PsuBlackIpConfigUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsuBlackIpConfigUtils.class);

    /** Encoded element length (AES block / CommonConstants.BLOCK_BYTE_LENGTH). */
    static final int IP_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;

    /**
     * First 12 bytes of SHA-256("libPSU:IPv4:v1") — domain separation prefix.
     */
    static final byte[] IPV4_DOMAIN_PREFIX = computeIpv4DomainPrefix();

    private PsuBlackIpConfigUtils() {
    }

    private static byte[] computeIpv4DomainPrefix() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest("libPSU:IPv4:v1".getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(digest, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Encode a dotted-decimal IPv4 string into a 16-byte domain-separated element.
     * Leading zeros in octets are accepted and normalized by {@link Integer#parseInt}.
     */
    static ByteBuffer encodeIpv4(String text) {
        String normalized = text.trim();
        String[] parts = normalized.split("\\.", -1);
        Preconditions.checkArgument(parts.length == 4, "Invalid IPv4 address: %s", text);

        byte[] encoded = new byte[IP_BYTE_LENGTH];
        System.arraycopy(IPV4_DOMAIN_PREFIX, 0, encoded, 0, 12);

        for (int i = 0; i < 4; i++) {
            Preconditions.checkArgument(
                parts[i].matches("[0-9]{1,3}"),
                "Invalid IPv4 octet '%s' in %s", parts[i], text
            );
            int octet = Integer.parseInt(parts[i]);
            Preconditions.checkArgument(
                octet >= 0 && octet <= 255,
                "IPv4 octet out of range in %s: %s", text, octet
            );
            encoded[12 + i] = (byte) octet;
        }
        return ByteBuffer.wrap(encoded);
    }

    /**
     * Read a black-IP set from a text file (one IPv4 address per non-blank line).
     */
    static Set<ByteBuffer> readBlackIpSet(String file) throws IOException {
        Path filePath = Paths.get(file);
        LOGGER.info("The given black IP file path = {}", filePath.toAbsolutePath());
        List<String> lines = Files.readAllLines(filePath);
        Set<ByteBuffer> out = new HashSet<>(lines.size());
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
            String raw = lines.get(lineNo);
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            try {
                out.add(encodeIpv4(raw));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "Invalid IPv4 on line " + (lineNo + 1) + ": " + raw, e
                );
            }
        }
        return out;
    }
}
