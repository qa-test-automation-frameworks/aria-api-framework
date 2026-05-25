package com.aria.framework.data;

import com.aria.framework.models.request.BookingRequest;
import com.aria.framework.builders.BookingPayloadBuilder;
import net.datafaker.Faker;

import java.util.Map;
import java.time.LocalDate;

/**
 * Factory for valid and invalid RestfulBooker test payloads.
 */
public final class BookingDataFactory {

    private static final Faker FAKER = new Faker();
    private static final String RUN_ID = System.getProperty(
        "aria.run.id",
        System.getenv().getOrDefault("ARIA_RUN_ID", Long.toString(System.currentTimeMillis()))
    );

    private BookingDataFactory() {
    }

    /**
     * Creates a valid randomized booking payload.
     *
     * @return booking request payload
     */
    public static BookingRequest validBooking() {
        return new BookingPayloadBuilder()
            .withFirstname("Aria" + RUN_ID.substring(Math.max(0, RUN_ID.length() - 6)))
            .withLastname(FAKER.name().lastName().replaceAll("[^A-Za-z]", ""))
            .withTotalprice(FAKER.number().numberBetween(100, 500))
            .withDepositpaid(true)
            .withAdditionalneeds("Run " + RUN_ID)
            .build();
    }

    /**
     * Creates a booking payload with missing required fields for negative validation.
     *
     * @return invalid booking request payload
     */
    public static BookingRequest invalidBookingMissingName() {
        return new BookingPayloadBuilder()
            .withFirstname(null)
            .withLastname(null)
            .build();
    }

    public static BookingRequest bookingWithNegativePrice(int price) {
        return new BookingPayloadBuilder()
            .withTotalprice(price)
            .build();
    }

    public static BookingRequest bookingWithInvalidDateRange() {
        return new BookingPayloadBuilder()
            .withDates(LocalDate.now().plusDays(10), LocalDate.now().plusDays(2))
            .build();
    }

    public static BookingRequest bookingWithLongNames() {
        return new BookingPayloadBuilder()
            .withFirstname("A".repeat(256))
            .withLastname("B".repeat(256))
            .build();
    }

    public static BookingRequest bookingWithInjectionPayload() {
        return new BookingPayloadBuilder()
            .withFirstname("' OR '1'='1")
            .withLastname("<script>alert('x')</script>")
            .build();
    }

    /**
     * Creates a minimal partial update map.
     *
     * @return partial update fields
     */
    public static Map<String, Object> partialUpdateFields() {
        return Map.of(
            "firstname", "Patch" + FAKER.number().digits(4),
            "additionalneeds", "Late checkout"
        );
    }
}
