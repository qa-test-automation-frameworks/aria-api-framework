package com.aria.framework.restfulbooker;

import com.aria.framework.base.LiveRestfulBookerTest;
import com.aria.framework.data.BookingDataFactory;
import com.aria.framework.models.request.BookingRequest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Map;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for updating RestfulBooker bookings.
 */
@Epic("RestfulBooker API")
@Feature("Booking update")
@Tag("live")
@ResourceLock("restfulbooker-api")
class UpdateBookingTests extends LiveRestfulBookerTest {

    /**
     * Validates a full booking update.
     */
    @Test
    @Tag("regression")
    @DisplayName("PUT /booking/{id} fully updates an existing booking")
    @Story("Full update booking")
    void updateBookingWithValidPayload() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");
        BookingRequest update = BookingDataFactory.validBooking();

        Response response = bookingService.updateBookingRaw(bookingId, update);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("firstname")).isEqualTo(update.getFirstname());
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
    }

    @Test
    @Tag("regression")
    @DisplayName("PUT /booking/{id} is idempotent for the same payload")
    @Story("Full update idempotency")
    void putBookingIsIdempotent() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");
        BookingRequest update = BookingDataFactory.validBooking();

        Response firstUpdate = bookingService.updateBookingRaw(bookingId, update);
        Response firstRead = bookingService.getBookingRaw(bookingId);
        Response secondUpdate = bookingService.updateBookingRaw(bookingId, update);
        Response secondRead = bookingService.getBookingRaw(bookingId);

        assertThat(firstUpdate.statusCode()).isEqualTo(200);
        assertThat(secondUpdate.statusCode()).isEqualTo(200);
        assertJsonContentType(secondRead);
        assertResponseTimeWithinConfiguredSla(secondRead);
        assertThat(firstRead.body().asString()).isEqualTo(secondRead.body().asString());
        responseSchema(secondRead);
    }

    /**
     * Validates a partial booking update.
     */
    @Test
    @Tag("regression")
    @DisplayName("PATCH /booking/{id} partially updates an existing booking")
    @Story("Partial update booking")
    void partialUpdateBookingWithValidFields() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");
        Map<String, Object> fields = BookingDataFactory.partialUpdateFields();

        Response response = bookingService.partialUpdateBookingRaw(bookingId, fields);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("firstname")).isEqualTo(fields.get("firstname"));
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
    }

    /**
     * Validates unauthorized update behavior without a token.
     */
    @Test
    @Tag("negative")
    @DisplayName("PUT /booking/{id} returns 403 when auth cookie is omitted")
    @Story("Reject unauthenticated update")
    void updateBookingWithoutAuthCookieReturnsForbidden() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");
        Response response = bookingSecurityService.updateBookingWithoutToken(bookingId, BookingDataFactory.validBooking());

        assertThat(response.statusCode()).isEqualTo(403);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.body().asString()).isNotBlank();
    }

    private static void responseSchema(Response response) {
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
    }
}
