package com.aria.framework.restfulbooker;

import com.aria.framework.base.LiveRestfulBookerTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RestfulBooker health endpoint.
 */
@Epic("RestfulBooker API")
@Feature("Health check")
@Tag("live")
@ResourceLock("restfulbooker-api")
class HealthCheckTests extends LiveRestfulBookerTest {

    /**
     * Validates that the ping endpoint is available.
     */
    @Test
    @Tag("smoke")
    @DisplayName("GET /ping returns Created when the API is healthy")
    @Story("API health")
    void pingReturnsCreated() {
        Response response = bookingService.pingRaw();

        assertThat(response.statusCode()).isEqualTo(201);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.body().asString()).isEqualTo("Created");
    }
}
