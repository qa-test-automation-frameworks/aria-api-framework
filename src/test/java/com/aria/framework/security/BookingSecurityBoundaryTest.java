package com.aria.framework.security;

import com.aria.framework.fixtures.OwnedApiProvider;
import com.aria.framework.services.BookingSecurityService;
import com.aria.framework.services.BookingService;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic OWASP API Top 10 security-boundary tests against the owned API provider fixture.
 */
@Epic("Security")
@Feature("Booking API security boundaries")
@Execution(ExecutionMode.SAME_THREAD)
class BookingSecurityBoundaryTest {

    private static OwnedApiProvider provider;

    @BeforeAll
    static void startOwnedProvider() {
        provider = OwnedApiProvider.start();
    }

    @BeforeEach
    void resetOwnedProvider() {
        provider.resetState();
    }

    @AfterAll
    static void stopOwnedProvider() {
        provider.close();
    }

    @Test
    @Tag("security")
    @DisplayName("POST /booking rejects mass-assignment fields")
    @Story("OWASP API3 mass assignment")
    void bookingCreateRejectsMassAssignmentFields() {
        Response response = new BookingSecurityService(provider.frameworkConfig())
            .createBookingPayload(Map.of(
                "firstname", "Jim",
                "lastname", "Brown",
                "role", "admin"
            ));

        assertThat(response.statusCode()).isEqualTo(400);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/error-schema.json"));
        assertThat(response.jsonPath().getString("error")).contains("mass assignment");
    }

    @Test
    @Tag("security")
    @DisplayName("GET /booking/{id} rejects cross-user access")
    @Story("OWASP API1 BOLA/IDOR")
    void bookingReadRejectsCrossUserAccess() {
        Response response = new BookingSecurityService(provider.frameworkConfig())
            .getBookingAsUser(1, "user-2");

        assertThat(response.statusCode()).isEqualTo(403);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/error-schema.json"));
        assertThat(response.jsonPath().getString("error")).contains("forbidden");
    }

    @Test
    @Tag("security")
    @DisplayName("PUT /booking/{id} rejects expired tokens")
    @Story("Expired token boundary")
    void bookingUpdateRejectsExpiredToken() {
        Response response = new BookingSecurityService(provider.frameworkConfig())
            .updateBookingWithToken(1, Map.of("firstname", "Jim"), "expired-token");

        assertThat(response.statusCode()).isEqualTo(403);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/error-schema.json"));
        assertThat(response.jsonPath().getString("error")).contains("expired");
    }

    @Test
    @Tag("security")
    @DisplayName("PUT /booking/{id} rejects wrong-role tokens")
    @Story("Authorization role boundary")
    void bookingUpdateRejectsWrongRoleToken() {
        Response response = new BookingSecurityService(provider.frameworkConfig())
            .updateBookingWithHeader(1, Map.of("firstname", "Jim"), "X-Role", "viewer");

        assertThat(response.statusCode()).isEqualTo(403);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/error-schema.json"));
        assertThat(response.jsonPath().getString("error")).contains("role");
    }

    @Test
    @Tag("security")
    @DisplayName("GET /booking/{id} does not expose internal fields")
    @Story("OWASP API3 excessive data exposure")
    void bookingReadDoesNotExposeInternalFields() {
        Response response = new BookingService(provider.frameworkConfig()).getBookingRaw(1);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
        assertThat(response.jsonPath().getMap("$"))
            .doesNotContainKeys("internalToken", "adminNotes", "password", "ownerEmail");
    }
}
