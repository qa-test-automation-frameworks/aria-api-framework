package com.aria.framework.models.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request payload class for creating or updating a booking in RestfulBooker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Jackson/Lombok request DTO intentionally exposes mutable nested payload fields.")
public class BookingRequest {

    private String firstname;
    private String lastname;
    private Integer totalprice;
    private Boolean depositpaid;
    private BookingDates bookingdates;
    private String additionalneeds;

    /**
     * Sub-DTO mapping check-in and check-out dates.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookingDates {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate checkin;

        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate checkout;
    }
}
