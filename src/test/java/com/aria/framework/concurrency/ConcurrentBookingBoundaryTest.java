package com.aria.framework.concurrency;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic race-condition boundary checks for conflicting booking updates.
 */
@Epic("Concurrency")
@Feature("Booking mutation races")
class ConcurrentBookingBoundaryTest {

    private AtomicBookingFixture fixture;

    @BeforeEach
    void startFixture() throws IOException {
        fixture = new AtomicBookingFixture();
    }

    @AfterEach
    void stopFixture() {
        fixture.close();
    }

    @Test
    @Tag("regression")
    @DisplayName("Concurrent updates accept one mutation and reject the stale competitor")
    @Story("Reject stale concurrent mutation")
    void concurrentBookingUpdatesEnforceOptimisticConcurrency() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Response> competingUpdate = () -> {
            start.await();
            return RestAssured.given()
            .baseUri(fixture.baseUrl())
            .contentType("application/json")
            .header("If-Match", "\"1\"")
            .body("{\"firstname\":\"Concurrent\"}")
            .put("/booking/42");
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Response>> results = List.of(
                executor.submit(competingUpdate),
                executor.submit(competingUpdate)
            );
            start.countDown();

            step("Verify exactly one mutation succeeds", () -> {
                List<Integer> statuses = results.stream()
                    .map(result -> {
                        try {
                            return result.get().statusCode();
                        } catch (Exception exception) {
                            throw new IllegalStateException("Failed to read concurrent update response", exception);
                        }
                    })
                    .sorted()
                    .toList();
                log("Concurrent update statuses were {}", statuses);
                assertThat(statuses).containsExactly(200, 409);
                assertThat(fixture.version()).isEqualTo(2);
                assertThat(fixture.successfulMutations()).isEqualTo(1);
            });
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class AtomicBookingFixture implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService requestExecutor = Executors.newFixedThreadPool(2);
        private final AtomicInteger version = new AtomicInteger(1);
        private final AtomicInteger successfulMutations = new AtomicInteger();

        private AtomicBookingFixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/booking/42", this::handleUpdate);
            server.setExecutor(requestExecutor);
            server.start();
        }

        private void handleUpdate(HttpExchange exchange) throws IOException {
            try (exchange) {
                if (!"PUT".equals(exchange.getRequestMethod())) {
                    writeResponse(exchange, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }

                String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
                int expectedVersion = parseVersion(ifMatch);
                if (!version.compareAndSet(expectedVersion, expectedVersion + 1)) {
                    writeResponse(exchange, 409, "{\"error\":\"booking version conflict\"}");
                    return;
                }

                successfulMutations.incrementAndGet();
                exchange.getResponseHeaders().set("ETag", "\"" + version.get() + "\"");
                writeResponse(exchange, 200, "{\"id\":42,\"version\":" + version.get() + "}");
            }
        }

        private static int parseVersion(String value) {
            if (value == null) {
                return -1;
            }
            try {
                return Integer.parseInt(value.replace("\"", ""));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private int version() {
            return version.get();
        }

        private int successfulMutations() {
            return successfulMutations.get();
        }

        @Override
        public void close() {
            server.stop(0);
            requestExecutor.shutdownNow();
        }
    }
}
