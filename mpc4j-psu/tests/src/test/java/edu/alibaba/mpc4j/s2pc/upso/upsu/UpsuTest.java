package edu.alibaba.mpc4j.s2pc.upso.upsu;

import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractTwoPartyMemoryRpcPto;
import edu.alibaba.mpc4j.s2pc.opf.pmpeqt.tcl23.Tcl23ByteEccDdhPmPeqtConfig;
import edu.alibaba.mpc4j.psu.common.PsuBenchmarkUtils;
import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import edu.alibaba.mpc4j.s2pc.upso.upsu.tcl23.Tcl23UpsuConfig;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * UPSU test.
 *
 * @author Liqiang Peng
 * @date 2024/3/12
 */
@RunWith(Parameterized.class)
public class UpsuTest extends AbstractTwoPartyMemoryRpcPto {

    /**
     * sender element size
     */
    private static final int SENDER_ELEMENT_SIZE = 1 << 6;
    /**
     * receiver element size
     */
    private static final int RECEIVER_ELEMENT_SIZE = 1 << 12;
    /**
     * element byte length
     */
    private static final int ELEMENT_BYTE_LENGTH = CommonConstants.BLOCK_BYTE_LENGTH;
    /**
     * UPSU config
     */
    private final UpsuConfig config;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> configurations() {
        Collection<Object[]> configurations = new ArrayList<>();
        configurations.add(new Object[]{
            UpsuType.CCS_TCLZ23.name() + " Byte Ecc DDH",
            new Tcl23UpsuConfig.Builder()
                .setPmPeqtConfig(new Tcl23ByteEccDdhPmPeqtConfig.Builder().build())
                .build()
        });
        return configurations;
    }

    public UpsuTest(String name, UpsuConfig config) {
        super(name);
        this.config = config;
    }

    @Test
    public void testDefaultParallel() {
        testUpsu(SENDER_ELEMENT_SIZE, RECEIVER_ELEMENT_SIZE, true);
    }

    @Test
    public void testDefault() {
        testUpsu(SENDER_ELEMENT_SIZE, RECEIVER_ELEMENT_SIZE, false);
    }

    public void testUpsu(int senderElementSize, int receiverElementSize, boolean parallel) {
        assumeNativeFheAvailable();
        List<Set<ByteBuffer>> sets = PsuBenchmarkUtils.generateBytesSets(senderElementSize, receiverElementSize, ELEMENT_BYTE_LENGTH);
        Set<ByteBuffer> senderElementSet = sets.get(0);
        Set<ByteBuffer> receiverElementSet = sets.get(1);
        // create instances
        UpsuSender sender = UpsuFactory.createSender(firstRpc, secondRpc.ownParty(), config);
        UpsuReceiver receiver = UpsuFactory.createReceiver(secondRpc, firstRpc.ownParty(), config);
        int randomTaskId = Math.abs(new SecureRandom().nextInt());
        sender.setTaskId(randomTaskId);
        receiver.setTaskId(randomTaskId);
        // set parallel
        sender.setParallel(parallel);
        receiver.setParallel(parallel);
        try {
            UpsuSenderThread senderThread = new UpsuSenderThread(
                sender, receiverElementSize, senderElementSet, ELEMENT_BYTE_LENGTH
            );
            UpsuReceiverThread receiverThread = new UpsuReceiverThread(
                receiver, senderElementSize, receiverElementSet, ELEMENT_BYTE_LENGTH
            );
            STOP_WATCH.start();
            senderThread.start();
            receiverThread.start();
            TwoPartyTestJoin.joinFailFast(
                senderThread, senderThread::getFailure, sender::destroy,
                receiverThread, receiverThread::getFailure, receiver::destroy,
                "UPSU"
            );
            STOP_WATCH.stop();
            long time = STOP_WATCH.getTime(TimeUnit.MILLISECONDS);
            STOP_WATCH.reset();
            UpsuReceiverOutput receiverOutput = receiverThread.getReceiverOutput();
            assertOutput(senderElementSet, receiverElementSet, receiverOutput);
            printAndResetRpc(time);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError("UPSU test failed", e);
        }
    }

    private static Boolean nativeFheAvailable;

    private static void assumeNativeFheAvailable() {
        if (nativeFheAvailable == null) {
            String required = System.getProperty("libpsu.native.fhe.tests");
            try {
                System.loadLibrary(CommonConstants.MPC4J_NATIVE_FHE_NAME);
                nativeFheAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                nativeFheAvailable = false;
            }
            if ("true".equalsIgnoreCase(required) && !nativeFheAvailable) {
                Assert.fail(
                    "Profile libpsu-native-fhe requested but libmpc4j-native-fhe is missing. "
                        + "Set MPC4J_NATIVE_FHE_DIR / java.library.path to the built native library."
                );
            }
        }
        Assume.assumeTrue(
            "CCS:TCLZ23 UPSU requires libmpc4j-native-fhe (activate -Plibpsu-native-fhe with MPC4J_NATIVE_FHE_DIR)",
            nativeFheAvailable
        );
    }

    private void assertOutput(Set<ByteBuffer> senderElementSet, Set<ByteBuffer> receiverElementSet,
                              UpsuReceiverOutput receiverOutput) {
        Set<ByteBuffer> expectUnion = new HashSet<>(receiverElementSet);
        expectUnion.addAll(senderElementSet);
        Set<ByteBuffer> expectIntersection = new HashSet<>(receiverElementSet);
        expectIntersection.retainAll(senderElementSet);
        int expectPsica = expectIntersection.size();
        Set<ByteBuffer> actualUnion = receiverOutput.getUnion();
        Assert.assertTrue(actualUnion.containsAll(expectUnion));
        Assert.assertTrue(expectUnion.containsAll(actualUnion));
        int actualPsica = receiverOutput.getPsica();
        Assert.assertEquals(expectPsica, actualPsica);
    }
}
