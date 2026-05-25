package com.aria.framework.contracts;

import com.aria.framework.builders.BookingPayloadBuilder;
import com.aria.framework.builders.GithubIssueBuilder;
import com.aria.framework.clients.BookingApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.fixtures.OwnedApiProvider;
import com.aria.framework.models.request.AuthRequest;
import com.aria.framework.models.request.BookingRequest;
import com.aria.framework.models.request.GithubIssueRequest;
import com.aria.framework.services.AuthService;
import com.aria.framework.services.BookingService;
import com.aria.framework.services.GithubService;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
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
import static com.aria.framework.assertions.ApiResponseAssertions.assertAllowHeaderContains;
import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Contracts")
@Feature("Default CI deterministic endpoint coverage")
@Tag("contract")
@Execution(ExecutionMode.SAME_THREAD)
class DeterministicApiCoverageTest {

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
    @DisplayName("POST /auth is covered by deterministic owned provider")
    void authTokenIssuedByOwnedProvider() {
        Response response = new AuthService(provider.frameworkConfig())
            .authenticateRaw(AuthRequest.builder().username("admin").password("mock-booker-password").build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/auth-token-schema.json"));
        assertThat(response.jsonPath().getString("token")).isEqualTo("mock-token");
    }

    @Test
    @DisplayName("GET /ping is covered by deterministic owned provider")
    void pingCoveredByOwnedProvider() {
        Response response = new BookingService(provider.frameworkConfig()).pingRaw();

        log("Owned provider GET /ping returned {}", response.statusCode());
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body().asString()).isEqualTo("Created");
    }

    @Test
    @DisplayName("GET /booking is covered by deterministic owned provider")
    void bookingIdsCoveredByOwnedProvider() {
        Response response = new BookingService(provider.frameworkConfig()).getAllBookingIdsRaw();

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-id-list-schema.json"));
        assertThat(response.jsonPath().getInt("[0].bookingid")).isEqualTo(1);
    }

    @Test
    @DisplayName("OPTIONS /booking is covered by deterministic owned provider")
    void bookingOptionsCoveredByOwnedProvider() {
        Response response = new BookingService(provider.frameworkConfig()).optionsBookingRaw();

        assertThat(response.statusCode()).isEqualTo(204);
        assertAllowHeaderContains(response, "GET", "POST", "OPTIONS");
        assertThat(response.header("Access-Control-Allow-Methods")).contains("GET", "POST", "OPTIONS");
        assertThat(response.header("Access-Control-Allow-Origin")).isEqualTo("*");
    }

    @Test
    @DisplayName("POST /booking is covered by deterministic owned provider")
    void createBookingCoveredByOwnedProvider() {
        Response response = new BookingService(provider.frameworkConfig()).createBookingRaw(booking());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
        assertThat(response.jsonPath().getInt("bookingid")).isGreaterThan(1);
    }

    @Test
    @DisplayName("GET /booking/{id} is covered by deterministic owned provider")
    void getBookingCoveredByOwnedProvider() {
        Response response = new BookingService(provider.frameworkConfig()).getBookingRaw(1);

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
        assertThat(response.jsonPath().getString("firstname")).isEqualTo("Jim");
    }

    @Test
    @DisplayName("PUT /booking/{id} is covered by deterministic owned provider")
    void updateBookingCoveredByOwnedProvider() {
        Response response = bookingClient(provider.frameworkConfig()).updateBooking(1, booking(), "mock-token");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
        assertThat(response.jsonPath().getString("lastname")).isEqualTo("Brown");
    }

    @Test
    @DisplayName("PUT /booking/{id} is idempotent against deterministic owned provider")
    void putBookingIsIdempotentAgainstOwnedProvider() {
        BookingApiClient client = bookingClient(provider.frameworkConfig());
        Response firstUpdate = client.updateBooking(1, booking(), "mock-token");
        Response firstRead = client.getBooking(1);
        Response secondUpdate = client.updateBooking(1, booking(), "mock-token");
        Response secondRead = client.getBooking(1);

        log(
            "Owned provider idempotency statuses: firstUpdate={}, secondUpdate={}",
            firstUpdate.statusCode(),
            secondUpdate.statusCode()
        );
        assertThat(firstUpdate.statusCode()).isEqualTo(200);
        assertThat(secondUpdate.statusCode()).isEqualTo(200);
        assertThat(firstRead.body().asString()).isEqualTo(secondRead.body().asString());
        assertThat(secondRead.jsonPath().getString("lastname")).isEqualTo("Brown");
    }

