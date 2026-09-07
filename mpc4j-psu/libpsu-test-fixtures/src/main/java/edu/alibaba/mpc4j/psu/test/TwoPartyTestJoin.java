package edu.alibaba.mpc4j.psu.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Bounded, fail-fast join for two-party protocol test workers.
 * <p>
 * Polls either party's failure while waiting, interrupts and destroys both parties
 * when one fails or the deadline expires, then rethrows the original cause.
 * </p>
 */
public final class TwoPartyTestJoin {
    /** Default deadline for small correctness tests. */
    public static final long DEFAULT_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(120);

    private TwoPartyTestJoin() {
    }

    @FunctionalInterface
    public interface FailureSource {
        Throwable getFailure();
    }

    /**
     * Join two worker threads with a deadline. Always runs both destroy actions in {@code finally}.
     *
     * @param serverThread   server worker thread (already started)
     * @param serverFailure  server failure supplier
     * @param destroyServer  server cleanup (destroy/disconnect); may be null
     * @param clientThread   client worker thread (already started)
     * @param clientFailure  client failure supplier
     * @param destroyClient  client cleanup; may be null
     * @param timeoutMs      maximum wait
     * @param label          label for assertion messages (e.g. protocol name)
     */
    public static void joinFailFast(
        Thread serverThread,
        FailureSource serverFailure,
        Runnable destroyServer,
        Thread clientThread,
        FailureSource clientFailure,
        Runnable destroyClient,
        long timeoutMs,
        String label
    ) {
        AtomicReference<Throwable> observed = new AtomicReference<>();
        long deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, timeoutMs));
        try {
            while (serverThread.isAlive() || clientThread.isAlive()) {
                Throwable sf = serverFailure == null ? null : serverFailure.getFailure();
                Throwable cf = clientFailure == null ? null : clientFailure.getFailure();
                if (sf != null || cf != null) {
                    observed.compareAndSet(null, sf != null ? sf : cf);
                    interruptQuietly(serverThread);
                    interruptQuietly(clientThread);
                    destroyQuietly(destroyServer);
                    destroyQuietly(destroyClient);
                    // Brief wait for workers to notice interrupt / destroy.
                    joinQuietly(serverThread, 1_000L);
                    joinQuietly(clientThread, 1_000L);
                    break;
                }
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadlineNs - System.nanoTime());
                if (remainingMs <= 0) {
                    interruptQuietly(serverThread);
                    interruptQuietly(clientThread);
                    destroyQuietly(destroyServer);
                    destroyQuietly(destroyClient);
                    joinQuietly(serverThread, 1_000L);
                    joinQuietly(clientThread, 1_000L);
                    throw new AssertionError(
                        label + " timed out after " + timeoutMs + " ms (serverAlive="
                            + serverThread.isAlive() + ", clientAlive=" + clientThread.isAlive() + ")"
                    );
                }
                long slice = Math.min(50L, remainingMs);
                joinQuietly(serverThread, slice);
                joinQuietly(clientThread, slice);
            }

            if (serverThread.isAlive() || clientThread.isAlive()) {
                interruptQuietly(serverThread);
                interruptQuietly(clientThread);
                destroyQuietly(destroyServer);
                destroyQuietly(destroyClient);
                joinQuietly(serverThread, 1_000L);
                joinQuietly(clientThread, 1_000L);
            }
            if (serverThread.isAlive() || clientThread.isAlive()) {
                throw new AssertionError(
                    label + " worker still alive after cleanup (serverAlive="
                        + serverThread.isAlive() + ", clientAlive=" + clientThread.isAlive() + ")"
                );
            }

            Throwable sf = firstNonNull(observed.get(),
                serverFailure == null ? null : serverFailure.getFailure());
            Throwable cf = clientFailure == null ? null : clientFailure.getFailure();
            if (sf != null) {
                throw wrap(label + " server failed", sf);
            }
            if (cf != null) {
                throw wrap(label + " client failed", cf);
            }
        } finally {
            destroyQuietly(destroyServer);
            destroyQuietly(destroyClient);
        }
    }

    public static void joinFailFast(
        Thread serverThread,
        FailureSource serverFailure,
        Runnable destroyServer,
        Thread clientThread,
        FailureSource clientFailure,
        Runnable destroyClient,
        String label
    ) {
        joinFailFast(
            serverThread, serverFailure, destroyServer,
            clientThread, clientFailure, destroyClient,
            DEFAULT_TIMEOUT_MS, label
        );
    }

    private static AssertionError wrap(String message, Throwable cause) {
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        return new AssertionError(message, cause);
    }

    private static Throwable firstNonNull(Throwable a, Throwable b) {
        return a != null ? a : b;
    }

    private static void interruptQuietly(Thread t) {
        if (t != null) {
            t.interrupt();
        }
    }

    private static void joinQuietly(Thread t, long ms) {
        if (t == null) {
            return;
        }
        try {
            t.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while joining test worker", e);
        }
    }

    private static void destroyQuietly(Runnable destroy) {
        if (destroy == null) {
            return;
        }
        try {
            destroy.run();
        } catch (Throwable ignored) {
            // best-effort cleanup
        }
    }

    /** Convenience when failure is held in an {@link AtomicReference}. */
    public static FailureSource fromAtomic(Supplier<AtomicReference<Throwable>> ref) {
        return () -> {
            AtomicReference<Throwable> r = ref.get();
            return r == null ? null : r.get();
        };
    }
}
