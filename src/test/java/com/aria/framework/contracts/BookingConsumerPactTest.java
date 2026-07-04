package com.aria.framework.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer contract tests for the RestfulBooker booking API.
 */
@Epic("Contracts")
@Feature("RestfulBooker Pact")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
class BookingConsumerPactTest {

    private static final String BOOKING_BODY = """
        {
          "firstname": "Jim",
          "lastname": "Brown",
          "totalprice": 111,
          "depositpaid": true,
          "bookingdates": {
            "checkin": "2026-05-24",
            "checkout": "2026-05-31"
          },
          "additionalneeds": "Breakfast"
        }
        """;

    /**
     * Defines a contract for fetching a booking by id.
     *
     * @param builder Pact V4 DSL builder
     * @return request-response pact
     */
    @Pact(consumer = "aria-api-framework", provider = "restful-booker")
    V4Pact getBookingPact(PactBuilder builder) {
        return builder
            .given("booking 1 exists")
            .expectsToReceiveHttpInteraction("a request for booking 1", interaction -> interaction
                .withRequest(request -> request
                    .method("GET")
                    .path("/booking/1"))
                .willRespondWith(response -> response
                    .status(200)
                    .header("Content-Type", "application/json")
                    .body("""
                        {
                          "firstname": "Jim",
                          "lastname": "Brown",
                          "totalprice": 111,
                          "depositpaid": true,
                          "bookingdates": {
                            "checkin": "2026-05-24",
                            "checkout": "2026-05-31"
                          },
                          "additionalneeds": "Breakfast"
                        }
                        """, "application/json")))
            .toPact();
    }

    @Pact(consumer = "aria-api-framework", provider = "restful-booker")
    V4Pact createBookingPact(PactBuilder builder) {
        return builder
            .expectsToReceiveHttpInteraction("a request to create a booking", interaction -> interaction
                .withRequest(request -> request
                    .method("POST")
                    .path("/booking")
                    .header("Content-Type", "application/json")
                    .body(BOOKING_BODY, "application/json"))
                .willRespondWith(response -> response
                    .status(200)
                    .header("Content-Type", "application/json")
                    .body("""
                        {
                          "bookingid": 2,
                          "booking": %s
                        }
                        """.replace("%s", BOOKING_BODY), "application/json")))
            .toPact();
    }

    @Pact(consumer = "aria-api-framework", provider = "restful-booker")
    V4Pact updateBookingPact(PactBuilder builder) {
        return builder
            .given("booking 1 exists")
            .expectsToReceiveHttpInteraction("a request to update booking 1", interaction -> interaction
                .withRequest(request -> request
                    .method("PUT")
                    .path("/booking/1")
                    .header("Cookie", "token=mock-token")
                    .header("Content-Type", "application/json")
                    .body(BOOKING_BODY, "application/json"))
                .willRespondWith(response -> response
                    .status(200)
                    .header("Content-Type", "application/json")
                    .body(BOOKING_BODY, "application/json")))
            .toPact();
    }

    @Pact(consumer = "aria-api-framework", provider = "restful-booker")
    V4Pact deleteBookingPact(PactBuilder builder) {
        return builder
            .given("booking 1 exists")
            .expectsToReceiveHttpInteraction("a request to delete booking 1", interaction -> interaction
                .withRequest(request -> request
                    .method("DELETE")
                    .path("/booking/1")
                    .header("Cookie", "token=mock-token"))
                .willRespondWith(response -> response
                    .status(201)
                    .body("Created")))
            .toPact();
    }

    @Pact(consumer = "aria-api-framework", provider = "restful-booker")
    V4Pact missingBookingPact(PactBuilder builder) {
        return builder
            .given("booking 999 does not exist")
            .expectsToReceiveHttpInteraction("a request for missing booking 999", interaction -> interaction
                .withRequest(request -> request
                    .method("GET")
                    .path("/booking/999"))
                .willRespondWith(response -> response
                    .status(404)
                    .body("Not Found")))
            .toPact();
    }

    /**
     * Verifies the booking consumer expectation against the Pact mock server.
     *
     * @param mockServer Pact mock server
     */
    @Test
    @Tag("contract")
    @DisplayName("GET /booking/{id} satisfies the RestfulBooker consumer pact")
    @Story("Booking consumer contract")
    @PactTestFor(pactMethod = "getBookingPact")
    void getBookingMatchesConsumerContract(MockServer mockServer) {
        Response response = step("Call Pact mock server GET /booking/1", () -> RestAssured.given()
            .baseUri(mockServer.getUrl())
            .accept("application/json")
            .get("/booking/1"));
        log("Pact GET /booking/1 returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("firstname")).isEqualTo("Jim");
    }

    @Test
    @Tag("contract")
    @DisplayName("POST /booking satisfies the RestfulBooker consumer pact")
    @Story("Create booking consumer contract")
    @PactTestFor(pactMethod = "createBookingPact")
    void createBookingMatchesConsumerContract(MockServer mockServer) {
        Response response = step("Call Pact mock server POST /booking", () -> RestAssured.given()
            .baseUri(mockServer.getUrl())
            .contentType("application/json")
            .body(BOOKING_BODY)
            .post("/booking"));
        log("Pact POST /booking returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertThat(response.jsonPath().getInt("bookingid")).isEqualTo(2);
    }

    @Test
    @Tag("contract")
    @DisplayName("PUT /booking/{id} satisfies the RestfulBooker consumer pact")
    @Story("Update booking consumer contract")
    @PactTestFor(pactMethod = "updateBookingPact")
    void updateBookingMatchesConsumerContract(MockServer mockServer) {
        Response response = step("Call Pact mock server PUT /booking/1", () -> RestAssured.given()
            .baseUri(mockServer.getUrl())
            .contentType("application/json")
            .header("Cookie", "token=mock-token")
            .body(BOOKING_BODY)
            .put("/booking/1"));
        log("Pact PUT /booking/1 returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertThat(response.jsonPath().getString("firstname")).isEqualTo("Jim");
    }

    @Test
    @Tag("contract")
    @DisplayName("DELETE /booking/{id} satisfies the RestfulBooker consumer pact")
    @Story("Delete booking consumer contract")
    @PactTestFor(pactMethod = "deleteBookingPact")
    void deleteBookingMatchesConsumerContract(MockServer mockServer) {
        Response response = step("Call Pact mock server DELETE /booking/1", () -> RestAssured.given()
            .baseUri(mockServer.getUrl())
            .header("Cookie", "token=mock-token")
            .delete("/booking/1"));
        log("Pact DELETE /booking/1 returned {}", response.statusCode());

        step("Verify DELETE /booking/1 pact response", () -> {
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body().asString()).isEqualTo("Created");
        });
    }

    @Test
    @Tag("contract")
    @DisplayName("GET /booking/{id} not-found satisfies the RestfulBooker consumer pact")
    @Story("Booking not-found consumer contract")
    @PactTestFor(pactMethod = "missingBookingPact")
    void missingBookingMatchesConsumerContract(MockServer mockServer) {
        Response response = step("Call Pact mock server GET /booking/999", () -> RestAssured.given()
            .baseUri(mockServer.getUrl())
            .accept("application/json")
            .get("/booking/999"));
        log("Pact GET /booking/999 returned {}", response.statusCode());

        step("Verify GET /booking/999 pact not-found response", () -> {
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body().asString()).isEqualTo("Not Found");
        });
    }
}
