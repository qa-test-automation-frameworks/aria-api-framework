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
 * Live authentication boundary tests for Restful Booker booking mutations.
 */
@Epic("RestfulBooker API")
@Feature("Booking authorization")
@Tag("live")
@Tag("security")
@ResourceLock("restfulbooker-api")
class BookingSecurityTests extends LiveRestfulBookerTest {

    @Test
    @DisplayName("PUT /booking/{id} returns 403 for a malformed auth token")
    @Story("Reject malformed update token")
    void updateBookingWithMalformedTokenReturnsForbidden() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");

        Response response = bookingSecurityService.updateBookingWithMalformedToken(
            bookingId,
            BookingDataFactory.validBooking()
        );

        assertThat(response.statusCode()).isEqualTo(403);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.body().asString()).isNotBlank();
    }

    @Test
    @DisplayName("DELETE /booking/{id} returns 403 for a malformed auth token")
    @Story("Reject malformed delete token")
    void deleteBookingWithMalformedTokenReturnsForbidden() {
        int bookingId = createTrackedBooking(BookingDataFactory.validBooking()).jsonPath().getInt("bookingid");

        Response response = bookingSecurityService.deleteBookingWithMalformedToken(bookingId);

        assertThat(response.statusCode()).isEqualTo(403);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.body().asString()).isNotBlank();
    }
}
