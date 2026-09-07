package edu.alibaba.mpc4j.s2pc.pso.psu;

import edu.alibaba.mpc4j.psu.test.TwoPartyTestJoin;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression: bounded join must surface the original peer failure within seconds
 * even when the other party is blocked in a receive-like wait.
 */
public class TwoPartyTestJoinFailFastTest {
    @Test(timeout = 5_000)
    public void failFastWhenPeerThrowsWhileOtherBlockedInReceive() {
        AtomicReference<Throwable> serverFail = new AtomicReference<>();
        AtomicReference<Throwable> clientFail = new AtomicReference<>();
        AtomicBoolean clientEnteredReceive = new AtomicBoolean(false);
        CountDownLatch clientBlocked = new CountDownLatch(1);
        CountDownLatch neverOpened = new CountDownLatch(1);
        AtomicBoolean serverDestroyed = new AtomicBoolean(false);
        AtomicBoolean clientDestroyed = new AtomicBoolean(false);

        Thread server = new Thread(() -> {
            try {
                Assert.assertTrue(clientBlocked.await(2, TimeUnit.SECONDS));
                throw new IllegalStateException("deliberate-server-failure");
            } catch (Throwable t) {
                serverFail.set(t);
            }
        }, "fail-fast-server");

        Thread client = new Thread(() -> {
            try {
                clientEnteredReceive.set(true);
                clientBlocked.countDown();
                // Stand-in for Rpc.receive(): block until interrupted / party destroyed.
                neverOpened.await();
            } catch (Throwable t) {
                clientFail.set(t);
            }
        }, "fail-fast-client");

        long start = System.nanoTime();
        server.start();
        client.start();
        try {
            TwoPartyTestJoin.joinFailFast(
                server, serverFail::get, () -> {
                    serverDestroyed.set(true);
                    neverOpened.countDown();
                },
                client, clientFail::get, () -> {
                    clientDestroyed.set(true);
                    neverOpened.countDown();
                },
                4_000L,
                "fail-fast-regression"
            );
            Assert.fail("expected deliberate-server-failure to surface");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("deliberate-server-failure", expected.getMessage());
        } catch (AssertionError ae) {
            Throwable cause = ae.getCause();
            Assert.assertNotNull(ae.toString(), cause);
            Assert.assertTrue(String.valueOf(cause.getMessage()),
                cause.getMessage() != null && cause.getMessage().contains("deliberate-server-failure"));
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        Assert.assertTrue("client should have entered receive-like wait", clientEnteredReceive.get());
        Assert.assertTrue("cleanup must destroy both parties", serverDestroyed.get() && clientDestroyed.get());
        Assert.assertTrue("must finish well under 5s, was " + elapsedMs + "ms", elapsedMs < 5_000L);
        Assert.assertFalse(server.isAlive());
        Assert.assertFalse(client.isAlive());
    }
}
