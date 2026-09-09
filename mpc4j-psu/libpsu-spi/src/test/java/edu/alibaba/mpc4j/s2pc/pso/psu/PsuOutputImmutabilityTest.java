package edu.alibaba.mpc4j.s2pc.pso.psu;

import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * Mutation isolation for immutable PSU output DTOs.
 */
public class PsuOutputImmutabilityTest {

    @Test
    public void clientOutputIsolatesUnion() {
        byte[] raw = new byte[]{7, 7, 7, 7};
        Set<ByteBuffer> union = new HashSet<>();
        union.add(ByteBuffer.wrap(raw));
        PsuClientOutput out = new PsuClientOutput(union, 0);
        raw[0] = 0;
        union.clear();

        Set<ByteBuffer> view = out.getUnion();
        Assert.assertEquals(1, view.size());
        Assert.assertEquals(7, view.iterator().next().get(0));
        Assert.assertTrue(view.iterator().next().isReadOnly());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void clientOutputRejectsAdd() {
        Set<ByteBuffer> union = new HashSet<>();
        union.add(ByteBuffer.wrap(new byte[]{1}));
        new PsuClientOutput(union, 0).getUnion().add(ByteBuffer.wrap(new byte[]{2}));
    }

    @Test
    public void twoSidedOutputIsolatesUnion() {
        byte[] raw = new byte[]{3, 3};
        Set<ByteBuffer> union = new HashSet<>();
        union.add(ByteBuffer.wrap(raw));
        PsuTwoSidedOutput out = new PsuTwoSidedOutput(union);
        raw[0] = 9;
        Assert.assertEquals(3, out.getUnion().iterator().next().get(0));
    }
}
