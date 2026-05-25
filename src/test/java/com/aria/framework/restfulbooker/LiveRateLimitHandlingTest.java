package com.aria.framework.restfulbooker;

import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.utils.RetryUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Epic("Resilience")
@Feature("Live rate-limit handling")
@Tag("live")
@Tag("regression")
class LiveRateLimitHandlingTest {

    @Test
    @DisplayName("GET retry policy handles a controlled live 429 endpoint")
    void liveRateLimitEndpointUsesRetryPolicy() {
        String rateLimitUrl = System.getenv("ARIA_RATE_LIMIT_TEST_URL");
        assumeTrue(rateLimitUrl != null && !rateLimitUrl.isBlank(), "ARIA_RATE_LIMIT_TEST_URL is not configured");

        FrameworkConfig retryConfig = new FrameworkConfig(
            "live-rate-limit",
            rateLimitUrl,
            rateLimitUrl,
            "",
            "",
            "",
            5,
            1_000,
            2,
            10,
            25,
            0
        );

        Response response = RetryUtils.executeGetWithRetry(retryConfig, () -> RestAssured.given().get(rateLimitUrl));

        assertThat(response.statusCode()).isEqualTo(429);
    }
}
