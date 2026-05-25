package com.aria.framework.builders;

import com.aria.framework.models.request.BookingRequest;

import java.time.LocalDate;

/**
 * Fluent payload builder with valid defaults for tests.
 */
public class BookingPayloadBuilder {

    private String firstname = "James";
    private String lastname = "Smith";
    private int totalprice = 250;
    private boolean depositpaid = true;
    private LocalDate checkin = LocalDate.now().plusDays(1);
    private LocalDate checkout = LocalDate.now().plusDays(7);
    private String additionalneeds = "Dinner";

    public BookingPayloadBuilder withFirstname(String firstname) {
        this.firstname = firstname;
        return this;
    }

    public BookingPayloadBuilder withLastname(String lastname) {
        this.lastname = lastname;
        return this;
    }

    public BookingPayloadBuilder withTotalprice(int totalprice) {
        this.totalprice = totalprice;
        return this;
    }

    public BookingPayloadBuilder withDepositpaid(boolean depositpaid) {
        this.depositpaid = depositpaid;
        return this;
    }

    public BookingPayloadBuilder withDates(LocalDate checkin, LocalDate checkout) {
        this.checkin = checkin;
        this.checkout = checkout;
        return this;
    }

    public BookingPayloadBuilder withAdditionalneeds(String additionalneeds) {
        this.additionalneeds = additionalneeds;
        return this;
    }

    public BookingRequest build() {
        return BookingRequest.builder()
            .firstname(firstname)
            .lastname(lastname)
            .totalprice(totalprice)
            .depositpaid(depositpaid)
            .bookingdates(BookingRequest.BookingDates.builder()
                .checkin(checkin)
                .checkout(checkout)
                .build())
            .additionalneeds(additionalneeds)
            .build();
    }
}
