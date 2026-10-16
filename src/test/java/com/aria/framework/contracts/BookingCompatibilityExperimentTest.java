package com.aria.framework.contracts;

import com.aria.framework.clients.BookingApiClient;
import com.aria.framework.fixtures.OwnedApiProvider;
import com.aria.framework.models.response.BookingResponse;
import com.aria.framework.utils.JsonUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Demonstrates consumer compatibility against independently authored response expectations.
 */
@Epic("Contracts")
@Feature("Booking compatibility experiment")
@Tag("contract")
class BookingCompatibilityExperimentTest {

    @Test
    @DisplayName("Consumer accepts an additive provider response field")
    @Story("Backward-compatible provider evolution")
    void acceptsCompatibleAdditiveProviderResponse() {
        try (OwnedApiProvider provider = OwnedApiProvider.start(
            OwnedApiProvider.BookingContractVariant.COMPATIBLE_ADDITIVE
        )) {
            Response response = new BookingApiClient(provider.frameworkConfig()).getBooking(1);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getString("contractrevision")).isEqualTo("fixture-v2-additive");
            response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));

            BookingResponse consumerView = JsonUtils.deserialize(response.asString(), BookingResponse.class);
            assertThat(consumerView.firstname()).isEqualTo("Jim");
            assertThat(consumerView.bookingdates().checkin()).isEqualTo("2026-05-24");
        }
    }

    @Test
    @DisplayName("Consumer rejects a provider response missing a required contract field")
    @Story("Breaking provider evolution")
    void rejectsBreakingRequiredFieldRemoval() {
        try (OwnedApiProvider provider = OwnedApiProvider.start(
            OwnedApiProvider.BookingContractVariant.BREAKING_REQUIRED_FIELD
        )) {
            Response response = new BookingApiClient(provider.frameworkConfig()).getBooking(1);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThatThrownBy(() -> response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("bookingdates");
        }
    }
}
