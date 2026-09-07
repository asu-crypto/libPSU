package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * Maps set elements into {@code Z_M} for IBLT sum fields and tagged OT/peel wire values.
 * <p>
 * Ring modulus is the power-of-two {@code M = 2^(8·zmByteLength)} (paper/reference-compatible
 * fixed-width unsigned wraparound). OT and peel payloads use a one-byte tag so {@code BOT} is not
 * confused with a legitimate ring element:
 * <ul>
 *   <li>{@code tag = 0}: BOT</li>
 *   <li>{@code tag = 1}: canonical {@code Z_M} value of length {@code zmByteLength}</li>
 * </ul>
 */
public final class Pt26Zm {
    public static final byte TAG_BOT = 0;
    public static final byte TAG_VALUE = 1;

    private Pt26Zm() {
    }

    public static BigInteger modulusFor(int zmByteLength) {
        if (zmByteLength < 2) {
            throw new IllegalArgumentException("zmByteLength must be >= 2");
        }
        return BigInteger.ONE.shiftLeft(zmByteLength * 8);
    }

    public static int wireByteLength(int zmByteLength) {
        return 1 + zmByteLength;
    }

    public static int wireByteLength(Pt26IbltParams params) {
        return wireByteLength(params.getZmByteLength());
    }

    /**
     * Encode a set element as a fixed-width unsigned {@code Z_M} value (leading zero byte when
     * {@code zmByteLength = elementByteLength + 1}).
     */
    public static byte[] encodeElement(ByteBuffer element, Pt26IbltParams params) {
        byte[] raw = elementBytes(element);
        if (raw.length > params.getZmByteLength()) {
            throw new IllegalArgumentException("element longer than Z_M encoding width");
        }
        BigInteger x = new BigInteger(1, raw);
        return toFixedLength(x, params.getZmByteLength(), params.modulus());
    }

    public static ByteBuffer decodeElement(byte[] encoded, int elementByteLength, Pt26IbltParams params) {
        BigInteger x = fromFixedLength(encoded, params);
        byte[] raw = x.toByteArray();
        byte[] out = new byte[elementByteLength];
        if (raw.length >= elementByteLength) {
            // Drop leading sign byte if present; take least-significant elementByteLength bytes.
            System.arraycopy(raw, raw.length - elementByteLength, out, 0, elementByteLength);
        } else {
            System.arraycopy(raw, 0, out, elementByteLength - raw.length, raw.length);
        }
        return ByteBuffer.wrap(out);
    }

    public static boolean isValidEncoding(byte[] encoded, ByteBuffer element, Pt26IbltParams params) {
        return Arrays.equals(encodeElement(element, params), encoded);
    }

    public static byte[] add(byte[] a, byte[] b, Pt26IbltParams params) {
        BigInteger sum = fromFixedLength(a, params).add(fromFixedLength(b, params)).mod(params.modulus());
        return toFixedLength(sum, params.getZmByteLength(), params.modulus());
    }

    public static byte[] subtract(byte[] a, byte[] b, Pt26IbltParams params) {
        BigInteger diff = fromFixedLength(a, params).subtract(fromFixedLength(b, params)).mod(params.modulus());
        return toFixedLength(diff, params.getZmByteLength(), params.modulus());
    }

    public static boolean equals(byte[] a, byte[] b) {
        return BytesUtils.equals(a, b);
    }

    public static byte[] zero(Pt26IbltParams params) {
        return new byte[params.getZmByteLength()];
    }

    public static boolean isZero(byte[] value, Pt26IbltParams params) {
        return fromFixedLength(value, params).equals(BigInteger.ZERO);
    }

    public static byte[] encodeWireBot(Pt26IbltParams params) {
        return encodeWireBot(params.getZmByteLength());
    }

    public static byte[] encodeWireBot(int zmByteLength) {
        return new byte[wireByteLength(zmByteLength)];
    }

    public static byte[] encodeWireValue(byte[] zmValue, Pt26IbltParams params) {
        return encodeWireValue(zmValue, params.getZmByteLength());
    }

    public static byte[] encodeWireValue(byte[] zmValue, int zmByteLength) {
        if (zmValue == null || zmValue.length != zmByteLength) {
            throw new IllegalArgumentException("Z_M value must be exactly " + zmByteLength + " bytes");
        }
        byte[] out = new byte[wireByteLength(zmByteLength)];
        out[0] = TAG_VALUE;
        System.arraycopy(zmValue, 0, out, 1, zmByteLength);
        return out;
    }

    /**
     * Decode a tagged OT/peel wire value. Empty optional means BOT.
     */
    public static Optional<byte[]> decodeWire(byte[] wire, Pt26IbltParams params) {
        return decodeWire(wire, params.getZmByteLength());
    }

    public static Optional<byte[]> decodeWire(byte[] wire, int zmByteLength) {
        if (wire == null || wire.length != wireByteLength(zmByteLength)) {
            throw new IllegalArgumentException(
                "wire payload must be exactly " + wireByteLength(zmByteLength) + " bytes"
            );
        }
        if (wire[0] == TAG_BOT) {
            for (int i = 1; i < wire.length; i++) {
                if (wire[i] != 0) {
                    throw new IllegalArgumentException("BOT wire payload must be zero-padded");
                }
            }
            return Optional.empty();
        }
        if (wire[0] != TAG_VALUE) {
            throw new IllegalArgumentException("unknown Z_M wire tag: " + (wire[0] & 0xFF));
        }
        return Optional.of(Arrays.copyOfRange(wire, 1, wire.length));
    }

    public static boolean isWireBot(byte[] wire, Pt26IbltParams params) {
        return decodeWire(wire, params).isEmpty();
    }

    private static byte[] elementBytes(ByteBuffer element) {
        byte[] raw = new byte[element.remaining()];
        int pos = element.position();
        element.get(raw);
        element.position(pos);
        return raw;
    }

    static BigInteger fromFixedLength(byte[] value, Pt26IbltParams params) {
        if (value == null || value.length != params.getZmByteLength()) {
            throw new IllegalArgumentException(
                "Z_M encoding must be exactly " + params.getZmByteLength() + " bytes"
            );
        }
        BigInteger x = new BigInteger(1, value);
        if (x.compareTo(params.modulus()) >= 0) {
            throw new IllegalArgumentException("Z_M encoding is not canonical (value >= M)");
        }
        return x;
    }

    static byte[] toFixedLength(BigInteger value, int byteLength, BigInteger modulus) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Z_M value must be non-negative");
        }
        if (value.compareTo(modulus) >= 0) {
            throw new IllegalArgumentException("Z_M value must be < M");
        }
        byte[] raw = value.toByteArray();
        byte[] out = new byte[byteLength];
        if (raw.length > byteLength + 1) {
            throw new IllegalArgumentException("Z_M value exceeds encoding width");
        }
        if (raw.length == byteLength + 1) {
            // Leading sign byte 0x00 from BigInteger.toByteArray().
            if (raw[0] != 0) {
                throw new IllegalArgumentException("unexpected sign byte in Z_M encoding");
            }
            System.arraycopy(raw, 1, out, 0, byteLength);
            return out;
        }
        System.arraycopy(raw, 0, out, byteLength - raw.length, raw.length);
        return out;
    }
}