    @Test
    @DisplayName("PATCH /booking/{id} is covered by deterministic owned provider")
    void partialUpdateBookingCoveredByOwnedProvider() {
        Response response = bookingClient(provider.frameworkConfig())
            .partialUpdateBooking(1, Map.of("firstname", "Patch"), "mock-token");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/booking-schema.json"));
        assertThat(response.jsonPath().getString("firstname")).isEqualTo("Patch");
    }

    @Test
    @DisplayName("DELETE /booking/{id} is covered by deterministic owned provider")
    void deleteBookingCoveredByOwnedProvider() {
        Response response = bookingClient(provider.frameworkConfig()).deleteBooking(1, "mock-token");

        log("Owned provider DELETE /booking/1 returned {}", response.statusCode());
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body().asString()).isEqualTo("Created");
    }

    @Test
    @DisplayName("GET /users/{username} is covered by deterministic owned provider")
    void githubUserCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig()).getUserProfileRaw("octocat");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-user-schema.json"));
        assertThat(response.jsonPath().getString("login")).isEqualTo("octocat");
    }

    @Test
    @DisplayName("GET /repos/{owner}/{repo} is covered by deterministic owned provider")
    void githubRepoCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig()).getRepoDetailsRaw("octocat", "Hello-World");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-repo-schema.json"));
        assertThat(response.jsonPath().getString("name")).isEqualTo("Hello-World");
    }

    @Test
    @DisplayName("GET /repos/{owner}/{repo}/issues is covered by deterministic owned provider")
    void githubIssuesCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig()).getIssuesRaw("octocat", "Hello-World", Map.of());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-issues-schema.json"));
        assertThat(response.jsonPath().getInt("[0].number")).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /repos/{owner}/{repo}/issues is covered by deterministic owned provider")
    void githubCreateIssueCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig())
            .createIssueRaw("octocat", "Hello-World", issue());

        assertThat(response.statusCode()).isEqualTo(201);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-issue-schema.json"));
        assertThat(response.jsonPath().getString("state")).isEqualTo("open");
    }

    @Test
    @DisplayName("PATCH /repos/{owner}/{repo}/issues/{issue_number} is covered by deterministic owned provider")
    void githubUpdateIssueCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig())
            .updateIssueRaw("octocat", "Hello-World", 1, closeIssue());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-issue-schema.json"));
        assertThat(response.jsonPath().getString("state")).isEqualTo("closed");
    }

    @Test
    @DisplayName("GET /search/repositories is covered by deterministic owned provider")
    void githubSearchCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig())
            .searchRepositoriesRaw(Map.of("q", "language:java"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-search-repositories-schema.json"));
        assertThat(response.jsonPath().getInt("total_count")).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /rate_limit is covered by deterministic owned provider")
    void githubRateLimitCoveredByOwnedProvider() {
        Response response = new GithubService(provider.frameworkConfig()).getRateLimitRaw();

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-rate-limit-schema.json"));
        assertThat(response.jsonPath().getInt("resources.core.limit")).isEqualTo(60);
    }

    private static BookingApiClient bookingClient(FrameworkConfig config) {
        return new BookingApiClient(config);
    }

    private static BookingRequest booking() {
        return new BookingPayloadBuilder()
            .withFirstname("Jim")
            .withLastname("Brown")
            .withTotalprice(111)
            .withDepositpaid(true)
            .withAdditionalneeds("Breakfast")
            .build();
    }

    private static GithubIssueRequest issue() {
        return new GithubIssueBuilder()
            .withTitle("Mock issue")
            .withBody("body")
            .addLabel("automation")
            .build();
    }

    private static GithubIssueRequest closeIssue() {
        return new GithubIssueBuilder()
            .withState("closed")
            .build();
    }
}
