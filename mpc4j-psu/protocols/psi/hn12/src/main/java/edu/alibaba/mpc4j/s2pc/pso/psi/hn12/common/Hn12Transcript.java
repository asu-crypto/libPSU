package edu.alibaba.mpc4j.s2pc.pso.psi.hn12.common;

import edu.alibaba.mpc4j.common.tool.crypto.hash.Hash;
import edu.alibaba.mpc4j.common.tool.crypto.hash.HashFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * Fiat–Shamir transcript with domain separation (protocol id, proof type, session, group).
 */
public final class Hn12Transcript {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Hash hash = HashFactory.createInstance(HashFactory.HashType.JDK_SHA256, 32);

    public Hn12Transcript(String protocolId, String proofType, int partyId, int sessionId, byte[] groupId) {
        appendBytes("HN12".getBytes(StandardCharsets.UTF_8));
        appendBytes(protocolId.getBytes(StandardCharsets.UTF_8));
        appendBytes(proofType.getBytes(StandardCharsets.UTF_8));
        appendBytes(new byte[] {(byte) partyId});
        appendBytes(new byte[] {
            (byte) sessionId, (byte) (sessionId >>> 8), (byte) (sessionId >>> 16), (byte) (sessionId >>> 24)
        });
        appendBytes(groupId);
    }

    public void appendBytes(byte[]... parts) {
        for (byte[] p : parts) {
            try {
                buffer.write(IntLen(p.length));
                buffer.write(p);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public void appendBigInteger(BigInteger q, BigInteger... values) {
        for (BigInteger v : values) {
            byte[] enc = v.mod(q).toByteArray();
            appendBytes(enc);
        }
    }

    public BigInteger challenge(BigInteger q) {
        byte[] digest = hash.digestToBytes(buffer.toByteArray());
        BigInteger c = new BigInteger(1, digest).mod(q);
        return c.signum() == 0 ? BigInteger.ONE : c;
    }

    private static byte[] IntLen(int len) {
        return new byte[] {(byte) (len >>> 24), (byte) (len >>> 16), (byte) (len >>> 8), (byte) len};
    }
}
