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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.OptionalLong;
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
        return executeWithRetry(method, requestSupplier, policy, sleeper, retryTransientExceptions, Clock.systemUTC());
    }

    public static Response executeWithRetry(
        String method,
        Supplier<Response> requestSupplier,
        RetryPolicy policy,
        Sleeper sleeper,
        boolean retryTransientExceptions,
        Clock clock
    ) {
        int attempt = 1;

        while (true) {
            Response response;
            try {
                response = requestSupplier.get();
            } catch (RuntimeException exception) {
                if (retryTransientExceptions && isTransientNetworkException(exception) && attempt < policy.maxAttempts()) {
                    sleepBeforeRetry(method, "transient exception " + exception.getClass().getSimpleName(), attempt, policy, sleeper, null, clock);
                    attempt++;
                    continue;
                }
                throw exception;
            }
            int statusCode = response.getStatusCode();

            if (isRetryableRateLimit(response) && attempt < policy.maxAttempts()) {
                if (!sleepBeforeRetry(method, "HTTP " + statusCode, attempt, policy, sleeper, response, clock)) {
                    return response;
                }
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

    private static OptionalLong retryAfterDelayMs(Response response, Clock clock) {
        String header = response.header("Retry-After");
        if (header == null || header.isBlank()) return OptionalLong.empty();
        String value = header.trim();
        if (value.matches("[0-9]+")) {
            try {
                long seconds = Long.parseLong(value);
                return OptionalLong.of(seconds > Long.MAX_VALUE / 1_000 ? Long.MAX_VALUE : seconds * 1_000);
            } catch (NumberFormatException ignored) {
                // A syntactically valid, huge positive delay must not become an early retry.
                return OptionalLong.of(Long.MAX_VALUE);
            }
        }
        try {
            Duration delay = Duration.between(clock.instant(), parseHttpDate(value, clock));
            if (delay.isNegative() || delay.isZero()) return OptionalLong.of(0);
            long extraMs = (delay.getNano() + 999_999L) / 1_000_000;
            long seconds = delay.getSeconds();
            return OptionalLong.of(seconds > (Long.MAX_VALUE - extraMs) / 1_000
                ? Long.MAX_VALUE : seconds * 1_000 + extraMs);
        } catch (DateTimeParseException ignored) {
            // Do not log arbitrary header text; malformed/negative values use local backoff.
            return OptionalLong.empty();
        }
    }

    private static Instant parseHttpDate(String value, Clock clock) {
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            // RFC 9110 recipients also accept obsolete RFC850 and asctime dates.
        }
        if (value.matches("(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday), .*")) {
            int baseYear = clock.instant().atZone(ZoneOffset.UTC).getYear() - 49;
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern("dd-MMM-").appendValueReduced(ChronoField.YEAR, 2, 2, baseYear)
                .appendPattern(" HH:mm:ss 'GMT'").toFormatter(Locale.US)
                .withResolverStyle(ResolverStyle.STRICT);
            LocalDateTime date = LocalDateTime.parse(value.substring(value.indexOf(',') + 1).trim(), formatter);
            if (date.toInstant(ZoneOffset.UTC).isAfter(clock.instant().atZone(ZoneOffset.UTC).plusYears(50).toInstant())) {
                date = date.minusYears(100);
            }
            return date.toInstant(ZoneOffset.UTC);
        }
        return LocalDateTime.parse(value.replaceAll(" +", " "),
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss uuuu", Locale.US)
                .withResolverStyle(ResolverStyle.STRICT)).toInstant(ZoneOffset.UTC);
    }

    private static boolean sleepBeforeRetry(
        String method,
        String reason,
        int attempt,
        RetryPolicy policy,
        Sleeper sleeper,
        Response response,
        Clock clock
    ) {
        OptionalLong serverDelay = response == null ? OptionalLong.empty() : retryAfterDelayMs(response, clock);
        if (serverDelay.isPresent() && serverDelay.getAsLong() > policy.maxDelayMs()) {
            log.warn("{} retry stopped: server delay exceeds the {}ms policy limit; not retrying early",
                method, policy.maxDelayMs());
            return false;
        }
        long retryDelayMs = serverDelay.isPresent()
            ? serverDelay.getAsLong() : calculateDelayMsWithoutResponse(attempt, policy);
        log.warn("{} retry triggered by {}. Retrying attempt {}/{} in {}ms...",
            method, reason, attempt + 1, policy.maxAttempts(), retryDelayMs);

        try {
            sleeper.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryInterruptedException("Retry thread sleep was interrupted", e);
        }
    }

    private static long calculateDelayMsWithoutResponse(int attempt, RetryPolicy policy) {
        long delay = policy.baseDelayMs();
        // Saturation bounds this loop to at most 63 doublings even for huge attempt counts.
        for (int doubling = 1; doubling < attempt && delay < policy.maxDelayMs(); doubling++) {
            delay = delay > policy.maxDelayMs() / 2 ? policy.maxDelayMs()
                : Math.min(delay * 2, policy.maxDelayMs());
        }
        long jitterLimit = Math.min(policy.jitterMs(), policy.maxDelayMs() - delay);
        // baseDelayMs is positive, so jitterLimit + 1 cannot overflow even at Long.MAX_VALUE.
        return delay + ThreadLocalRandom.current().nextLong(jitterLimit + 1);
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
        public RetryPolicy {
            if (maxAttempts <= 0 || baseDelayMs <= 0 || maxDelayMs < baseDelayMs || jitterMs < 0) {
                throw new IllegalArgumentException("Retry policy requires positive attempts/base delay, max >= base, and nonnegative jitter");
            }
        }

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
