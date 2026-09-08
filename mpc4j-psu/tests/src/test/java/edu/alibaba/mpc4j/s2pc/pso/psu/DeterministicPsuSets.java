package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.libpsu.core.set.SetElementUtils;
import org.junit.Assert;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic fixed-length PSU element sets for small end-to-end matrix tests.
 * <p>
 * Domains: shared=0, server-only=1, client-only=2. Encoded as {@code byte[0] = domain + 1}
 * (never all-zero) plus a big-endian index; never equal to the all-{@code 0xFF} BOT sentinel.
 * </p>
 */
public final class DeterministicPsuSets {
    public static final int DOMAIN_SHARED = 0;
    public static final int DOMAIN_SERVER_ONLY = 1;
    public static final int DOMAIN_CLIENT_ONLY = 2;

    private DeterministicPsuSets() {
    }

    /**
     * Built sets and ground truth for a single (serverSize, clientSize, intersectionSize) case.
     */
    public static final class SetsOf {
        public final Set<ByteBuffer> serverSet;
        public final Set<ByteBuffer> clientSet;
        public final Set<ByteBuffer> expectedUnion;
        public final int intersectionSize;
        public final int elementByteLength;

        SetsOf(
            Set<ByteBuffer> serverSet,
            Set<ByteBuffer> clientSet,
            Set<ByteBuffer> expectedUnion,
            int intersectionSize,
            int elementByteLength
        ) {
            this.serverSet = serverSet;
            this.clientSet = clientSet;
            this.expectedUnion = expectedUnion;
            this.intersectionSize = intersectionSize;
            this.elementByteLength = elementByteLength;
        }
    }

    public static SetsOf build(int serverSize, int clientSize, int intersectionSize, int elementByteLength) {
        Assert.assertTrue("elementByteLength >= 5", elementByteLength >= 5);
        Assert.assertTrue(intersectionSize >= 0);
        Assert.assertTrue(intersectionSize <= Math.min(serverSize, clientSize));
        Assert.assertTrue(serverSize >= 0);
        Assert.assertTrue(clientSize >= 0);

        Set<ByteBuffer> serverSet = new HashSet<>(serverSize);
        Set<ByteBuffer> clientSet = new HashSet<>(clientSize);

        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer shared = encode(DOMAIN_SHARED, i, elementByteLength);
            serverSet.add(copyOf(shared));
            clientSet.add(copyOf(shared));
        }
        for (int i = 0; i < serverSize - intersectionSize; i++) {
            serverSet.add(encode(DOMAIN_SERVER_ONLY, i, elementByteLength));
        }
        for (int i = 0; i < clientSize - intersectionSize; i++) {
            clientSet.add(encode(DOMAIN_CLIENT_ONLY, i, elementByteLength));
        }

        Assert.assertEquals(serverSize, serverSet.size());
        Assert.assertEquals(clientSize, clientSet.size());

        Set<ByteBuffer> expectedUnion = new HashSet<>(serverSet);
        expectedUnion.addAll(clientSet);
        Assert.assertEquals(serverSize + clientSize - intersectionSize, expectedUnion.size());

        for (ByteBuffer e : expectedUnion) {
            assertNotBotOrZero(e, elementByteLength);
        }

        return new SetsOf(serverSet, clientSet, expectedUnion, intersectionSize, elementByteLength);
    }

    public static ByteBuffer encode(int domain, int index, int elementByteLength) {
        Assert.assertTrue(domain >= DOMAIN_SHARED && domain <= DOMAIN_CLIENT_ONLY);
        Assert.assertTrue(elementByteLength >= 5);
        byte[] bytes = new byte[elementByteLength];
        bytes[0] = (byte) (domain + 1);
        ByteBuffer.wrap(bytes).putInt(1, index);
        ByteBuffer element = ByteBuffer.wrap(bytes);
        assertNotBotOrZero(element, elementByteLength);
        return element;
    }

    public static void assertUnionEqual(Set<ByteBuffer> expected, Set<ByteBuffer> actual, int elementByteLength) {
        Assert.assertNotNull(actual);
        Assert.assertEquals(expected.size(), actual.size());
        for (ByteBuffer e : expected) {
            Assert.assertTrue("missing expected element", unionContains(actual, e));
        }
        for (ByteBuffer a : actual) {
            assertNotBotOrZero(a, elementByteLength);
            Assert.assertTrue("unexpected element in union", unionContains(expected, a));
        }
    }

    public static boolean unionContains(Set<ByteBuffer> union, ByteBuffer needle) {
        byte[] target = toBytes(needle);
        for (ByteBuffer b : union) {
            if (Arrays.equals(target, toBytes(b))) {
                return true;
            }
        }
        return false;
    }

    public static byte[] toBytes(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] item = new byte[dup.remaining()];
        dup.get(item);
        return item;
    }

    public static void assertNotBotOrZero(ByteBuffer element, int elementByteLength) {
        byte[] bytes = SetElementUtils.toFixedByteArray(element, elementByteLength);
        byte[] bot = SetElementUtils.createBotElement(elementByteLength).array();
        Assert.assertFalse("element must not be all-zero", isAllZero(bytes));
        Assert.assertFalse("element must not equal BOT sentinel", Arrays.equals(bytes, bot));
    }

    private static boolean isAllZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static ByteBuffer copyOf(ByteBuffer src) {
        return ByteBuffer.wrap(Objects.requireNonNull(toBytes(src)).clone());
    }
}
