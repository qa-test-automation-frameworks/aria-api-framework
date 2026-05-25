package com.aria.framework.clients;

import com.aria.framework.auth.CookieTokenAuthStrategy;
import com.aria.framework.config.ConfigManager;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.models.request.BookingRequest;
import com.aria.framework.utils.JsonUtils;
import com.aria.framework.utils.RetryUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * REST Client mapping restful-booker booking operations (/booking, /ping).
 */
public class BookingApiClient extends BaseApiClient {

    private final String baseUrl;

    public BookingApiClient() {
        this(ConfigManager.defaults().getFrameworkConfig());
    }

    public BookingApiClient(ConfigManager configManager) {
        this(configManager.getFrameworkConfig());
    }

    public BookingApiClient(FrameworkConfig config) {
        super(config);
        this.baseUrl = config.baseUrl();
    }

    /**
     * GET /ping - Simple health check.
     */
    public Response ping() {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .get("/ping"));
    }

    /**
     * GET /booking - Retrieve all booking IDs.
     */
    public Response getBookingIds() {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .get("/booking"));
    }

    /**
     * GET /booking/{id} - Retrieve details for a specific booking.
     */
    public Response getBooking(int bookingId) {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .pathParam("id", bookingId)
            .get("/booking/{id}"));
    }

    /**
     * POST /booking - Create a new booking.
     */
    public Response createBooking(BookingRequest request) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .body(JsonUtils.serialize(request))
            .post("/booking"));
    }

    public Response createBookingPayload(Object request) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .body(JsonUtils.serialize(request))
            .post("/booking"));
    }

    /**
     * PUT /booking/{id} - Full update on an existing booking.
     */
    public Response updateBooking(int bookingId, BookingRequest request, String token) {
        return RetryUtils.executeMutationWithRetry(config, "PUT", true, () -> RestAssured.given()
            .spec(authenticatedBookingSpec(bookingId, token))
            .body(JsonUtils.serialize(request))
            .put("/booking/{id}"));
    }

    public Response updateBookingPayload(int bookingId, Object request, String token) {
        return RetryUtils.executeMutationWithRetry(config, "PUT", true, () -> RestAssured.given()
            .spec(authenticatedBookingSpec(bookingId, token))
            .body(JsonUtils.serialize(request))
            .put("/booking/{id}"));
    }

    public Response updateBookingPayloadWithHeader(int bookingId, Object request, String headerName, String headerValue) {
        return RetryUtils.executeMutationWithRetry(config, "PUT", true, () -> RestAssured.given()
            .spec(bookingSpec(bookingId))
            .header(headerName, headerValue)
            .body(JsonUtils.serialize(request))
            .put("/booking/{id}"));
    }

    public Response updateBookingWithoutAuth(int bookingId, BookingRequest request) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(bookingSpec(bookingId))
            .body(JsonUtils.serialize(request))
            .put("/booking/{id}"));
    }

    /**
     * PATCH /booking/{id} - Partial update on an existing booking.
     */
    public Response partialUpdateBooking(int bookingId, Map<String, Object> fields, String token) {
        return RetryUtils.executeMutationWithRetry(config, "PATCH", true, () -> RestAssured.given()
            .spec(authenticatedBookingSpec(bookingId, token))
            .body(fields)
            .patch("/booking/{id}"));
    }

    public Response partialUpdateBookingWithoutAuth(int bookingId, Map<String, Object> fields) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(bookingSpec(bookingId))
            .body(fields)
            .patch("/booking/{id}"));
    }

    /**
     * DELETE /booking/{id} - Delete an existing booking.
     */
    public Response deleteBooking(int bookingId, String token) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(authenticatedBookingSpec(bookingId, token))
            .delete("/booking/{id}"));
    }

    public Response deleteBookingWithoutAuth(int bookingId) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(bookingSpec(bookingId))
            .delete("/booking/{id}"));
    }

    private RequestSpecification authenticatedBookingSpec(int bookingId, String token) {
        return new CookieTokenAuthStrategy("token", token).apply(bookingSpec(bookingId));
    }

    public Response getBookingWithHeader(int bookingId, String headerName, String headerValue) {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(bookingSpec(bookingId))
            .header(headerName, headerValue)
            .get("/booking/{id}"));
    }

    public Response optionsBooking() {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .options("/booking"));
    }

    private RequestSpecification bookingSpec(int bookingId) {
        return getRequestSpec(baseUrl).pathParam("id", bookingId);
    }
}
