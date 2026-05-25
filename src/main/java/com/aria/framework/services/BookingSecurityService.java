package com.aria.framework.services;

import com.aria.framework.clients.BookingApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.models.request.BookingRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.restassured.response.Response;

/**
 * Service object for intentionally unauthenticated or malformed-auth booking scenarios.
 */
public class BookingSecurityService implements BookingSecurityOperations {

    private final BookingApiClient apiClient;

    public BookingSecurityService() {
        this(new BookingApiClient());
    }

    public BookingSecurityService(FrameworkConfig config) {
        this(new BookingApiClient(config));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Constructor injection intentionally stores a stateless API client adapter.")
    public BookingSecurityService(BookingApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public Response updateBookingWithoutToken(int bookingId, BookingRequest request) {
        return apiClient.updateBookingWithoutAuth(bookingId, request);
    }

    public Response updateBookingWithEmptyToken(int bookingId, BookingRequest request) {
        return apiClient.updateBooking(bookingId, request, "");
    }

    public Response updateBookingWithMalformedToken(int bookingId, BookingRequest request) {
        return apiClient.updateBooking(bookingId, request, "malformed-token");
    }

    public Response updateBookingWithToken(int bookingId, Object request, String token) {
        return apiClient.updateBookingPayload(bookingId, request, token);
    }

    public Response updateBookingWithHeader(int bookingId, Object request, String headerName, String headerValue) {
        return apiClient.updateBookingPayloadWithHeader(bookingId, request, headerName, headerValue);
    }

    public Response deleteBookingWithMalformedToken(int bookingId) {
        return apiClient.deleteBooking(bookingId, "malformed-token");
    }

    public Response deleteBookingWithoutToken(int bookingId) {
        return apiClient.deleteBookingWithoutAuth(bookingId);
    }

    public Response deleteBookingWithEmptyToken(int bookingId) {
        return apiClient.deleteBooking(bookingId, "");
    }

    public Response createBookingPayload(Object request) {
        return apiClient.createBookingPayload(request);
    }

    public Response getBookingAsUser(int bookingId, String userId) {
        return apiClient.getBookingWithHeader(bookingId, "X-User-Id", userId);
    }
}
