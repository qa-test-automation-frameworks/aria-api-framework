package com.aria.framework.services;

import com.aria.framework.clients.BookingApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.models.request.BookingRequest;
import com.aria.framework.models.response.BookingResponse;
import com.aria.framework.utils.TokenManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

/**
 * Service Object layer orchestrating restful-booker booking operations.
 * Exposes both raw responses (for assertions) and structured DTO records.
 */
public class BookingService implements BookingOperations {

    private final BookingApiClient apiClient;
    private final TokenManager tokenManager;

    public BookingService() {
        this(new BookingApiClient());
    }

    public BookingService(FrameworkConfig config) {
        this(new BookingApiClient(config), new TokenManager(config));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Constructor injection intentionally stores a stateless API client adapter.")
    public BookingService(BookingApiClient apiClient) {
        this(apiClient, new TokenManager(apiClient.config()));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Constructor injection intentionally stores stateless API client and scoped token cache adapters.")
    public BookingService(BookingApiClient apiClient, TokenManager tokenManager) {
        this.apiClient = apiClient;
        this.tokenManager = tokenManager;
    }

    /**
     * POST /booking - Create booking raw request.
     */
    public Response createBookingRaw(BookingRequest request) {
        return apiClient.createBooking(request);
    }

    public Response createBookingPayloadRaw(Object request) {
        return apiClient.createBookingPayload(request);
    }

    /**
     * POST /booking - Create booking and return parsed DTO.
     */
    public BookingResponse createBooking(BookingRequest request) {
        return createBookingRaw(request).as(BookingResponse.class);
    }

    /**
     * GET /booking/{id} - Get booking raw request.
     */
    public Response getBookingRaw(int bookingId) {
        return apiClient.getBooking(bookingId);
    }

    public Response getBookingWithHeaderRaw(int bookingId, String headerName, String headerValue) {
        return apiClient.getBookingWithHeader(bookingId, headerName, headerValue);
    }

    /**
     * GET /booking/{id} - Get booking details.
     */
    public BookingResponse getBooking(int bookingId) {
        return getBookingRaw(bookingId).as(BookingResponse.class);
    }

    /**
     * PUT /booking/{id} - Full update raw request.
     */
    public Response updateBookingRaw(int bookingId, BookingRequest request) {
        String token = tokenManager.getRestfulBookerToken();
        return apiClient.updateBooking(bookingId, request, token);
    }

    /**
     * PUT /booking/{id} - Full update on booking details.
     */
    public BookingResponse updateBooking(int bookingId, BookingRequest request) {
        return updateBookingRaw(bookingId, request).as(BookingResponse.class);
    }

    /**
     * PATCH /booking/{id} - Partial update raw request.
     */
    public Response partialUpdateBookingRaw(int bookingId, Map<String, Object> fields) {
        String token = tokenManager.getRestfulBookerToken();
        return apiClient.partialUpdateBooking(bookingId, fields, token);
    }

    /**
     * PATCH /booking/{id} - Partial update on booking details.
     */
    public BookingResponse partialUpdateBooking(int bookingId, Map<String, Object> fields) {
        return partialUpdateBookingRaw(bookingId, fields).as(BookingResponse.class);
    }

    /**
     * DELETE /booking/{id} - Delete booking raw request.
     */
    public Response deleteBookingRaw(int bookingId) {
        String token = tokenManager.getRestfulBookerToken();
        return apiClient.deleteBooking(bookingId, token);
    }

    /**
     * DELETE /booking/{id} - Delete booking and return the raw response for caller assertions.
     */
    public Response deleteBooking(int bookingId) {
        return deleteBookingRaw(bookingId);
    }

    /**
     * GET /booking - Retrieve all booking IDs raw response.
     */
    public Response getAllBookingIdsRaw() {
        return apiClient.getBookingIds();
    }

    /**
     * GET /booking - Retrieve all booking IDs.
     */
    public List<Integer> getAllBookingIds() {
        return getAllBookingIdsRaw().jsonPath().getList("bookingid", Integer.class);
    }

    /**
     * GET /ping - Service health check status raw response.
     */
    public Response pingRaw() {
        return apiClient.ping();
    }

    public Response optionsBookingRaw() {
        return apiClient.optionsBooking();
    }
}
