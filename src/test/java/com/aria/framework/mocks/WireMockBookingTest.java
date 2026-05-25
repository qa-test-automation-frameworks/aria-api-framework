package com.aria.framework.mocks;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.SocketException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import com.aria.framework.utils.RetryUtils;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock tests for deterministic RestfulBooker scenarios.
 */
@Epic("Mocks")
@Feature("WireMock RestfulBooker")
@WireMockTest(httpPort = 0)
@Execution(ExecutionMode.SAME_THREAD)
class WireMockBookingTest {

    private static final String RATE_LIMIT_SCENARIO = "booking rate limit";

    /**
     * Validates a stubbed auth token response.
     */
    @Test
    @Tag("smoke")
    @DisplayName("WireMock POST /auth returns a mock token")
    @Story("Mock auth")
    void stubAuthReturnsToken(WireMockRuntimeInfo wireMockRuntimeInfo) {
        step("Configure WireMock auth token stub", () ->
            stubFor(post(urlEqualTo("/auth"))
                .withRequestBody(equalToJson("""
                    {"username":"mock-user","password":"mock-password"}
                    """, true, true))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"token\":\"mock-token\"}"))));

        Response response = step("Call WireMock POST /auth", () -> RestAssured.given()
            .baseUri(wireMockRuntimeInfo.getHttpBaseUrl())
            .contentType("application/json")
            .body("{\"username\":\"mock-user\",\"password\":\"mock-password\"}")
            .post("/auth"));
        log("WireMock POST /auth returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("token")).isEqualTo("mock-token");
    }

    /**
     * Validates a stubbed booking list response.
     */
    @Test
    @Tag("regression")
    @DisplayName("WireMock GET /booking returns fixed booking identifiers")
    @Story("Mock booking list")
    void stubBookingListReturnsIds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        step("Configure WireMock booking list stub", () ->
            stubFor(get(urlEqualTo("/booking"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"bookingid\":1},{\"bookingid\":2}]"))));

        Response response = step("Call WireMock GET /booking", () -> RestAssured.given()
            .baseUri(wireMockRuntimeInfo.getHttpBaseUrl())
            .accept("application/json")
            .get("/booking"));
        log("WireMock GET /booking returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getList("bookingid", Integer.class)).containsExactly(1, 2);
    }

    /**
     * Validates resilience behavior for a stubbed unavailable dependency.
     */
    @Test
    @Tag("negative")
    @DisplayName("WireMock GET /booking returns 503 for unavailable dependency")
    @Story("Mock service unavailable")
    void stubBookingListReturnsServiceUnavailable(WireMockRuntimeInfo wireMockRuntimeInfo) {
        step("Configure WireMock unavailable dependency stub", () ->
            stubFor(get(urlEqualTo("/booking"))
                .willReturn(aResponse()
                    .withStatus(503)
                    .withFixedDelay(100)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\":\"service unavailable\"}"))));

        Response response = step("Call WireMock GET /booking for 503 scenario", () -> RestAssured.given()
            .baseUri(wireMockRuntimeInfo.getHttpBaseUrl())
            .accept("application/json")
            .get("/booking"));
        log("WireMock GET /booking unavailable scenario returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(503);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("message")).isEqualTo("service unavailable");
    }

    @Test
    @Tag("regression")
    @DisplayName("WireMock GET /booking retries once after 429 Retry-After")
    @Story("Rate-limit retry")
    void stubBookingListRetriesAfterRateLimit(WireMockRuntimeInfo wireMockRuntimeInfo) {
        step("Configure WireMock rate-limit then success stubs", () -> {
            stubFor(get(urlEqualTo("/booking"))
                .inScenario(RATE_LIMIT_SCENARIO)
                .whenScenarioStateIs("Started")
                .willSetStateTo("rate limit cleared")
                .willReturn(aResponse()
                    .withStatus(429)
                    .withHeader("Retry-After", "1")
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"message\":\"rate limited\"}")));
            stubFor(get(urlEqualTo("/booking"))
                .inScenario(RATE_LIMIT_SCENARIO)
                .whenScenarioStateIs("rate limit cleared")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"bookingid\":1}]")));
        });
        AtomicInteger calls = new AtomicInteger();

        Response response = step("Call GET /booking through retry utility", () -> RetryUtils.executeWithRetry(
            "GET",
            () -> {
                calls.incrementAndGet();
                return RestAssured.given()
                    .baseUri(wireMockRuntimeInfo.getHttpBaseUrl())
                    .accept("application/json")
                    .get("/booking");
            },
            new RetryUtils.RetryPolicy(2, 1, 10, 0),
            ignored -> { },
            true
        ));
        log("Retry scenario returned {} after {} calls", response.statusCode(), calls.get());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertThat(response.jsonPath().getInt("[0].bookingid")).isEqualTo(1);
        assertThat(calls).hasValue(2);
    }

    /**
     * Validates fault injection for a dependency connection reset.
     */
    @Test
    @Tag("negative")
    @DisplayName("WireMock GET /booking can simulate a connection reset fault")
    @Story("Mock network fault")
    void stubBookingListConnectionResetFault(WireMockRuntimeInfo wireMockRuntimeInfo) {
        step("Configure WireMock connection reset fault", () ->
            stubFor(get(urlEqualTo("/booking"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))));

        step("Verify GET /booking raises connection reset", () -> {
            try {
                RestAssured.given()
                    .baseUri(wireMockRuntimeInfo.getHttpBaseUrl())
                    .accept("application/json")
                    .get("/booking");
            } catch (Exception exception) {
                log("Connection reset scenario raised {}", exception.getClass().getName());
                assertThat(exception).isInstanceOf(SocketException.class);
                assertThat(exception).hasMessageContaining("Connection reset");
                return;
            }
            throw new AssertionError("Expected connection reset fault to raise an exception");
        });
    }
}
