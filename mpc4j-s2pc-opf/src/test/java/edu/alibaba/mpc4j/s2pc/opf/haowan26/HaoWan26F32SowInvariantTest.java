package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfReceiver;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfSender;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32WprfPublicParamsType;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.aprr24.Aprr24F32SowOprfConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.conv32.Conv32Factory.Conv32Type;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * SOW invariant: {@code share0 XOR share1 == F(x)} with matching secure-join G/A/B.
 */
public class HaoWan26F32SowInvariantTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int BATCH = 8;

    public HaoWan26F32SowInvariantTest() {
        super("HAO_WAN26_F32_SOW");
    }

    @Test(timeout = 180_000)
    public void testShareXorEqualsPrfSecureJoin() throws InterruptedException {
        var config = new Aprr24F32SowOprfConfig.Builder(Conv32Type.CCOT)
            .setPublicParamsType(F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN)
            .build();
        runInvariant(config, HaoWan26AltModExpand.ExpandProfile.HAO_WAN_SECURE_JOIN, true);
    }

    @Test(timeout = 180_000)
    public void testShareXorEqualsPrfIncludingZero() throws InterruptedException {
        var config = new Aprr24F32SowOprfConfig.Builder(Conv32Type.CCOT)
            .setPublicParamsType(F32WprfPublicParamsType.HAO_WAN_SECURE_JOIN)
            .build();
        F32SowOprfSender sender = F32SowOprfFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        F32SowOprfReceiver receiver = F32SowOprfFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

        byte[][] expanded = new byte[BATCH][];
        expanded[0] = HaoWan26AltModExpand.expand(new byte[16], HaoWan26AltModExpand.ExpandProfile.HAO_WAN_SECURE_JOIN);
        for (int i = 1; i < BATCH; i++) {
            byte[] item = new byte[16];
            SECURE_RANDOM.nextBytes(item);
            expanded[i] = HaoWan26AltModExpand.expand(item, HaoWan26AltModExpand.ExpandProfile.HAO_WAN_SECURE_JOIN);
        }
        runShares(sender, receiver, expanded);
    }

    private void runInvariant(
        edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfConfig config,
        HaoWan26AltModExpand.ExpandProfile expandProfile,
        boolean includeRandom
    ) throws InterruptedException {
        F32SowOprfSender sender = F32SowOprfFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        F32SowOprfReceiver receiver = F32SowOprfFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

        byte[][] expanded = new byte[BATCH][];
        for (int i = 0; i < BATCH; i++) {
            byte[] item = new byte[16];
            if (includeRandom) {
                SECURE_RANDOM.nextBytes(item);
            } else {
                item[0] = (byte) (i + 1);
            }
            expanded[i] = HaoWan26AltModExpand.expand(item, expandProfile);
        }
        runShares(sender, receiver, expanded);
    }

    private void runShares(F32SowOprfSender sender, F32SowOprfReceiver receiver, byte[][] expanded)
        throws InterruptedException {
        AtomicReference<byte[][]> senderShares = new AtomicReference<>();
        AtomicReference<byte[][]> receiverShares = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();

        Thread senderThread = new Thread(() -> {
            try {
                sender.init(BATCH);
                senderShares.set(sender.oprf(BATCH));
            } catch (Throwable t) {
                err.set(t);
            }
        });
        Thread receiverThread = new Thread(() -> {
            try {
                receiver.init(BATCH);
                receiverShares.set(receiver.oprf(expanded));
            } catch (Throwable t) {
                err.set(t);
            }
        });
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        if (err.get() != null) {
            throw new AssertionError(err.get());
        }

        byte[][] s0 = receiverShares.get();
        byte[][] s1 = senderShares.get();
        Assert.assertEquals(BATCH, s0.length);
        Assert.assertEquals(BATCH, s1.length);
        for (int i = 0; i < BATCH; i++) {
            Assert.assertEquals(F32Wprf.getOutputByteLength(), s0[i].length);
            Assert.assertEquals(F32Wprf.getOutputByteLength(), s1[i].length);
            byte[] actual = BytesUtils.xor(s0[i], s1[i]);
            byte[] expect = sender.prf(expanded[i]);
            Assert.assertArrayEquals(expect, actual);
        }
    }
}
