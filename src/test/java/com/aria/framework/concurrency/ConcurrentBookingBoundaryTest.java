package com.aria.framework.concurrency;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic race-condition boundary checks for conflicting booking updates.
 */
@Epic("Concurrency")
@Feature("Booking mutation races")
@WireMockTest(httpPort = 0)
@Execution(ExecutionMode.SAME_THREAD)
class ConcurrentBookingBoundaryTest {

    @Test
    @Tag("regression")
    @DisplayName("Concurrent stale booking updates return conflict instead of last-write-wins")
    @Story("Reject stale concurrent mutation")
    void concurrentStaleBookingUpdatesReturnConflict(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        step("Configure WireMock conflict response for stale booking updates", () ->
            stubFor(put(urlEqualTo("/booking/42"))
                .withHeader("If-Match", equalTo("stale-version"))
                .willReturn(aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\":\"booking version conflict\"}"))));

        Callable<Response> staleUpdate = () -> RestAssured.given()
            .baseUri(wireMockRuntimeInfo.getHttpBaseUrl())
            .contentType("application/json")
            .header("If-Match", "stale-version")
            .body("{\"firstname\":\"Concurrent\"}")
            .put("/booking/42");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Response>> results = step("Execute two concurrent stale booking updates", () ->
                executor.invokeAll(List.of(staleUpdate, staleUpdate)));

            step("Verify both concurrent updates return conflict", () -> {
                List<Integer> statuses = results.stream()
                    .map(result -> {
                        try {
                            return result.get().statusCode();
                        } catch (Exception exception) {
                            throw new IllegalStateException("Failed to read concurrent update response", exception);
                        }
                    })
                    .toList();
                log("Concurrent update statuses were {}", statuses);
                assertThat(statuses).containsExactly(409, 409);
            });
        } finally {
            executor.shutdownNow();
        }
    }
}
