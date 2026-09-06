package edu.alibaba.libpsu.core.set;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fixed-length set element encoding, validation, and deduplication (union semantics).
 */
public final class SetElementUtils {
    private SetElementUtils() {
    }

    public static void validateElementByteLength(int elementByteLength) {
        Preconditions.checkArgument(elementByteLength > 0, "element_byte_length must be positive");
    }

    public static void validateProtocolElementByteLength(int elementByteLength) {
        MathPreconditions.checkGreaterOrEqual(
            "elementByteLength", elementByteLength, CommonConstants.STATS_BYTE_LENGTH
        );
    }

    public static void checkElementSizeInRange(String name, int size, int maxSize, int minimumSize) {
        MathPreconditions.checkGreaterOrEqual(name, size, minimumSize);
        MathPreconditions.checkLessOrEqual(name, size, maxSize);
    }

    public static ByteBuffer createBotElement(int elementByteLength) {
        validateElementByteLength(elementByteLength);
        byte[] botElementByteArray = new byte[elementByteLength];
        Arrays.fill(botElementByteArray, (byte) 0xFF);
        return ByteBuffer.wrap(botElementByteArray);
    }

    public static byte[] toFixedByteArray(ByteBuffer buffer, int elementByteLength) {
        Preconditions.checkNotNull(buffer);
        ByteBuffer dup = buffer.duplicate();
        Preconditions.checkArgument(
            dup.remaining() == elementByteLength,
            "element length %s != expected %s", dup.remaining(), elementByteLength
        );
        byte[] item = new byte[elementByteLength];
        dup.get(item);
        return item;
    }

    /**
     * Deduplicate set elements by raw byte content (set union semantics).
     */
    public static ArrayList<ByteBuffer> deduplicateElements(Set<ByteBuffer> elementSet, int elementByteLength) {
        validateElementByteLength(elementByteLength);
        Preconditions.checkNotNull(elementSet, "elementSet");
        Map<ByteBuffer, ByteBuffer> uniqueElements = new LinkedHashMap<>(elementSet.size());
        for (ByteBuffer xi : elementSet) {
            byte[] bytes = toFixedByteArray(xi, elementByteLength);
            ByteBuffer key = ByteBuffer.wrap(bytes);
            uniqueElements.putIfAbsent(key, ByteBuffer.wrap(bytes.clone()));
        }
        return new ArrayList<>(uniqueElements.values());
    }

    public static ArrayList<ByteBuffer> normalizeProtocolElements(
        Set<ByteBuffer> elementSet, int elementByteLength, ByteBuffer botElementByteBuffer, String elementName
    ) {
        validateProtocolElementByteLength(elementByteLength);
        byte[] botElement = toFixedByteArray(botElementByteBuffer, elementByteLength);
        ArrayList<ByteBuffer> normalized = deduplicateElements(elementSet, elementByteLength);
        for (ByteBuffer element : normalized) {
            Preconditions.checkArgument(
                !Arrays.equals(toFixedByteArray(element, elementByteLength), botElement),
                "%s must not equal bot element", elementName
            );
        }
        return normalized;
    }
}
