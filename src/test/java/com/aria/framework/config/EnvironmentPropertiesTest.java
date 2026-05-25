package com.aria.framework.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards committed environment property files against duplicate keys and missing tunables.
 */
class EnvironmentPropertiesTest {

    private static final List<String> ENVIRONMENTS = List.of("dev", "staging", "prod");
    private static final List<String> REQUIRED_KEYS = List.of(
        "base.url",
        "github.base.url",
        "github.token",
        "timeout.seconds",
        "sla.responseTimeMs",
        "retry.maxAttempts",
        "retry.baseDelayMs",
        "retry.maxDelayMs",
        "retry.jitterMs"
    );

    @Test
    @Tag("config")
    @DisplayName("Environment property files contain one value for every required key")
    void environmentFilesDoNotContainDuplicateKeys() throws IOException {
        for (String environment : ENVIRONMENTS) {
            Path path = Path.of("src/main/resources/config/" + environment + ".properties");
            List<String> lines = Files.readAllLines(path);

            Set<String> seenKeys = new HashSet<>();
            Set<String> duplicateKeys = new HashSet<>();
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }
                String key = trimmedLine.substring(0, trimmedLine.indexOf('=')).trim();
                if (!seenKeys.add(key)) {
                    duplicateKeys.add(key);
                }
            }

            assertThat(duplicateKeys)
                .as(environment + " duplicate config keys")
                .isEmpty();
            assertThat(seenKeys)
                .as(environment + " required config keys")
                .containsAll(REQUIRED_KEYS);
            assertThat(seenKeys)
                .as(environment + " committed credential keys")
                .doesNotContain("booker.username", "booker.password");
        }
    }

    @Test
    @Tag("config")
    @DisplayName("ConfigManager can create isolated managers for supported environments")
    void configManagerCreatesIsolatedSupportedEnvironments() {
        assertThat(ConfigManager.create("dev").getEnvironment()).isEqualTo("dev");
        assertThat(ConfigManager.create("staging").getEnvironment()).isEqualTo("staging");
        assertThat(ConfigManager.create("prod").getEnvironment()).isEqualTo("prod");
    }

    @Test
    @Tag("config")
    @DisplayName("Documented system property overrides are propagated into FrameworkConfig")
    void documentedSystemPropertyOverridesReachFrameworkConfig() {
        Map<String, String> overrides = Map.ofEntries(
            Map.entry("booking.base.url", "http://127.0.0.1:18080"),
            Map.entry("github.base.url", "http://127.0.0.1:18081"),
            Map.entry("github.token", "test-github-token"),
            Map.entry("booker.username", "test-user"),
            Map.entry("booker.password", "test-password"),
            Map.entry("timeout.seconds", "7"),
            Map.entry("sla.responseTimeMs", "1200"),
            Map.entry("retry.maxAttempts", "4"),
            Map.entry("retry.baseDelayMs", "25"),
            Map.entry("retry.maxDelayMs", "250"),
            Map.entry("retry.jitterMs", "5")
        );
        Map<String, String> previousValues = snapshotSystemProperties(overrides.keySet());

        try {
            overrides.forEach(System::setProperty);

            FrameworkConfig config = ConfigManager.create("dev").getFrameworkConfig();

            assertThat(config.baseUrl()).isEqualTo("http://127.0.0.1:18080");
            assertThat(config.githubBaseUrl()).isEqualTo("http://127.0.0.1:18081");
            assertThat(config.githubToken()).isEqualTo("test-github-token");
            assertThat(config.bookerUsername()).isEqualTo("test-user");
            assertThat(config.bookerPassword()).isEqualTo("test-password");
            assertThat(config.timeoutSeconds()).isEqualTo(7);
            assertThat(config.responseTimeSlaMs()).isEqualTo(1200);
            assertThat(config.retryMaxAttempts()).isEqualTo(4);
            assertThat(config.retryBaseDelayMs()).isEqualTo(25);
            assertThat(config.retryMaxDelayMs()).isEqualTo(250);
            assertThat(config.retryJitterMs()).isEqualTo(5);
        } finally {
            restoreSystemProperties(previousValues);
        }
    }

    private static Map<String, String> snapshotSystemProperties(Set<String> keys) {
        Map<String, String> snapshot = new HashMap<>();
        keys.forEach(key -> snapshot.put(key, System.getProperty(key)));
        return snapshot;
    }

    private static void restoreSystemProperties(Map<String, String> previousValues) {
        previousValues.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }
}
