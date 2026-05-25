package com.aria.framework.services;

import com.aria.framework.models.request.BookingRequest;
import io.restassured.response.Response;

public interface BookingSecurityOperations {

    Response updateBookingWithoutToken(int bookingId, BookingRequest request);

    Response updateBookingWithEmptyToken(int bookingId, BookingRequest request);

    Response updateBookingWithMalformedToken(int bookingId, BookingRequest request);

    Response updateBookingWithToken(int bookingId, Object request, String token);

    Response updateBookingWithHeader(int bookingId, Object request, String headerName, String headerValue);

    Response deleteBookingWithMalformedToken(int bookingId);

    Response deleteBookingWithoutToken(int bookingId);

    Response deleteBookingWithEmptyToken(int bookingId);

    Response createBookingPayload(Object request);

    Response getBookingAsUser(int bookingId, String userId);
}
