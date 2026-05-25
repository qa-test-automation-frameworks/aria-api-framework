package com.aria.framework.config;

import org.aeonbits.owner.ConfigFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * Loads and validates framework configuration for a specific environment.
 */
public final class ConfigManager {

    private final EnvironmentConfig config;
    private final FrameworkConfig frameworkConfig;

    private ConfigManager(String environment) {
        Properties overrides = new Properties();
        overrides.setProperty("env", normalizeEnvironment(environment));
        this.config = ConfigFactory.create(EnvironmentConfig.class, overrides);
        this.frameworkConfig = resolveFrameworkConfig(config, overrides.getProperty("env"));
        validate();
    }

    public static ConfigManager defaults() {
        return DefaultsHolder.DEFAULTS;
    }

    public static ConfigManager create(String environment) {
        return new ConfigManager(environment);
    }

    public EnvironmentConfig getConfig() {
        return config;
    }

    public FrameworkConfig getFrameworkConfig() {
        return frameworkConfig;
    }

    public String getEnvironment() {
        return frameworkConfig.environment();
    }

    public void validate() {
        requireHttpUrl("base.url", getBaseUrl());
        requireHttpUrl("github.base.url", getGithubBaseUrl());
        requirePositive("timeout.seconds", getTimeoutSeconds());
        requirePositive("sla.responseTimeMs", getResponseTimeSlaMs());
        requirePositive("retry.maxAttempts", getRetryMaxAttempts());
        requirePositive("retry.baseDelayMs", getRetryBaseDelayMs());
        requirePositive("retry.maxDelayMs", getRetryMaxDelayMs());
        requireNonNegative("retry.jitterMs", getRetryJitterMs());
        if (getRetryMaxDelayMs() < getRetryBaseDelayMs()) {
            throw new IllegalArgumentException("retry.maxDelayMs must be greater than or equal to retry.baseDelayMs");
        }
    }

    public void requireBookerCredentials() {
        if (getBookerUsername().isBlank() || getBookerPassword().isBlank()) {
            throw new IllegalStateException("Restful Booker live tests require BOOKER_USERNAME and BOOKER_PASSWORD "
                + "or matching system properties. See .env.example.");
        }
    }

    public String getBaseUrl() {
        return frameworkConfig.baseUrl();
    }

    public String getGithubBaseUrl() {
        return frameworkConfig.githubBaseUrl();
    }

    public String getGithubToken() {
        return frameworkConfig.githubToken();
    }

    public String getBookerUsername() {
        return frameworkConfig.bookerUsername();
    }

    public String getBookerPassword() {
        return frameworkConfig.bookerPassword();
    }

    public int getTimeoutSeconds() {
        return frameworkConfig.timeoutSeconds();
    }

    public long getResponseTimeSlaMs() {
        return frameworkConfig.responseTimeSlaMs();
    }

    public int getRetryMaxAttempts() {
        return frameworkConfig.retryMaxAttempts();
    }

    public long getRetryBaseDelayMs() {
        return frameworkConfig.retryBaseDelayMs();
    }

    public long getRetryMaxDelayMs() {
        return frameworkConfig.retryMaxDelayMs();
    }

    public long getRetryJitterMs() {
        return frameworkConfig.retryJitterMs();
    }

    private static FrameworkConfig resolveFrameworkConfig(EnvironmentConfig config, String environment) {
        return new FrameworkConfig(
            environment,
            resolveBaseUrl(config),
            firstNonBlank(
                firstNonBlank(System.getProperty("github.base.url"), System.getenv("GITHUB_BASE_URL")),
                config.githubBaseUrl()
            ),
            firstNonBlank(
                firstNonBlank(System.getProperty("github.token"), System.getenv("GITHUB_TOKEN")),
                config.githubToken()
            ),
            firstNonBlank(
                firstNonBlank(System.getProperty("booker.username"), System.getenv("BOOKER_USERNAME")),
                config.bookerUsername()
            ),
            firstNonBlank(
                firstNonBlank(System.getProperty("booker.password"), System.getenv("BOOKER_PASSWORD")),
                config.bookerPassword()
            ),
            intFromOverride("TIMEOUT_SECONDS", "timeout.seconds", config.timeoutSeconds()),
            longFromOverride("RESPONSE_TIME_SLA_MS", "sla.responseTimeMs", config.responseTimeSlaMs()),
            intFromOverride("RETRY_MAX_ATTEMPTS", "retry.maxAttempts", config.retryMaxAttempts()),
            longFromOverride("RETRY_BASE_DELAY_MS", "retry.baseDelayMs", config.retryBaseDelayMs()),
            longFromOverride("RETRY_MAX_DELAY_MS", "retry.maxDelayMs", config.retryMaxDelayMs()),
            longFromOverride("RETRY_JITTER_MS", "retry.jitterMs", config.retryJitterMs())
        );
    }

    private static String resolveBaseUrl(EnvironmentConfig config) {
        String systemPropertyOverride = firstNonBlank(
            System.getProperty("booking.base.url"),
            System.getProperty("base.url")
        );
        return firstNonBlank(
            firstNonBlank(systemPropertyOverride, System.getenv("BOOKING_BASE_URL")),
            config.baseUrl()
        );
    }

    private static String resolveDefaultEnvironment() {
        return firstNonBlank(
            firstNonBlank(System.getProperty("env"), System.getenv("ENV")),
            "dev"
        );
    }

    private static String normalizeEnvironment(String environment) {
        String normalized = firstNonBlank(environment, "dev").toLowerCase();
        if (!isSupportedEnvironment(normalized)) {
            throw new IllegalArgumentException("Unsupported environment '" + normalized
                + "'. Supported environments are " + supportedEnvironments());
        }
        return normalized;
    }

    private static int intFromOverride(String envKey, String propertyKey, int defaultValue) {
        String override = firstNonBlank(System.getProperty(propertyKey), System.getenv(envKey));
        return override == null ? defaultValue : Integer.parseInt(override);
    }

    private static long longFromOverride(String envKey, String propertyKey, long defaultValue) {
        String override = firstNonBlank(System.getProperty(propertyKey), System.getenv(envKey));
        return override == null ? defaultValue : Long.parseLong(override);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private static void requireHttpUrl(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException(key + " must be an absolute http(s) URL: " + value);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(key + " must be a valid URI: " + value, e);
        }
    }

    private static void requirePositive(String key, long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be positive but was " + value);
        }
    }

    private static void requireNonNegative(String key, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be non-negative but was " + value);
        }
    }

    private static boolean isSupportedEnvironment(String env) {
        return "dev".equals(env) || "staging".equals(env) || "prod".equals(env);
    }

    private static String supportedEnvironments() {
        return "[dev, staging, prod]";
    }

    private static final class DefaultsHolder {
        private static final ConfigManager DEFAULTS = create(resolveDefaultEnvironment());

        private DefaultsHolder() {
        }
    }
}
