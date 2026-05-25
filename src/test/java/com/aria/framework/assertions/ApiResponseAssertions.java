package com.aria.framework.assertions;

import com.aria.framework.reporting.AllureTestDiagnostics;
import io.restassured.response.Response;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared API response assertions for status-adjacent metadata that every endpoint should verify.
 */
public final class ApiResponseAssertions {

    private ApiResponseAssertions() {
    }

    public static void assertJsonContentType(Response response) {
        assertStrictJsonContentType(response);
    }

    public static void assertStrictJsonContentType(Response response) {
        AllureTestDiagnostics.step("Verify JSON Content-Type header", () -> {
            String mediaType = mediaType(response.header("Content-Type"));
            assertThat(mediaType)
                .as("Content-Type response header")
                .isEqualTo("application/json");
            AllureTestDiagnostics.log("Content-Type media type was {}", mediaType);
        });
    }

    public static void assertResponseTimeUnder(Response response, long maxMillis) {
        AllureTestDiagnostics.step("Verify response time is under " + maxMillis + " ms", () -> {
            assertThat(response.time())
                .as("response time")
                .isLessThan(maxMillis);
            AllureTestDiagnostics.log("Response time was {} ms; SLA is {} ms", response.time(), maxMillis);
        });
    }

    public static void assertResponseTimeWithinConfiguredSla(Response response) {
        assertResponseTimeUnder(response, com.aria.framework.config.ConfigManager.defaults().getResponseTimeSlaMs());
    }

    public static void assertGithubRateLimitHeaders(Response response) {
        AllureTestDiagnostics.step("Verify GitHub rate-limit headers", () -> {
            assertThat(response.header("X-RateLimit-Limit"))
                .as("GitHub rate limit header")
                .isNotBlank();
            assertThat(response.header("X-RateLimit-Remaining"))
                .as("GitHub remaining rate limit header")
                .isNotBlank();
            AllureTestDiagnostics.log(
                "GitHub rate limit headers: limit={}, remaining={}",
                response.header("X-RateLimit-Limit"),
                response.header("X-RateLimit-Remaining")
            );
        });
    }

    public static void assertCacheControlHeader(Response response) {
        AllureTestDiagnostics.step("Verify Cache-Control header", () -> {
            assertThat(response.header("Cache-Control"))
                .as("Cache-Control response header")
                .isNotBlank();
            AllureTestDiagnostics.log("Cache-Control header was {}", response.header("Cache-Control"));
        });
    }

    public static void assertSecurityHeaderIfPresent(Response response, String headerName) {
        AllureTestDiagnostics.step("Verify optional security header " + headerName, () -> {
            String header = response.header(headerName);
            if (header != null) {
                assertThat(header)
                    .as(headerName + " response header")
                    .isNotBlank();
                AllureTestDiagnostics.log("{} header was present", headerName);
            } else {
                AllureTestDiagnostics.log("{} header was not present", headerName);
            }
        });
    }

    public static void assertAllowHeaderContains(Response response, String... methods) {
        AllureTestDiagnostics.step("Verify Allow header methods", () -> {
            String allow = response.header("Allow");
            assertThat(allow)
                .as("Allow response header")
                .isNotBlank();
            Arrays.stream(methods)
                .forEach(method -> assertThat(allow.toUpperCase(Locale.ROOT))
                    .as("Allow includes " + method)
                    .contains(method.toUpperCase(Locale.ROOT)));
            AllureTestDiagnostics.log("Allow header was {}", allow);
        });
    }

    private static String mediaType(String contentType) {
        assertThat(contentType)
            .as("Content-Type response header")
            .isNotBlank();
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }
}
