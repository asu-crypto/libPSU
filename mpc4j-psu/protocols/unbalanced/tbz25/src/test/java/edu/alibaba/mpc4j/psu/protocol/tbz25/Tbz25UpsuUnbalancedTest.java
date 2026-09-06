package edu.alibaba.mpc4j.psu.protocol.tbz25;

import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.s2pc.upso.upsu.UpsuReceiverOutput;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuConfig;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuReceiver;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tbz25.Tbz25UpsuSender;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * USENIX_BinYujConYanYu25 UPSU correctness with unequal set sizes (sender 2^5, receiver 2^7).
 */
public class Tbz25UpsuUnbalancedTest extends AbstractTwoPartyMemoryRpcPto {
    private static final int SENDER_SIZE = 32;
    private static final int RECEIVER_SIZE = 128;
    private static final int ELEMENT_BYTE_LENGTH = 16;

    public Tbz25UpsuUnbalancedTest() {
        super("TBZ25_UPSU");
    }

    @Test(timeout = 180_000)
    public void testUnbalancedUnion() throws InterruptedException {
        Tbz25UpsuConfig config = new Tbz25UpsuConfig.Builder(false).build();
        Tbz25UpsuSender sender = new Tbz25UpsuSender(firstRpc, secondRpc.ownParty(), config);
        Tbz25UpsuReceiver receiver = new Tbz25UpsuReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

        ArrayList<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(
            SENDER_SIZE, RECEIVER_SIZE, ELEMENT_BYTE_LENGTH
        );
        Set<ByteBuffer> senderSet = sets.get(0);
        Set<ByteBuffer> receiverSet = sets.get(1);

        AtomicReference<UpsuReceiverOutput> receiverOut = new AtomicReference<>();
        AtomicReference<Throwable> senderErr = new AtomicReference<>();
        AtomicReference<Throwable> receiverErr = new AtomicReference<>();

        Thread senderThread = new Thread(() -> {
            try {
                sender.init(senderSet.size(), receiverSet.size());
                sender.psu(senderSet, ELEMENT_BYTE_LENGTH);
            } catch (Throwable t) {
                senderErr.set(t);
            }
        });
        Thread receiverThread = new Thread(() -> {
            try {
                receiver.init(receiverSet, senderSet.size(), ELEMENT_BYTE_LENGTH);
                receiverOut.set(receiver.psu(senderSet.size()));
            } catch (Throwable t) {
                receiverErr.set(t);
            }
        });
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();

        if (senderErr.get() != null) {
            throw new AssertionError("sender failed", senderErr.get());
        }
        if (receiverErr.get() != null) {
            throw new AssertionError("receiver failed", receiverErr.get());
        }

        Set<ByteBuffer> expectUnion = new HashSet<>(senderSet);
        expectUnion.addAll(receiverSet);
        Set<ByteBuffer> actualUnion = receiverOut.get().getUnion();
        Assert.assertEquals(expectUnion.size(), actualUnion.size());
        Assert.assertTrue(actualUnion.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actualUnion));
        int expectPsiCa = senderSet.size() + receiverSet.size() - expectUnion.size();
        Assert.assertEquals(expectPsiCa, receiverOut.get().getPsica());
    }

    @Test(timeout = 180_000)
    public void testReceiver10Sender8Intersection4() throws InterruptedException {
        Tbz25UpsuConfig config = new Tbz25UpsuConfig.Builder(false).build();
        Tbz25UpsuSender sender = new Tbz25UpsuSender(firstRpc, secondRpc.ownParty(), config);
        Tbz25UpsuReceiver receiver = new Tbz25UpsuReceiver(secondRpc, firstRpc.ownParty(), config);
        int taskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(taskId);
        receiver.setTaskId(taskId);

        int senderSize = 8;
        int receiverSize = 10;
        int intersectionSize = 4;
        Set<ByteBuffer> senderSet = new HashSet<>();
        Set<ByteBuffer> receiverSet = new HashSet<>();
        for (int i = 0; i < intersectionSize; i++) {
            ByteBuffer shared = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            shared.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, i);
            byte[] v = shared.array();
            senderSet.add(ByteBuffer.wrap(v.clone()));
            receiverSet.add(ByteBuffer.wrap(v.clone()));
        }
        int s = intersectionSize;
        while (senderSet.size() < senderSize) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES * 2, 1);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, s++);
            senderSet.add(bb);
        }
        int r = intersectionSize;
        while (receiverSet.size() < receiverSize) {
            ByteBuffer bb = ByteBuffer.allocate(ELEMENT_BYTE_LENGTH);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES * 2, 2);
            bb.putInt(ELEMENT_BYTE_LENGTH - Integer.BYTES, r++);
            receiverSet.add(bb);
        }

        AtomicReference<UpsuReceiverOutput> receiverOut = new AtomicReference<>();
        AtomicReference<Throwable> senderErr = new AtomicReference<>();
        AtomicReference<Throwable> receiverErr = new AtomicReference<>();
        Thread senderThread = new Thread(() -> {
            try {
                sender.init(senderSet.size(), receiverSet.size());
                sender.psu(senderSet, ELEMENT_BYTE_LENGTH);
            } catch (Throwable t) {
                senderErr.set(t);
            }
        });
        Thread receiverThread = new Thread(() -> {
            try {
                receiver.init(receiverSet, senderSet.size(), ELEMENT_BYTE_LENGTH);
                receiverOut.set(receiver.psu(senderSet.size()));
            } catch (Throwable t) {
                receiverErr.set(t);
            }
        });
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();
        if (senderErr.get() != null) {
            throw new AssertionError("sender failed", senderErr.get());
        }
        if (receiverErr.get() != null) {
            throw new AssertionError("receiver failed", receiverErr.get());
        }
        Set<ByteBuffer> expectUnion = new HashSet<>(senderSet);
        expectUnion.addAll(receiverSet);
        Assert.assertEquals(expectUnion, receiverOut.get().getUnion());
        Assert.assertEquals(intersectionSize, receiverOut.get().getPsica());
        sender.destroy();
        receiver.destroy();
    }
}
