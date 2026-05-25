package com.aria.framework.utils;

import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryUtilsTest {

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

    private static Response response(int statusCode, String retryAfter) {
        ResponseBuilder builder = new ResponseBuilder()
            .setStatusCode(statusCode);
        if (retryAfter != null) {
            builder.setHeader("Retry-After", retryAfter);
        }
        return builder.build();
    }
}
