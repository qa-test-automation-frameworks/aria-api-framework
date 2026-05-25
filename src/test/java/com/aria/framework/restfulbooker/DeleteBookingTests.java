package com.aria.framework.restfulbooker;

import com.aria.framework.base.LiveRestfulBookerTest;
import com.aria.framework.data.BookingDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for deleting RestfulBooker bookings.
 */
@Epic("RestfulBooker API")
@Feature("Booking deletion")
@Tag("live")
@ResourceLock("restfulbooker-api")
class DeleteBookingTests extends LiveRestfulBookerTest {

    /**
     * Validates deleting a booking and subsequent not-found behavior.
     */
    @Test
    @Tag("regression")
    @DisplayName("DELETE /booking/{id} removes an existing booking")
    @Story("Delete booking")
    void deleteExistingBooking() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");

        Response deleteResponse = bookingService.deleteBookingRaw(bookingId);
        Response getResponse = bookingService.getBookingRaw(bookingId);

        assertThat(deleteResponse.statusCode()).isEqualTo(201);
        assertResponseTimeWithinConfiguredSla(deleteResponse);
        assertThat(deleteResponse.body().asString()).containsIgnoringCase("created");
        assertThat(getResponse.statusCode()).isEqualTo(404);
        assertResponseTimeWithinConfiguredSla(getResponse);
        unregisterCreatedBooking(bookingId);
    }

    /**
     * Validates deleting a missing booking id.
     */
    @Test
    @Tag("negative")
    @Tag("known-demo-api-limitations")
    @DisplayName("DELETE /booking/{id} returns not-found or method-not-allowed for a missing booking")
    @Story("Reject missing delete")
    void deleteMissingBookingReturnsMethodNotAllowed() {
        Response response = bookingService.deleteBookingRaw(99999999);

        assertThat(response.statusCode()).isIn(404, 405);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.body().asString()).isNotBlank();
    }
}
