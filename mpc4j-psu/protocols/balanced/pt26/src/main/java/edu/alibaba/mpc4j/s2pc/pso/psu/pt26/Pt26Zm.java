package edu.alibaba.mpc4j.s2pc.pso.psu.pt26;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Maps set elements into Z_M (§2) for IBLT sum fields.
 */
public class Pt26Zm {
    private Pt26Zm() {
        // empty
    }

    public static byte[] encodeElement(ByteBuffer element, Pt26IbltParams params) {
        byte[] raw = elementBytes(element);
        BigInteger x = new BigInteger(1, raw).mod(params.modulus());
        return toFixedLength(x, params.getZmByteLength());
    }

    public static ByteBuffer decodeElement(byte[] encoded, int elementByteLength, Pt26IbltParams params) {
        BigInteger x = toBigInteger(encoded, params);
        byte[] raw = x.toByteArray();
        byte[] out = new byte[elementByteLength];
        Arrays.fill(out, (byte) 0);
        if (raw.length >= elementByteLength) {
            System.arraycopy(raw, raw.length - elementByteLength, out, 0, elementByteLength);
        } else {
            System.arraycopy(raw, 0, out, elementByteLength - raw.length, raw.length);
        }
        return ByteBuffer.wrap(out);
    }

    public static boolean isValidEncoding(byte[] encoded, ByteBuffer element, Pt26IbltParams params) {
        return Arrays.equals(encodeElement(element, params), encoded);
    }

    private static byte[] elementBytes(ByteBuffer element) {
        byte[] raw = new byte[element.remaining()];
        int pos = element.position();
        element.get(raw);
        element.position(pos);
        return raw;
    }

    public static byte[] add(byte[] a, byte[] b, Pt26IbltParams params) {
        BigInteger sum = toBigInteger(a, params).add(toBigInteger(b, params)).mod(params.modulus());
        return toFixedLength(sum, params.getZmByteLength());
    }

    public static byte[] subtract(byte[] a, byte[] b, Pt26IbltParams params) {
        BigInteger diff = toBigInteger(a, params).subtract(toBigInteger(b, params)).mod(params.modulus());
        return toFixedLength(diff, params.getZmByteLength());
    }

    public static boolean equals(byte[] a, byte[] b) {
        return BytesUtils.equals(a, b);
    }

    public static byte[] zero(Pt26IbltParams params) {
        return toFixedLength(BigInteger.ZERO, params.getZmByteLength());
    }

    public static boolean isZero(byte[] value, Pt26IbltParams params) {
        return toBigInteger(value, params).equals(BigInteger.ZERO);
    }

    private static BigInteger toBigInteger(byte[] value, Pt26IbltParams params) {
        BigInteger x = new BigInteger(1, value);
        return x.mod(params.modulus());
    }

    private static byte[] toFixedLength(BigInteger value, int byteLength) {
        byte[] raw = value.toByteArray();
        if (raw.length == byteLength) {
            return raw;
        }
        byte[] out = new byte[byteLength];
        if (raw.length > byteLength) {
            System.arraycopy(raw, raw.length - byteLength, out, 0, byteLength);
        } else {
            System.arraycopy(raw, 0, out, byteLength - raw.length, raw.length);
        }
        return out;
    }
}
