package com.aria.framework.utils;

import com.aria.framework.exceptions.RetryDeadlineExceededException;
import com.aria.framework.exceptions.RetryInterruptedException;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Caller deadline plus transport cancellation; unfinished work keeps its bounded capacity slot. */
public final class RetryDeadline {
    private static final Logger log = LoggerFactory.getLogger(RetryDeadline.class);
    public static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final Semaphore CAPACITY = new Semaphore(32);
    private static final AtomicLong IDS = new AtomicLong();
    private static final ThreadLocal<Budget> CURRENT = new ThreadLocal<>();

    private RetryDeadline() { }

    public static <T> T run(long timeoutMs, Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation");
        if (timeoutMs <= 0 || timeoutMs > Long.MAX_VALUE / 1_000_000) {
            throw new IllegalArgumentException("Operation timeout must be positive and fit monotonic nanoseconds");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new RetryInterruptedException("Operation interrupted before execution", new InterruptedException());
        }
        Budget existing = CURRENT.get();
        if (existing != null) {
            existing.requireRemaining();
            T result = operation.get();
            existing.requireRemaining();
            return result;
        }
        Budget budget = new Budget(timeoutMs);
        Map<String, String> diagnosticContext = MDC.getCopyOfContextMap();
        if (!CAPACITY.tryAcquire()) {
            throw new RetryDeadlineExceededException("All 32 operation slots are occupied; no background work was started");
        }
        FutureTask<T> task = new FutureTask<>(() -> {
            CURRENT.set(budget);
            try {
                if (diagnosticContext != null) MDC.setContextMap(diagnosticContext);
                budget.requireRemaining();
                T result = operation.get();
                budget.requireRemaining();
                return result;
            } finally {
                budget.closeClients(true);
                CURRENT.remove();
                MDC.clear();
            }
        });
        try {
            Thread worker = Thread.ofPlatform().daemon(true).inheritInheritableThreadLocals(true)
                .name("aria-operation-" + IDS.incrementAndGet()).unstarted(() -> {
                    try {
                        task.run();
                    } finally {
                        // FutureTask cancellation can precede callable startup; release at thread exit only.
                        CAPACITY.release();
                    }
                });
            worker.start();
        } catch (RuntimeException | Error failure) {
            CAPACITY.release();
            throw failure;
        }
        try {
            return task.get(Math.max(0, budget.timeRemaining()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            budget.closeClients(true);
            throw new RetryDeadlineExceededException("Operation deadline exceeded (" + timeoutMs + "ms)");
        } catch (InterruptedException exception) {
            task.cancel(true);
            budget.closeClients(true);
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Operation interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RetryInterruptedException) Thread.currentThread().interrupt();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Operation failed", cause);
        } catch (RetryDeadlineExceededException exception) {
            task.cancel(true);
            budget.closeClients(true);
            throw exception;
        }
    }

    static long remainingMillis() {
        Budget budget = CURRENT.get();
        if (budget == null) return Long.MAX_VALUE;
        long nanos = budget.requireRemaining();
        return nanos / 1_000_000 + (nanos % 1_000_000 == 0 ? 0 : 1);
    }

    public static int requestTimeoutMs(int configuredSeconds) {
        if (configuredSeconds <= 0) throw new IllegalArgumentException("Request timeout seconds must be positive");
        long configured = Math.multiplyExact((long) configuredSeconds, 1_000);
        long timeout = Math.min(configured, remainingMillis());
        return (int) Math.min(timeout, Integer.MAX_VALUE);
    }

    public static void registerClient(HttpClient client) {
        Budget budget = CURRENT.get();
        if (budget != null) budget.register(client);
    }

    public static void closeAttemptClients() {
        Budget budget = CURRENT.get();
        if (budget != null) budget.closeClients(false);
    }

    private static final class Budget {
        private final long started = System.nanoTime();
        private final long durationNanos;
        private final List<HttpClient> clients = new ArrayList<>();
        private boolean closed;

        private Budget(long timeoutMs) {
            durationNanos = Math.multiplyExact(timeoutMs, 1_000_000);
        }

        private synchronized long requireRemaining() {
            long remaining = timeRemaining();
            if (closed || remaining <= 0) {
                throw new RetryDeadlineExceededException("Operation deadline exhausted");
            }
            return remaining;
        }

        private long timeRemaining() {
            return durationNanos - (System.nanoTime() - started);
        }

        private synchronized void register(HttpClient client) {
            if (closed) {
                client.getConnectionManager().shutdown();
                throw new RetryDeadlineExceededException("Operation cancelled before transport execution");
            }
            clients.add(client);
        }

        private void closeClients(boolean finish) {
            List<HttpClient> closing;
            synchronized (this) {
                closed |= finish;
                closing = new ArrayList<>(clients);
                clients.clear();
            }
            for (HttpClient client : closing) {
                try {
                    client.getConnectionManager().shutdown();
                } catch (RuntimeException failure) {
                    // Close every registered transport without replacing the original deadline outcome.
                    log.warn("Transport shutdown failed: {}", failure.getClass().getSimpleName());
                }
            }
        }
    }
}
