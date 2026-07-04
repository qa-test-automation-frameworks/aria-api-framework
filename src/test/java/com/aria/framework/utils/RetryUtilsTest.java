package com.aria.framework.utils;

import com.aria.framework.config.FrameworkConfig;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryUtilsTest {

    private static final RetryUtils.RetryPolicy ZERO_JITTER_POLICY =
        new RetryUtils.RetryPolicy(3, 100, 10_000, 0);

    @Test
    void retriesGetOnRateLimitResponse() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> calls.incrementAndGet() == 1 ? response(429, "1") : response(200, null),
            new RetryUtils.RetryPolicy(2, 1, 1_000, 0),
            sleeps::add,
            true
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
        assertThat(sleeps).containsExactly(1_000L);
    }

    @Test
    void doesNotRetryNonIdempotentMutationWhenPolicyDisablesTransientRetries() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryUtils.executeWithRetry(
            "POST",
            () -> {
                calls.incrementAndGet();
                throw new RuntimeException(new SocketTimeoutException("timeout"));
            },
            new RetryUtils.RetryPolicy(3, 1, 10, 0),
            ignored -> { },
            false
        )).isInstanceOf(RuntimeException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesTransientNetworkExceptionWhenAllowed() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new RuntimeException(new SocketTimeoutException("timeout"));
                }
                return response(200, null);
            },
            new RetryUtils.RetryPolicy(2, 1, 10, 0),
            ignored -> { },
            true
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    @Test
    void stopsRetryingOnceMaxAttemptsReached() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> {
                calls.incrementAndGet();
                return response(429, "1");
            },
            new RetryUtils.RetryPolicy(2, 1, 1_000, 0),
            sleeps::add,
            true
        );

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(calls).hasValue(2);
        assertThat(sleeps).hasSize(1);
    }

    @Test
    void usesRetryAfterHeaderDelayCappedAtMaxDelay() {
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        RetryUtils.executeWithRetry(
            "GET",
            () -> calls.incrementAndGet() == 1 ? response(429, "5") : response(200, null),
            new RetryUtils.RetryPolicy(2, 1, 2_000, 0),
            sleeps::add,
            true
        );

        assertThat(sleeps).containsExactly(2_000L);
    }

    @Test
    void ignoresMalformedRetryAfterHeaderAndFallsBackToExponentialDelay() {
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        RetryUtils.executeWithRetry(
            "GET",
            () -> calls.incrementAndGet() == 1 ? response(429, "not-a-number") : response(200, null),
            new RetryUtils.RetryPolicy(2, 50, 10_000, 0),
            sleeps::add,
            true
        );

        assertThat(sleeps).containsExactly(50L);
    }

    @Test
    void calculatesExponentialDelayForTransientExceptionWithoutResponse() {
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();

        RetryUtils.executeWithRetry(
            "GET",
            () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new RuntimeException(new SocketTimeoutException("timeout"));
                }
                return response(200, null);
            },
            new RetryUtils.RetryPolicy(2, 25, 10_000, 0),
            sleeps::add,
            true
        );

        assertThat(sleeps).containsExactly(25L);
    }

    @Test
    void executeGetWithRetryPublicOverloadsReturnUnderlyingResponse() {
        FrameworkConfig config = frameworkConfigWithRetryPolicy();

        Response direct = RetryUtils.executeGetWithRetry(config, () -> response(200, null));
        Response singleArg = RetryUtils.executeWithRetry(() -> response(200, null));

        assertThat(direct.statusCode()).isEqualTo(200);
        assertThat(singleArg.statusCode()).isEqualTo(200);
    }

    @Test
    void executeMutationWithRetrySkipsRetryWhenNotIdempotencyControlled() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeMutationWithRetry(
            "POST",
            false,
            () -> {
                calls.incrementAndGet();
                return response(429, "1");
            }
        );

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(calls).hasValue(1);
    }

    @Test
    void executeMutationWithRetryRetriesWhenIdempotencyControlled() {
        FrameworkConfig config = frameworkConfigWithRetryPolicy();
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeMutationWithRetry(
            config,
            "PUT",
            true,
            () -> calls.incrementAndGet() == 1 ? response(429, "0") : response(200, null)
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryOn403WithoutRetryAfterOrExhaustedRateLimitHeaders() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> {
                calls.incrementAndGet();
                return new ResponseBuilder().setStatusCode(403).setHeader("Content-Type", "application/json").build();
            },
            ZERO_JITTER_POLICY,
            ignored -> { },
            true
        );

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesOn403WhenRateLimitRemainingHeaderIsZero() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> calls.incrementAndGet() == 1
                ? new ResponseBuilder().setStatusCode(403).setHeader("X-RateLimit-Remaining", "0").build()
                : response(200, null),
            ZERO_JITTER_POLICY,
            ignored -> { },
            true
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryOn403WhenRateLimitRemainingHeaderIsNonZero() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> {
                calls.incrementAndGet();
                return new ResponseBuilder().setStatusCode(403).setHeader("X-RateLimit-Remaining", "5").build();
            },
            ZERO_JITTER_POLICY,
            ignored -> { },
            true
        );

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotRetryTransientExceptionWhenNotAllowed() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryUtils.executeWithRetry(
            "GET",
            () -> {
                calls.incrementAndGet();
                throw new RuntimeException(new SocketTimeoutException("timeout"));
            },
            ZERO_JITTER_POLICY,
            ignored -> { },
            false
        )).isInstanceOf(RuntimeException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void doesNotTreatNonTransientExceptionAsRetryable() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> RetryUtils.executeWithRetry(
            "GET",
            () -> {
                calls.incrementAndGet();
                throw new RuntimeException(new IllegalStateException("boom"));
            },
            ZERO_JITTER_POLICY,
            ignored -> { },
            true
        )).isInstanceOf(RuntimeException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void detectsTransientNetworkExceptionsAcrossCauseChain() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new RuntimeException("wrapper", new SocketException("reset"));
                }
                return response(200, null);
            },
            ZERO_JITTER_POLICY,
            ignored -> { },
            true
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    @Test
    void detectsConnectExceptionAsTransient() {
        AtomicInteger calls = new AtomicInteger();

        Response response = RetryUtils.executeWithRetry(
            "GET",
            () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new RuntimeException(new ConnectException("refused"));
                }
                return response(200, null);
            },
            ZERO_JITTER_POLICY,
            ignored -> { },
            true
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    @Test
    void interruptedSleepRestoresInterruptFlagAndThrows() {
        assertThatThrownBy(() -> RetryUtils.executeWithRetry(
            "GET",
            () -> response(429, "1"),
            new RetryUtils.RetryPolicy(2, 1, 1_000, 0),
            delayMs -> {
                throw new InterruptedException("stop");
            },
            true
        )).isInstanceOf(RuntimeException.class);

        assertThat(Thread.interrupted()).isTrue();
    }

    private static FrameworkConfig frameworkConfigWithRetryPolicy() {
        return new FrameworkConfig(
            "test",
            "http://localhost",
            "http://localhost",
            "token",
            "user",
            "pass",
            5,
            1_000,
            2,
            1,
            1_000,
            0
        );
    }

    private static Response response(int statusCode, String retryAfter) {
        ResponseBuilder builder = new ResponseBuilder()
            .setStatusCode(statusCode);
        if (retryAfter != null) {
            builder.setHeader("Retry-After", retryAfter);
        }
        return builder.build();
    }
}
