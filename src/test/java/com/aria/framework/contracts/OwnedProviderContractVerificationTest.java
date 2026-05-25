package com.aria.framework.contracts;

import com.aria.framework.builders.BookingPayloadBuilder;
import com.aria.framework.clients.BookingApiClient;
import com.aria.framework.fixtures.OwnedApiProvider;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider-state style verification for the owned provider fixture used by deterministic contract tests.
 */
@Epic("Contracts")
@Feature("Owned provider verification")
@Tag("contract")
@Execution(ExecutionMode.SAME_THREAD)
class OwnedProviderContractVerificationTest {

    @Test
    @DisplayName("Owned provider satisfies core Restful Booker consumer states")
    @Story("Provider state contract verification")
    void ownedProviderSatisfiesRestfulBookerConsumerStates() {
        try (OwnedApiProvider provider = OwnedApiProvider.start()) {
            BookingApiClient client = new BookingApiClient(provider.frameworkConfig());

            Response existingBooking = client.getBooking(1);
            assertThat(existingBooking.statusCode()).isEqualTo(200);
            assertJsonContentType(existingBooking);
            assertThat(existingBooking.jsonPath().getString("firstname")).isEqualTo("Jim");

            Response createdBooking = client.createBooking(new BookingPayloadBuilder()
                .withFirstname("Jim")
                .withLastname("Brown")
                .build());
            assertThat(createdBooking.statusCode()).isEqualTo(200);
            assertJsonContentType(createdBooking);
            assertThat(createdBooking.jsonPath().getInt("bookingid")).isGreaterThan(1);

            Response updatedBooking = client.updateBooking(1, new BookingPayloadBuilder()
                .withFirstname("Jim")
                .withLastname("Brown")
                .build(), "mock-token");
            assertThat(updatedBooking.statusCode()).isEqualTo(200);
            assertJsonContentType(updatedBooking);
            assertThat(updatedBooking.jsonPath().getString("lastname")).isEqualTo("Brown");

            Response deletedBooking = client.deleteBooking(1, "mock-token");
            assertThat(deletedBooking.statusCode()).isEqualTo(201);
            assertThat(deletedBooking.body().asString()).isEqualTo("Created");

            Response missingBooking = client.getBooking(999);
            assertThat(missingBooking.statusCode()).isEqualTo(404);
            assertThat(missingBooking.body().asString()).isEqualTo("Not Found");
        }
    }
}
