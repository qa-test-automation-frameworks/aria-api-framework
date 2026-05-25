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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * Tests for creating RestfulBooker bookings.
 */
@Epic("RestfulBooker API")
@Feature("Booking creation")
@Tag("live")
@ResourceLock("restfulbooker-api")
class CreateBookingTests extends LiveRestfulBookerTest {

    /**
     * Validates successful booking creation and response schema.
     */
    @Test
    @Tag("smoke")
    @Tag("regression")
    @DisplayName("POST /booking creates a booking with complete payload")
    @Story("Create booking")
    void createBookingWithValidPayload() {
        BookingRequest request = BookingDataFactory.validBooking();

        Response response = createTrackedBooking(request);

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                .as("POST /booking create status")
                .isEqualTo(200);
            softly.assertThat(response.time())
                .as("POST /booking response time")
                .isLessThan(CONFIG.getResponseTimeSlaMs());
            softly.assertThat(response.jsonPath().getInt("bookingid"))
                .as("created booking id")
                .isPositive();
            softly.assertThat(response.jsonPath().getString("booking.firstname"))
                .as("created booking firstname")
                .isEqualTo(request.getFirstname());
        });
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
    }

    /**
     * Validates server behavior for an incomplete booking payload.
     */
    @Test
    @Tag("known-demo-api-limitations")
    @DisplayName("POST /booking documents missing-name behavior in the public demo API")
    @Story("Document weak input validation")
    void createBookingWithMissingNameDocumentsDemoApiBehavior() {
        Response response = createTrackedBooking(BookingDataFactory.invalidBookingMissingName());

        assertThat(response.statusCode())
            .as("Restful Booker demo currently accepts null names instead of rejecting them")
            .isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("booking.firstname")).isNull();
        assertThat(response.jsonPath().getString("booking.lastname")).isNull();
    }

    @ParameterizedTest(name = "POST /booking documents accepted invalid totalprice {0}")
    @ValueSource(ints = {-1, -100, Integer.MIN_VALUE})
    @Tag("known-demo-api-limitations")
    @DisplayName("POST /booking documents accepted invalid totalprice boundary values")
    @Story("Document weak boundary validation")
    void createBookingWithInvalidPriceBoundaryDocumentsDemoApiBehavior(int price) {
        Response response = createTrackedBooking(BookingDataFactory.bookingWithNegativePrice(price));

        assertThat(response.statusCode())
            .as("Restful Booker demo currently accepts invalid totalprice values")
            .isEqualTo(200);
        assertThat(response.time())
            .as("POST /booking invalid price response time")
            .isLessThan(CONFIG.getResponseTimeSlaMs());
        assertJsonContentType(response);
        assertThat(response.jsonPath().getInt("booking.totalprice"))
            .as("accepted invalid totalprice is echoed")
            .isEqualTo(price);
    }

    @Test
    @Tag("known-demo-api-limitations")
    @DisplayName("POST /booking documents accepted checkout before checkin")
    @Story("Document weak date validation")
    void createBookingWithInvalidDateRangeDocumentsDemoApiBehavior() {
        BookingRequest request = BookingDataFactory.bookingWithInvalidDateRange();
        Response response = createTrackedBooking(request);

        assertThat(response.statusCode())
            .as("Restful Booker demo currently accepts checkout before checkin")
            .isEqualTo(200);
        assertThat(response.time())
            .as("POST /booking invalid date range response time")
            .isLessThan(CONFIG.getResponseTimeSlaMs());
        assertJsonContentType(response);
        assertThat(response.jsonPath().getString("booking.bookingdates.checkin"))
            .isEqualTo(request.getBookingdates().getCheckin().toString());
        assertThat(response.jsonPath().getString("booking.bookingdates.checkout"))
            .isEqualTo(request.getBookingdates().getCheckout().toString());
    }

    @Test
    @Tag("known-demo-api-limitations")
    @DisplayName("POST /booking documents accepted 256-character names")
    @Story("Document weak boundary validation")
    void createBookingWithLongNamesDocumentsDemoApiBehavior() {
        BookingRequest request = BookingDataFactory.bookingWithLongNames();
        Response response = createTrackedBooking(request);

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode())
                .as("Restful Booker demo currently accepts 256-character names")
                .isEqualTo(200);
            softly.assertThat(response.time())
                .as("POST /booking long-name response time")
                .isLessThan(CONFIG.getResponseTimeSlaMs());
            softly.assertThat(response.jsonPath().getString("booking.firstname"))
                .as("accepted firstname length")
                .hasSize(256);
            softly.assertThat(response.jsonPath().getString("booking.lastname"))
                .as("accepted lastname length")
                .hasSize(256);
        });
        assertJsonContentType(response);
    }

    @Test
    @Tag("regression")
    @DisplayName("POST /booking returns strict JSON content type")
    @Story("Response headers")
    void createBookingReturnsStrictJsonContentType() {
        Response response = createTrackedBooking(BookingDataFactory.validBooking());

        assertJsonContentType(response);
    }

    @Test
    @Tag("security")
    @Tag("negative")
    @DisplayName("POST /booking stores injection-like text as inert data")
    @Story("Input hardening")
    void createBookingWithInjectionLikePayloadIsStoredAsData() {
        BookingRequest request = BookingDataFactory.bookingWithInjectionPayload();
        Response response = createTrackedBooking(request);

        assertThat(response.statusCode())
            .as("POST /booking injection-like payload status")
            .isEqualTo(200);
        assertThat(response.time())
            .as("POST /booking injection-like payload response time")
            .isLessThan(CONFIG.getResponseTimeSlaMs());
        assertJsonContentType(response);
        assertThat(response.jsonPath().getString("booking.firstname"))
            .isEqualTo(request.getFirstname());
        assertThat(response.jsonPath().getString("booking.lastname"))
            .isEqualTo(request.getLastname());
    }
}
