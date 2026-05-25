package com.aria.framework.config;

/**
 * Immutable runtime configuration used by clients and services.
 */
public record FrameworkConfig(
    String environment,
    String baseUrl,
    String githubBaseUrl,
    String githubToken,
    String bookerUsername,
    String bookerPassword,
    int timeoutSeconds,
    long responseTimeSlaMs,
    int retryMaxAttempts,
    long retryBaseDelayMs,
    long retryMaxDelayMs,
    long retryJitterMs
) {
}
