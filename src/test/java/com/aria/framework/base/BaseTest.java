package com.aria.framework.base;

import com.aria.framework.config.ConfigManager;
import com.aria.framework.reporting.AllureTestDiagnostics;
import com.aria.framework.services.AuthService;
import com.aria.framework.services.BookingSecurityService;
import com.aria.framework.services.BookingService;
import com.aria.framework.services.GithubService;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aria.framework.models.request.BookingRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for API tests that initializes shared configuration, RestAssured defaults,
 * and reusable service objects.
 */
public abstract class BaseTest {

    private static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    protected final ConfigManager CONFIG;
    protected final AuthService authService;
    protected final BookingService bookingService;
    protected final BookingSecurityService bookingSecurityService;
    protected final GithubService githubService;

    private final List<Integer> createdBookingIds = new ArrayList<>();

    protected BaseTest() {
        this(ConfigManager.defaults());
    }

    protected BaseTest(ConfigManager config) {
        this.CONFIG = config;
        this.authService = new AuthService(config.getFrameworkConfig());
        this.bookingService = new BookingService(config.getFrameworkConfig());
        this.bookingSecurityService = new BookingSecurityService(config.getFrameworkConfig());
        this.githubService = new GithubService(config.getFrameworkConfig());
    }

    /**
     * Validates shared configuration before each live test method.
     */
    @BeforeEach
    @Step("Initialize ARIA framework services")
    void initializeFramework() {
        AllureTestDiagnostics.step("Initialize ARIA framework services", CONFIG::validate);
    }

    /**
     * Deletes bookings created through createTrackedBooking after each test method.
     */
    @AfterEach
    @Step("Reset ARIA framework test state")
    void cleanupFramework() {
        AllureTestDiagnostics.step("Reset ARIA framework test state", () -> {
            AllureTestDiagnostics.log("Tracked bookings pending cleanup: {}", createdBookingIds.size());
            Collections.reverse(createdBookingIds);
            RuntimeException cleanupFailure = null;
            for (Integer bookingId : createdBookingIds) {
                try {
                    Response response = bookingService.deleteBookingRaw(bookingId);
                    int statusCode = response.statusCode();
                    AllureTestDiagnostics.log("Cleanup DELETE /booking/{} returned {}", bookingId, statusCode);
                    if (statusCode != 201 && statusCode != 404) {
                        throw new IllegalStateException("Unexpected cleanup status " + statusCode
                            + " for booking id " + bookingId);
                    }
                } catch (RuntimeException exception) {
                    log.warn("Failed to cleanup booking id {}", bookingId, exception);
                    AllureTestDiagnostics.log("Cleanup failed for booking id {}: {}", bookingId, exception.getMessage());
                    if (cleanupFailure == null) {
                        cleanupFailure = new IllegalStateException("One or more booking cleanup operations failed");
                    }
                    cleanupFailure.addSuppressed(exception);
                }
            }
            createdBookingIds.clear();
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
        });
    }

    protected Response createTrackedBooking(BookingRequest request) {
        return AllureTestDiagnostics.step("Create tracked Restful Booker booking", () -> {
            Response response = bookingService.createBookingRaw(request);
            registerCreatedBookingIfPresent(response);
            AllureTestDiagnostics.log("POST /booking returned status {} in {} ms", response.statusCode(), response.time());
            return response;
        });
    }

    protected void unregisterCreatedBooking(int bookingId) {
        AllureTestDiagnostics.log("Unregister tracked booking id {}", bookingId);
        createdBookingIds.remove(Integer.valueOf(bookingId));
    }

    private void registerCreatedBookingIfPresent(Response response) {
        if (response.statusCode() != 200) {
            return;
        }
        String bookingId = response.jsonPath().getString("bookingid");
        if (bookingId != null && !bookingId.isBlank()) {
            createdBookingIds.add(Integer.parseInt(bookingId));
            AllureTestDiagnostics.log("Registered booking id {} for cleanup", bookingId);
        }
    }
}
