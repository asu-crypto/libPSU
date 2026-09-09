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

    /**
     * Temporary all-{@code 0xFF} filler for legacy callers. Prefer
     * {@link #sampleUnusedPaddingElement} or {@code insertPaddingItems(SecureRandom)}.
     * All-{@code 0xFF} is a valid user-domain element.
     *
     * @deprecated Do not use as a reserved domain sentinel.
     */
    @Deprecated
    public static ByteBuffer createBotElement(int elementByteLength) {
        validateElementByteLength(elementByteLength);
        byte[] botElementByteArray = new byte[elementByteLength];
        Arrays.fill(botElementByteArray, (byte) 0xFF);
        return ByteBuffer.wrap(botElementByteArray);
    }

    /**
     * Samples a fixed-length element that is not already present in {@code occupied}.
     * Used only as a stored filler for hash-bin APIs that require a value; padding is
     * still identified by {@code HashBinEntry.DUMMY_ITEM_HASH_INDEX}.
     */
    public static ByteBuffer sampleUnusedPaddingElement(
        Set<ByteBuffer> occupied, int elementByteLength, java.security.SecureRandom secureRandom
    ) {
        validateElementByteLength(elementByteLength);
        Preconditions.checkNotNull(occupied, "occupied");
        Preconditions.checkNotNull(secureRandom, "secureRandom");
        for (int attempt = 0; attempt < 10_000; attempt++) {
            byte[] bytes = new byte[elementByteLength];
            secureRandom.nextBytes(bytes);
            ByteBuffer candidate = ByteBuffer.wrap(bytes);
            if (!occupied.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("unable to sample unused padding element");
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

    /**
     * Normalize protocol input elements: validate length and deduplicate.
     * All byte strings of the configured length are valid, including all-{@code 0xFF}.
     */
    public static ArrayList<ByteBuffer> normalizeProtocolElements(
        Set<ByteBuffer> elementSet, int elementByteLength, String elementName
    ) {
        validateProtocolElementByteLength(elementByteLength);
        Preconditions.checkNotNull(elementName, "elementName");
        return deduplicateElements(elementSet, elementByteLength);
    }

    /**
     * @deprecated Prefer {@link #normalizeProtocolElements(Set, int, String)}. The bot argument is
     *             ignored; all-{@code 0xFF} is a valid domain element.
     */
    @Deprecated
    public static ArrayList<ByteBuffer> normalizeProtocolElements(
        Set<ByteBuffer> elementSet, int elementByteLength, ByteBuffer botElementByteBuffer, String elementName
    ) {
        if (botElementByteBuffer != null) {
            toFixedByteArray(botElementByteBuffer, elementByteLength);
        }
        return normalizeProtocolElements(elementSet, elementByteLength, elementName);
    }
}
