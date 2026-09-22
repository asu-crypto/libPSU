package edu.alibaba.libpsu.core.set;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class SetElementUtilsTest {
    @Test
    public void deduplicateByBytes() {
        Set<ByteBuffer> in = new HashSet<>();
        in.add(ByteBuffer.wrap(new byte[]{1, 2}));
        in.add(ByteBuffer.wrap(new byte[]{1, 2}));
        in.add(ByteBuffer.wrap(new byte[]{3, 4}));
        Assert.assertEquals(2, SetElementUtils.deduplicateElements(in, 2).size());
    }

    @Test
    public void normalizeAcceptsAllFfElement() {
        Set<ByteBuffer> in = new HashSet<>();
        in.add(SetElementUtils.createBotElement(16));
        in.add(ByteBuffer.wrap(new byte[16]));
        Assert.assertEquals(2, SetElementUtils.normalizeProtocolElements(in, 16, "element").size());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void deprecatedNormalizeIgnoresBotArgument() {
        Set<ByteBuffer> in = new HashSet<>();
        ByteBuffer allFf = SetElementUtils.createBotElement(16);
        in.add(allFf);
        Assert.assertEquals(
            1,
            SetElementUtils.normalizeProtocolElements(in, 16, allFf, "element").size()
        );
    }
}
