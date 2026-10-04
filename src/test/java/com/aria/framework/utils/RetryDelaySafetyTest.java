package com.aria.framework.utils;

import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RetryDelaySafetyTest {

    @Test
    void obsoleteHttpDateFormatsAndFiftyYearRuleUseInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC);
        for (String header : List.of("Sunday, 06-Nov-94 08:49:37 GMT", "Sun Nov  6 08:49:37 1994")) {
            AtomicInteger calls = new AtomicInteger();
            List<Long> sleeps = new ArrayList<>();
            RetryUtils.executeWithRetry("GET",
                () -> calls.incrementAndGet() == 1 ? rateLimit(header) : ok(),
                new RetryUtils.RetryPolicy(2, 100, 1_000, 0), sleeps::add, true, clock);
            assertThat(sleeps).containsExactly(0L);
        }
    }

    @Test
    void futureHttpDateUsesInjectedClockAndRoundsUpFractionalMilliseconds() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00.500500Z"), ZoneOffset.UTC);
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        RetryUtils.executeWithRetry("GET",
            () -> calls.incrementAndGet() == 1 ? rateLimit("Sun, 4 Oct 2026 00:00:01 GMT") : ok(),
            new RetryUtils.RetryPolicy(2, 100, 1_000, 0), sleeps::add, true, clock);
        assertThat(sleeps).containsExactly(500L);
    }

    @Test
    void invalidDateFallsBackToBackoff() {
        assertThat(retryThenSuccess("Sun, 99 Oct 2026 00:00:01 GMT",
            new RetryUtils.RetryPolicy(2, 100, 1_000, 0))).containsExactly(100L);
    }

    @Test
    void syntacticallyValidUnparseableHugeDeltaDoesNotBecomeEarlyRetry() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        Response response = RetryUtils.executeWithRetry("GET", () -> {
            calls.incrementAndGet();
            return rateLimit("9999999999999999999999999999999999999999");
        }, new RetryUtils.RetryPolicy(2, 100, 1_000, 0), sleeps::add, true);
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void pastHttpDateMeansNoAdditionalWait() {
        List<Long> sleeps = retryThenSuccess("Wed, 21 Oct 2015 07:28:00 GMT",
            new RetryUtils.RetryPolicy(2, 100, 1_000, 0));
        assertThat(sleeps).containsExactly(0L);
    }

    @Test
    void invalidNegativeDeltaFallsBackToPositiveBackoff() {
        List<Long> sleeps = retryThenSuccess("-1", new RetryUtils.RetryPolicy(2, 100, 1_000, 0));
        assertThat(sleeps).containsExactly(100L);
    }

    @Test
    void oversizedDeltaDoesNotOverflowOrRetryBeforeServerPermits() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        Response response = RetryUtils.executeWithRetry("GET", () -> {
            calls.incrementAndGet();
            return rateLimit(Long.toString(Long.MAX_VALUE));
        }, new RetryUtils.RetryPolicy(2, 100, 1_000, 0), sleeps::add, true);
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void exponentialMultiplicationSaturatesWithoutNegativeDelay() {
        long base = Long.MAX_VALUE / 2 + 1;
        RetryUtils.RetryPolicy policy = new RetryUtils.RetryPolicy(4, base, Long.MAX_VALUE, 0);
        assertThat(List.of(
            RetryUtils.calculateDelayMsWithoutResponse(1, policy),
            RetryUtils.calculateDelayMsWithoutResponse(2, policy),
            RetryUtils.calculateDelayMsWithoutResponse(3, policy)))
            .containsExactly(base, Long.MAX_VALUE, Long.MAX_VALUE);
    }

    @Test
    void maximumJitterIsBoundedInsideDelayCapWithoutOverflow() {
        List<Long> sleeps = retryThenSuccess(null,
            new RetryUtils.RetryPolicy(2, 100, 101, Long.MAX_VALUE));
        assertThat(sleeps).hasSize(1);
        assertThat(sleeps.getFirst()).isBetween(100L, 101L);
    }

    private static List<Long> retryThenSuccess(String header, RetryUtils.RetryPolicy policy) {
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        RetryUtils.executeWithRetry("GET", () -> calls.incrementAndGet() == 1 ? rateLimit(header) : ok(),
            policy, sleeps::add, true);
        return sleeps;
    }

    private static Response rateLimit(String header) {
        ResponseBuilder builder = new ResponseBuilder().setStatusCode(429).setHeader("Content-Type", "application/json");
        if (header != null) builder.setHeader("Retry-After", header);
        return builder.build();
    }

    private static Response ok() {
        return new ResponseBuilder().setStatusCode(200).build();
    }
}
