package com.aria.framework.mocks;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers-backed WireMock smoke test proving containerized dependencies are executable.
 */
@Epic("Mocks")
@Feature("Testcontainers WireMock")
@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class ContainerizedWireMockBookingTest {

    @Container
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.5.4"))
        .withExposedPorts(8080)
        .withCommand("--verbose");

    @Test
    @Tag("smoke")
    @Tag("container")
    @DisplayName("Testcontainers WireMock serves a deterministic booking stub")
    @Story("Containerized mock dependency")
    void containerizedWireMockServesBookingStub() {
        String baseUrl = "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
        log("Containerized WireMock base URL resolved to {}", baseUrl);

        Response stubResponse = step("Register WireMock booking stub through admin API", () -> RestAssured.given()
            .baseUri(baseUrl)
            .contentType("application/json")
            .body("""
                {
                  "request": { "method": "GET", "url": "/booking/42" },
                  "response": {
                    "status": 200,
                    "headers": { "Content-Type": "application/json" },
                    "jsonBody": {
                      "firstname": "Container",
                      "lastname": "Mock",
                      "totalprice": 120,
                      "depositpaid": true,
                      "bookingdates": { "checkin": "2026-05-25", "checkout": "2026-05-30" },
                      "additionalneeds": "Breakfast"
                    }
                  }
                }
                """)
            .post("/__admin/mappings"));

        Response bookingResponse = step("Call containerized WireMock GET /booking/42", () -> RestAssured.given()
            .baseUri(baseUrl)
            .accept("application/json")
            .get("/booking/42"));
        log(
            "WireMock admin status={}, booking status={}",
            stubResponse.statusCode(),
            bookingResponse.statusCode()
        );

        assertThat(stubResponse.statusCode())
            .as("WireMock admin stub registration status")
            .isEqualTo(201);
        assertThat(bookingResponse.statusCode())
            .as("Containerized WireMock GET /booking/42 status")
            .isEqualTo(200);
        assertJsonContentType(bookingResponse);
        assertThat(bookingResponse.jsonPath().getString("firstname"))
            .as("Stubbed booking firstname")
            .isEqualTo("Container");
    }
}
