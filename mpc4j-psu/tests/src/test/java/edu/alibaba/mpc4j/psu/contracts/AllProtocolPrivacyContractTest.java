package edu.alibaba.mpc4j.psu.contracts;

import edu.alibaba.libpsu.api.OutputModel;
import edu.alibaba.libpsu.api.ProtocolFunctionality;
import edu.alibaba.libpsu.api.ProtocolInfo;
import edu.alibaba.libpsu.api.ProtocolMetadataRegistry;
import edu.alibaba.libpsu.factory.ProtocolRegistry;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Privacy API contracts that apply across all registered protocols. */
public class AllProtocolPrivacyContractTest {
    @Test
    public void testOutputDisclosureIsExplicitAndFactoryRoutingMatchesIt() {
        for (ProtocolInfo info : ProtocolMetadataRegistry.allEntries()) {
            Assert.assertNotNull(info.getProtocolName(), info.getOutputModel());
            if (info.getFunctionality() != ProtocolFunctionality.PSU) {
                continue;
            }
            Properties properties = new Properties();
            properties.setProperty("silent_cot", "true");
            properties.setProperty("rosn_type", "GMR21_NET");
            PsuConfig config = ProtocolRegistry.createPsuConfig(info.getProtocolName(), properties);
            Assert.assertEquals(
                info.getProtocolName(), OutputModel.isTwoSided(info.getOutputModel()),
                ProtocolRegistry.usesTwoSidedPublicFactory(config)
            );
            if (info.getOutputModel() == OutputModel.LEAKAGE_BASELINE) {
                Assert.assertEquals("Ours", info.getProtocolName());
            } else {
                Assert.assertNotEquals(info.getProtocolName(), OutputModel.LEAKAGE_BASELINE, info.getOutputModel());
            }
        }
    }

    @Test
    public void testReceiverOutputCannotLeakThroughMutableAliases() {
        byte[] secret = new byte[]{1, 2, 3, 4};
        ByteBuffer sourceElement = ByteBuffer.wrap(secret);
        Set<ByteBuffer> source = new HashSet<>();
        source.add(sourceElement);
        PsuClientOutput output = new PsuClientOutput(source, 0);

        secret[0] = 99;
        sourceElement.put(1, (byte) 99);
        source.clear();
        Set<ByteBuffer> firstRead = output.getUnion();
        Assert.assertArrayEquals(new byte[]{1, 2, 3, 4}, bytes(firstRead.iterator().next()));
        Assert.assertThrows(UnsupportedOperationException.class, firstRead::clear);

        ByteBuffer returned = firstRead.iterator().next();
        Assert.assertTrue(returned.isReadOnly());
        Assert.assertThrows(java.nio.ReadOnlyBufferException.class, () -> returned.put(0, (byte) 7));
        Assert.assertArrayEquals(new byte[]{1, 2, 3, 4}, bytes(output.getUnion().iterator().next()));
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] result = new byte[copy.remaining()];
        copy.get(result);
        return result;
    }
}
