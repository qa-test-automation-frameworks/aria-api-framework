package com.aria.framework.utils;

import com.aria.framework.config.ConfigManager;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.exceptions.RetryDeadlineExceededException;
import com.aria.framework.exceptions.RetryInterruptedException;
import io.qameta.allure.Allure;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryDeadlineTest {
    @Test
    void combinedRequestAndSleepBudgetStopsBeforeSecondRequest() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        Response limited = rateLimited("1");
        Response result = RetryUtils.executeWithRetry("GET", () -> {
            calls.incrementAndGet();
            pause(120);
            return limited;
        }, new RetryUtils.RetryPolicy(3, 1, 2_000, 0, 600), sleeps::add, true);
        assertThat(result.statusCode()).isEqualTo(429);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void actualRetrySleepUsesTheSameOperationBudget() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        long start = System.nanoTime();
        assertThatThrownBy(() -> RetryUtils.executeWithRetry("GET", () -> {
            worker.set(Thread.currentThread());
            int attempt = calls.incrementAndGet();
            pause(attempt == 1 ? 150 : 500);
            return rateLimited("invalid");
        }, new RetryUtils.RetryPolicy(100, 100, 100, 0, 450), Thread::sleep, true))
            .isInstanceOf(RetryDeadlineExceededException.class);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isBetween(350L, 2_000L);
        assertThat(calls.get()).isBetween(2, 3);
        awaitWorkerExit(worker.get());
    }

    @Test
    void oversizedTransientBackoffFailsWithoutCallingSleeper() {
        AtomicInteger sleeps = new AtomicInteger();
        assertThatThrownBy(() -> RetryUtils.executeWithRetry("GET", () -> {
            throw new RuntimeException(new SocketTimeoutException("synthetic"));
        }, new RetryUtils.RetryPolicy(2, Long.MAX_VALUE, Long.MAX_VALUE, 0, 500),
            ignored -> sleeps.incrementAndGet(), true))
            .isInstanceOf(RetryDeadlineExceededException.class)
            .hasMessageContaining("retry delay");
        assertThat(sleeps).hasValue(0);
    }

    @Test
    void nestedOperationCannotResetOuterDeadline() {
        AtomicReference<Thread> worker = new AtomicReference<>();
        assertThatThrownBy(() -> RetryDeadline.run(250, () -> {
            worker.set(Thread.currentThread());
            pause(150);
            return RetryDeadline.run(5_000, () -> { pause(300); return "late"; });
        })).isInstanceOf(RetryDeadlineExceededException.class);
        awaitWorkerExit(worker.get());
    }

    @Test
    void priorInterruptionDoesNotStartSupplierAndKeepsFlag() {
        AtomicInteger calls = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> RetryDeadline.run(500, calls::incrementAndGet))
                .isInstanceOf(RetryInterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(calls).hasValue(0);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void callerInterruptionCancelsWorkerAndPreservesCallerFlag() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread caller = Thread.ofPlatform().unstarted(() -> {
            try {
                RetryDeadline.run(5_000, () -> {
                    worker.set(Thread.currentThread());
                    started.countDown();
                    pause(10_000);
                    return null;
                });
            } catch (Throwable failure) {
                outcome.set(failure);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        caller.start();
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(2_000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(outcome.get()).isInstanceOf(RetryInterruptedException.class);
            assertThat(interrupted.get()).isTrue();
            awaitWorkerExit(worker.get());
        } finally {
            caller.interrupt();
            caller.join(2_000);
        }
    }

    @Test
    void unsafeWriteHasOneAttemptWithItsConfiguredDeadline() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        assertThatThrownBy(() -> RetryUtils.executeMutationWithRetry(config(200), "POST", false, () -> {
            worker.set(Thread.currentThread());
            calls.incrementAndGet();
            pause(2_000);
            return rateLimited("0");
        })).isInstanceOf(RetryDeadlineExceededException.class);
        assertThat(calls).hasValue(1);
        awaitWorkerExit(worker.get());
    }

    @Test
    void explicitlyIdempotentWriteRetainsAllowedRetry() {
        AtomicInteger calls = new AtomicInteger();
        Response result = RetryUtils.executeMutationWithRetry(config(2_000), "PUT", true,
            () -> calls.incrementAndGet() == 1 ? rateLimited("0") : ok());
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    @Test
    void diagnosticContextIsInheritedWithoutChangingCallerOrNextOperation() {
        String stepId = "deadline-context-" + java.util.UUID.randomUUID();
        MDC.put("deadline-probe", "first");
        Allure.getLifecycle().startStep(stepId, new io.qameta.allure.model.StepResult().setName("deadline context"));
        try {
            String parent = Allure.getLifecycle().getCurrentTestCaseOrStep().orElse(null);
            assertThat(parent).isEqualTo(stepId);
            RetryDeadline.run(1_000, () -> {
                assertThat(MDC.get("deadline-probe")).isEqualTo("first");
                assertThat(Allure.getLifecycle().getCurrentTestCaseOrStep()).contains(stepId);
                MDC.put("deadline-probe", "child");
                String childId = stepId + "-child";
                Allure.getLifecycle().startStep(childId, new io.qameta.allure.model.StepResult().setName("child"));
                assertThat(Allure.getLifecycle().getCurrentTestCaseOrStep()).contains(childId);
                Allure.getLifecycle().stopStep(childId);
                assertThat(Allure.getLifecycle().getCurrentTestCaseOrStep()).contains(stepId);
                return null;
            });
            assertThat(MDC.get("deadline-probe")).isEqualTo("first");
            assertThat(Allure.getLifecycle().getCurrentTestCaseOrStep()).contains(stepId);
        } finally {
            MDC.remove("deadline-probe");
            Allure.getLifecycle().stopStep(stepId);
        }
        RetryDeadline.run(1_000, () -> {
            assertThat(MDC.get("deadline-probe")).isNull();
            assertThat(Allure.getLifecycle().getCurrentTestCaseOrStep().orElse(null)).isNotEqualTo(stepId);
            return null;
        });
    }

    @Test
    void uncooperativeSuppliersKeepBoundedSlotsUntilTheyActuallyExit() throws Exception {
        CountDownLatch started = new CountDownLatch(32);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> callers = new ArrayList<>();
        List<Thread> workers = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger rejectedCalls = new AtomicInteger();
        try {
            for (int index = 0; index < 32; index++) {
                Thread caller = Thread.ofPlatform().start(() -> {
                    try {
                        RetryDeadline.run(10_000, () -> {
                            workers.add(Thread.currentThread());
                            started.countDown();
                            boolean waiting = true;
                            while (waiting) {
                                try { release.await(); waiting = false; }
                                catch (InterruptedException ignored) { /* controlled uncooperative supplier */ }
                            }
                            return null;
                        });
                    } catch (Throwable outcome) { failures.add(outcome); }
                });
                callers.add(caller);
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> RetryDeadline.run(1_000, rejectedCalls::incrementAndGet))
                .isInstanceOf(RetryDeadlineExceededException.class).hasMessageContaining("32 operation slots");
            for (Thread caller : callers) caller.interrupt();
            for (Thread caller : callers) caller.join(2_000);
            assertThat(callers).allMatch(thread -> !thread.isAlive());
            assertThat(failures).hasSize(32).allMatch(failure -> failure instanceof RetryInterruptedException);
            assertThatThrownBy(() -> RetryDeadline.run(1_000, rejectedCalls::incrementAndGet))
                .isInstanceOf(RetryDeadlineExceededException.class).hasMessageContaining("32 operation slots");
            assertThat(rejectedCalls).hasValue(0);
        } finally {
            release.countDown();
            for (Thread caller : callers) { caller.interrupt(); caller.join(2_000); }
            for (Thread worker : workers) awaitWorkerExit(worker);
        }
        assertThat(RetryDeadline.run(1_000, () -> "capacity recovered")).isEqualTo("capacity recovered");
    }

    @Test
    void timeoutConfigurationRejectsZeroNegativeAndOverflowWithoutWork() {
        for (long value : new long[]{0, -1, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> RetryDeadline.run(value, () -> "unused"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RetryUtils.RetryPolicy(2, 1, 10, 0, value))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(RetryDeadline.requestTimeoutMs(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> RetryDeadline.requestTimeoutMs(0)).isInstanceOf(IllegalArgumentException.class);
        String old = System.getProperty("retry.totalTimeoutMs");
        try {
            for (String value : List.of("0", "-1", Long.toString(Long.MAX_VALUE))) {
                System.setProperty("retry.totalTimeoutMs", value);
                assertThatThrownBy(() -> ConfigManager.create("dev"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("retry.totalTimeoutMs");
            }
        } finally {
            if (old == null) System.clearProperty("retry.totalTimeoutMs");
            else System.setProperty("retry.totalTimeoutMs", old);
        }
    }

    @Test
    void cyclicCauseChainDoesNotTrapAWorker() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);
        assertThatThrownBy(() -> RetryUtils.executeWithRetry("GET", () -> { throw first; },
            new RetryUtils.RetryPolicy(2, 1, 10, 0, 500), ignored -> { }, true)).isSameAs(first);
    }

    static FrameworkConfig config(long timeoutMs) {
        return new FrameworkConfig("test", "http://localhost", "http://localhost", "", "", "",
            5, 1_000, 3, 1, 2_000, 0, timeoutMs);
    }

    static void awaitWorkerExit(Thread worker) {
        assertThat(worker).isNotNull();
        try { worker.join(2_000); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
        assertThat(worker.isAlive()).isFalse();
    }

    private static void pause(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Synthetic supplier interrupted", failure);
        }
    }

    private static Response rateLimited(String delay) {
        return new ResponseBuilder().setStatusCode(429).setHeader("Retry-After", delay).build();
    }

    private static Response ok() { return new ResponseBuilder().setStatusCode(200).build(); }
}
