package com.aria.framework.restfulbooker;

import com.aria.framework.base.LiveRestfulBookerTest;
import com.aria.framework.models.request.AuthRequest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for RestfulBooker authentication endpoint.
 */
@Epic("RestfulBooker API")
@Feature("Authentication")
@Tag("live")
@ResourceLock("restfulbooker-api")
class AuthTests extends LiveRestfulBookerTest {

    /**
     * Validates token generation for configured RestfulBooker credentials.
     */
    @Test
    @Tag("smoke")
    @DisplayName("POST /auth returns a non-empty token for valid credentials")
    @Story("Generate auth token")
    void authenticateWithValidCredentialsReturnsToken() {
        AuthRequest request = AuthRequest.builder()
            .username(CONFIG.getBookerUsername())
            .password(CONFIG.getBookerPassword())
            .build();

        Response response = authService.authenticateRaw(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("token")).isNotBlank();
    }

    /**
     * Validates error handling for invalid credentials.
     */
    @Test
    @Tag("negative")
    @DisplayName("POST /auth returns Bad credentials for invalid credentials")
    @Story("Reject invalid auth")
    void authenticateWithInvalidCredentialsReturnsReason() {
        AuthRequest request = AuthRequest.builder()
            .username("invalid-user")
            .password("invalid-password")
            .build();

        Response response = authService.authenticateRaw(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("reason")).isEqualTo("Bad credentials");
    }
}
