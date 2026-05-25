package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response model for RestfulBooker booking operations.
 * Handles both the wrapped creation/update responses (containing bookingid + booking details)
 * and direct GET responses (containing raw details).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingResponse(
    Integer bookingid,
    BookingDetails booking,
    String firstname,
    String lastname,
    Integer totalprice,
    Boolean depositpaid,
    BookingDates bookingdates,
    String additionalneeds
) {

    /**
     * Nested check-in and check-out dates response object.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingDates(
        String checkin,
        String checkout
    ) {}

    /**
     * Inner class representing the detail body returned inside creation/update payloads.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookingDetails(
        String firstname,
        String lastname,
        Integer totalprice,
        Boolean depositpaid,
        BookingDates bookingdates,
        String additionalneeds
    ) {}
}
