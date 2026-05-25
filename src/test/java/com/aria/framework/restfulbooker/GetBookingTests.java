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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.stream.Stream;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for retrieving RestfulBooker bookings.
 */
@Epic("RestfulBooker API")
@Feature("Booking retrieval")
@Tag("live")
@ResourceLock("restfulbooker-api")
class GetBookingTests extends LiveRestfulBookerTest {

    /**
     * Provides invalid booking identifiers for negative lookup tests.
     *
     * @return stream of invalid ids
     */
    static Stream<Integer> invalidBookingIds() {
        return Stream.of(0, -1, 99999999);
    }

    /**
     * Validates that all booking ids endpoint returns at least one id.
     */
    @Test
    @Tag("smoke")
    @DisplayName("GET /booking returns booking identifiers")
    @Story("List booking ids")
    void getAllBookingIdsReturnsIds() {
        Response response = bookingService.getAllBookingIdsRaw();

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getList("bookingid", Integer.class)).isNotEmpty();
    }

    /**
     * Validates fetching a booking that was created by the test.
     */
    @Test
    @Tag("regression")
    @DisplayName("GET /booking/{id} returns a created booking")
    @Story("Get booking by id")
    void getCreatedBookingById() {
        BookingRequest request = BookingDataFactory.validBooking();
        int bookingId = createTrackedBooking(request).jsonPath().getInt("bookingid");

        Response response = bookingService.getBookingRaw(bookingId);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("firstname")).isEqualTo(request.getFirstname());
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
    }

    /**
     * Validates not-found behavior for invalid booking identifiers.
     */
    @ParameterizedTest
    @MethodSource("invalidBookingIds")
    @Tag("negative")
    @DisplayName("GET /booking/{id} returns 404 for invalid booking ids")
    @Story("Booking not found")
    void getBookingWithInvalidIdReturnsNotFound(int bookingId) {
        Response response = bookingService.getBookingRaw(bookingId);

        assertThat(response.statusCode()).isEqualTo(404);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.body().asString()).containsIgnoringCase("not");
    }
}
