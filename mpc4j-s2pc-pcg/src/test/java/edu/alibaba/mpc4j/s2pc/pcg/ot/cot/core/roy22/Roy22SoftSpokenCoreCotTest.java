package edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.roy22;

import edu.alibaba.mpc4j.common.rpc.MpcAbortException;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.common.tool.utils.BinaryUtils;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.OtTestUtils;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory.CoreCotType;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Roy22 SoftSpoken core COT protocol-level test (Component 1, semi-honest, k=2).
 *
 * <p>Verifies the COT correlation {@code q_j ⊕ t_j = c_j · Δ} produced by the subspace VOLE
 * construction. Mirrors {@code CoreCotTest}'s machinery but lives in its own class because:</p>
 * <ul>
 *     <li>Roy22 is currently hard-coded to {@code k = 2} (Component-1 scope); other {@code k}
 *         values would need separate config plumbing.</li>
 *     <li>Adding Roy22 to {@code CoreCotTest}'s parametrized matrix is fine in principle, but
 *         keeping it isolated here makes it easy to skip in CI loops that target only the legacy
 *         protocols while the Component-2 bandwidth optimization is still TODO.</li>
 * </ul>
 *
 * @author audit follow-up #5 (May 26, 2026)
 */
public class Roy22SoftSpokenCoreCotTest extends AbstractTwoPartyMemoryRpcPto {
    private static final Logger LOGGER = LoggerFactory.getLogger(Roy22SoftSpokenCoreCotTest.class);
    private static final int DEFAULT_NUM = 1000;
    private static final int LARGE_NUM = 1 << 16;

    public Roy22SoftSpokenCoreCotTest() {
        super(Roy22SoftSpokenCoreCotTest.class.getSimpleName());
    }

    @Test
    public void test1Num() {
        testPto(1, false);
    }

    @Test
    public void test2Num() {
        testPto(2, false);
    }

    @Test
    public void test4Num() {
        // 4 = boundary case: exactly one packed byte holds all four F-elements at k=2.
        testPto(4, false);
    }

    @Test
    public void test5Num() {
        // 5 = forces a second byte that uses only one F-element.
        testPto(5, false);
    }

    @Test
    public void testDefaultNum() {
        testPto(DEFAULT_NUM, false);
    }

    @Test
    public void testParallelDefaultNum() {
        testPto(DEFAULT_NUM, true);
    }

    @Test
    public void testLargeNum() {
        testPto(LARGE_NUM, false);
    }

    @Test
    public void testFactoryWiringSender() {
        Roy22SoftSpokenCoreCotConfig config = new Roy22SoftSpokenCoreCotConfig.Builder().build();
        CoreCotSender sender = CoreCotFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        Assert.assertTrue(
            "CoreCotFactory.createSender(ROY22_SOFT_SPOKEN) should return Roy22SoftSpokenCoreCotSender",
            sender instanceof Roy22SoftSpokenCoreCotSender
        );
        Assert.assertEquals(CoreCotType.ROY22_SOFT_SPOKEN, config.getPtoType());
        new Thread(sender::destroy).start();
    }

    @Test
    public void testFactoryWiringReceiver() {
        Roy22SoftSpokenCoreCotConfig config = new Roy22SoftSpokenCoreCotConfig.Builder().build();
        CoreCotReceiver receiver = CoreCotFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        Assert.assertTrue(
            "CoreCotFactory.createReceiver(ROY22_SOFT_SPOKEN) should return Roy22SoftSpokenCoreCotReceiver",
            receiver instanceof Roy22SoftSpokenCoreCotReceiver
        );
        new Thread(receiver::destroy).start();
    }

    private void testPto(int num, boolean parallel) {
        Roy22SoftSpokenCoreCotConfig config = new Roy22SoftSpokenCoreCotConfig.Builder().build();
        CoreCotSender sender = CoreCotFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        CoreCotReceiver receiver = CoreCotFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        sender.setParallel(parallel);
        receiver.setParallel(parallel);
        int randomTaskId = Math.abs(SECURE_RANDOM.nextInt());
        sender.setTaskId(randomTaskId);
        receiver.setTaskId(randomTaskId);
        try {
            LOGGER.info("-----test {} (num={}) start-----", sender.getPtoDesc().getPtoName(), num);
            byte[] delta = BlockUtils.randomBlock(SECURE_RANDOM);
            boolean[] choices = BinaryUtils.randomBinary(num, SECURE_RANDOM);
            SenderRunner senderThread = new SenderRunner(sender, delta, num);
            ReceiverRunner receiverThread = new ReceiverRunner(receiver, choices);
            STOP_WATCH.start();
            senderThread.start();
            receiverThread.start();
            senderThread.join();
            receiverThread.join();
            STOP_WATCH.stop();
            long time = STOP_WATCH.getTime(TimeUnit.MILLISECONDS);
            STOP_WATCH.reset();
            CotSenderOutput senderOutput = senderThread.getSenderOutput();
            CotReceiverOutput receiverOutput = receiverThread.getReceiverOutput();
            OtTestUtils.assertOutput(num, senderOutput, receiverOutput);
            printAndResetRpc(time);
            new Thread(sender::destroy).start();
            new Thread(receiver::destroy).start();
            LOGGER.info("-----test {} (num={}) end-----", sender.getPtoDesc().getPtoName(), num);
        } catch (InterruptedException e) {
            throw new AssertionError("Roy22 test interrupted", e);
        }
    }

    /**
     * Local thread runner — {@code CoreCotSenderThread} is package-private.
     */
    private static final class SenderRunner extends Thread {
        private final CoreCotSender sender;
        private final byte[] delta;
        private final int num;
        private CotSenderOutput senderOutput;

        SenderRunner(CoreCotSender sender, byte[] delta, int num) {
            this.sender = sender;
            this.delta = delta;
            this.num = num;
        }

        CotSenderOutput getSenderOutput() {
            return senderOutput;
        }

        @Override
        public void run() {
            try {
                sender.init(delta);
                senderOutput = sender.send(num);
            } catch (MpcAbortException e) {
                throw new AssertionError("Roy22 sender aborted", e);
            }
        }
    }

    /**
     * Local thread runner — {@code CoreCotReceiverThread} is package-private.
     */
    private static final class ReceiverRunner extends Thread {
        private final CoreCotReceiver receiver;
        private final boolean[] choices;
        private CotReceiverOutput receiverOutput;

        ReceiverRunner(CoreCotReceiver receiver, boolean[] choices) {
            this.receiver = receiver;
            this.choices = choices;
        }

        CotReceiverOutput getReceiverOutput() {
            return receiverOutput;
        }

        @Override
        public void run() {
            try {
                receiver.init();
                receiverOutput = receiver.receive(choices);
            } catch (MpcAbortException e) {
                throw new AssertionError("Roy22 receiver aborted", e);
            }
        }
    }
}
