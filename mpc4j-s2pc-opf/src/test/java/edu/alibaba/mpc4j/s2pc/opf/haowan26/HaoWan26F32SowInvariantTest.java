package edu.alibaba.mpc4j.s2pc.opf.haowan26;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfFactory;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfReceiver;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32SowOprfSender;
import edu.alibaba.mpc4j.s2pc.aby.pcg.sowoprf.F32Wprf;
import edu.alibaba.mpc4j.s2pc.pcg.ot.conv32.Conv32Factory.Conv32Type;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

/**
 * SOW invariant: {@code share0 XOR share1 == F(x)} for APRR24 F32 with HaoWan expand.
 */
public class HaoWan26F32SowInvariantTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int BATCH = 8;

    public HaoWan26F32SowInvariantTest() {
        super("HAO_WAN26_F32_SOW");
    }

    @Test(timeout = 180_000)
    public void testShareXorEqualsPrf() throws InterruptedException {
        var config = F32SowOprfFactory.createDefaultConfig(Conv32Type.CCOT);
        F32SowOprfSender sender = F32SowOprfFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        F32SowOprfReceiver receiver = F32SowOprfFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

        byte[][] items = new byte[BATCH][16];
        for (int i = 0; i < BATCH; i++) {
            SECURE_RANDOM.nextBytes(items[i]);
        }
        byte[][] expanded = new byte[BATCH][];
        for (int i = 0; i < BATCH; i++) {
            expanded[i] = HaoWan26AltModExpand.expand(items[i]);
        }

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
