package com.aria.framework.services;

import com.aria.framework.models.request.BookingRequest;
import com.aria.framework.models.response.BookingResponse;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;

public interface BookingOperations {

    Response createBookingRaw(BookingRequest request);

    BookingResponse createBooking(BookingRequest request);

    Response getBookingRaw(int bookingId);

    BookingResponse getBooking(int bookingId);

    Response updateBookingRaw(int bookingId, BookingRequest request);

    BookingResponse updateBooking(int bookingId, BookingRequest request);

    Response partialUpdateBookingRaw(int bookingId, Map<String, Object> fields);

    BookingResponse partialUpdateBooking(int bookingId, Map<String, Object> fields);

    Response deleteBookingRaw(int bookingId);

    Response deleteBooking(int bookingId);

    Response getAllBookingIdsRaw();

    List<Integer> getAllBookingIds();

    Response pingRaw();
}
