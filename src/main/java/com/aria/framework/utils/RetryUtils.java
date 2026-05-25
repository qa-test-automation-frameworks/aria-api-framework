package com.aria.framework.utils;

import com.aria.framework.config.ConfigManager;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.exceptions.RetryInterruptedException;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Exponential backoff utility for dealing with rate limit errors (HTTP 403 or 429).
 */
public final class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    private RetryUtils() {
        // Prevent instantiation
    }

    /**
     * Executes an API request and retries on 403 or 429 response codes using exponential backoff.
     *
     * @param requestSupplier Functional interface supplying the RestAssured Response
     * @return Response the final execution response
     */
    public static Response executeWithRetry(Supplier<Response> requestSupplier) {
        return executeGetWithRetry(requestSupplier);
    }

    public static Response executeGetWithRetry(Supplier<Response> requestSupplier) {
        return executeGetWithRetry(ConfigManager.defaults().getFrameworkConfig(), requestSupplier);
    }

    public static Response executeGetWithRetry(FrameworkConfig config, Supplier<Response> requestSupplier) {
        return executeWithPolicy("GET", true, requestSupplier, RetryPolicy.from(config));
    }

    public static Response executeWithoutRetry(Supplier<Response> requestSupplier) {
        return requestSupplier.get();
    }

    public static Response executeMutationWithRetry(
        String method,
        boolean idempotencyControlled,
        Supplier<Response> requestSupplier
    ) {
        if (!idempotencyControlled) {
            return executeWithoutRetry(requestSupplier);
        }
        return executeMutationWithRetry(ConfigManager.defaults().getFrameworkConfig(), method, true, requestSupplier);
    }

    public static Response executeMutationWithRetry(
        FrameworkConfig config,
        String method,
        boolean idempotencyControlled,
        Supplier<Response> requestSupplier
    ) {
        if (!idempotencyControlled) {
            return executeWithoutRetry(requestSupplier);
        }
        return executeWithPolicy(method, true, requestSupplier, RetryPolicy.from(config));
    }

    private static Response executeWithPolicy(
        String method,
        boolean retryTransientExceptions,
        Supplier<Response> requestSupplier,
        RetryPolicy policy
    ) {
        return executeWithRetry(method, requestSupplier, policy, Thread::sleep, retryTransientExceptions);
    }

    public static Response executeWithRetry(
        String method,
        Supplier<Response> requestSupplier,
        RetryPolicy policy,
        Sleeper sleeper,
        boolean retryTransientExceptions
    ) {
        int attempt = 1;

        while (true) {
            Response response;
            try {
                response = requestSupplier.get();
            } catch (RuntimeException exception) {
                if (retryTransientExceptions && isTransientNetworkException(exception) && attempt < policy.maxAttempts()) {
                    sleepBeforeRetry(method, "transient exception " + exception.getClass().getSimpleName(), attempt, policy, sleeper, null);
                    attempt++;
                    continue;
                }
                throw exception;
            }
            int statusCode = response.getStatusCode();

            if (isRetryableRateLimit(response) && attempt < policy.maxAttempts()) {
                sleepBeforeRetry(method, "HTTP " + statusCode, attempt, policy, sleeper, response);
                attempt++;
            } else {
                return response;
            }
        }
    }

    private static boolean isRetryableRateLimit(Response response) {
        int statusCode = response.getStatusCode();
        if (statusCode == 429) {
            return true;
        }
        if (statusCode != 403) {
            return false;
        }
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isBlank()) {
            return true;
        }
        String remaining = response.header("X-RateLimit-Remaining");
        return "0".equals(remaining == null ? null : remaining.trim());
    }

    private static long calculateDelayMs(Response response, int attempt, RetryPolicy policy) {
        String retryAfter = response.header("Retry-After");
        if (retryAfter != null && !retryAfter.isBlank()) {
            try {
                return Math.min(Duration.ofSeconds(Long.parseLong(retryAfter.trim())).toMillis(), policy.maxDelayMs());
            } catch (NumberFormatException ignored) {
                log.debug("Ignoring non-numeric Retry-After header: {}", retryAfter);
            }
        }

        long exponentialDelay = policy.baseDelayMs() * (1L << Math.max(0, attempt - 1));
        long boundedDelay = Math.min(exponentialDelay, policy.maxDelayMs());
        return boundedDelay + ThreadLocalRandom.current().nextLong(policy.jitterMs() + 1L);
    }

    private static void sleepBeforeRetry(
        String method,
        String reason,
        int attempt,
        RetryPolicy policy,
        Sleeper sleeper,
        Response response
    ) {
        long retryDelayMs = response == null
            ? calculateDelayMsWithoutResponse(attempt, policy)
            : calculateDelayMs(response, attempt, policy);
        log.warn("{} retry triggered by {}. Retrying attempt {}/{} in {}ms...",
            method, reason, attempt + 1, policy.maxAttempts(), retryDelayMs);

        try {
            sleeper.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Retry thread sleep was interrupted", e);
        }
    }

    private static long calculateDelayMsWithoutResponse(int attempt, RetryPolicy policy) {
        long exponentialDelay = policy.baseDelayMs() * (1L << Math.max(0, attempt - 1));
        long boundedDelay = Math.min(exponentialDelay, policy.maxDelayMs());
        return boundedDelay + ThreadLocalRandom.current().nextLong(policy.jitterMs() + 1L);
    }

    private static boolean isTransientNetworkException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                || current instanceof SocketException
                || current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record RetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs, long jitterMs) {
        public static RetryPolicy from(FrameworkConfig config) {
            return new RetryPolicy(
                config.retryMaxAttempts(),
                config.retryBaseDelayMs(),
                config.retryMaxDelayMs(),
                config.retryJitterMs()
            );
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long delayMs) throws InterruptedException;
    }
}
