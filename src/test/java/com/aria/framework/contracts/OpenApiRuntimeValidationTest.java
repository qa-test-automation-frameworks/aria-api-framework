package com.aria.framework.contracts;

import com.aria.framework.assertions.OpenApiResponseValidator;
import com.aria.framework.builders.BookingPayloadBuilder;
import com.aria.framework.builders.GithubIssueBuilder;
import com.aria.framework.clients.BookingApiClient;
import com.aria.framework.fixtures.OwnedApiProvider;
import com.aria.framework.models.request.AuthRequest;
import com.aria.framework.services.AuthService;
import com.aria.framework.services.BookingService;
import com.aria.framework.services.GithubService;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Tag("contract")
class OpenApiRuntimeValidationTest {

    private static final OpenApiResponseValidator BOOKER_SPEC =
        OpenApiResponseValidator.fromClasspath("openapi/restful-booker-openapi.yaml");
    private static final OpenApiResponseValidator GITHUB_SPEC =
        OpenApiResponseValidator.fromClasspath("openapi/github-subset-openapi.yaml");

    @Test
    @DisplayName("Owned provider Restful Booker responses match documented OpenAPI operations")
    void restfulBookerResponsesMatchOpenApiOperations() {
        try (OwnedApiProvider provider = OwnedApiProvider.start()) {
            BookingService service = new BookingService(provider.frameworkConfig());
            BookingApiClient client = new BookingApiClient(provider.frameworkConfig());

            assertBooker("post", "/auth", new AuthService(provider.frameworkConfig())
                .authenticateRaw(AuthRequest.builder().username("admin").password("mock-booker-password").build()));
            assertBooker("get", "/ping", service.pingRaw());
            assertBooker("get", "/booking", service.getAllBookingIdsRaw());
            assertBooker("options", "/booking", service.optionsBookingRaw());
            assertBooker("post", "/booking", service.createBookingRaw(booking()));
            assertBooker("get", "/booking/{id}", service.getBookingRaw(1));
            assertBooker("put", "/booking/{id}", client.updateBooking(1, booking(), "mock-token"));
            assertBooker("patch", "/booking/{id}", client.partialUpdateBooking(1, Map.of("firstname", "Patch"), "mock-token"));
            assertBooker("delete", "/booking/{id}", client.deleteBooking(1, "mock-token"));
        }
    }

    @Test
    @DisplayName("Owned provider GitHub responses match documented OpenAPI operations")
    void githubResponsesMatchOpenApiOperations() {
        try (OwnedApiProvider provider = OwnedApiProvider.start()) {
            GithubService service = new GithubService(provider.frameworkConfig());

            assertGithub("get", "/users/{username}", service.getUserProfileRaw("octocat"));
            assertGithub("get", "/repos/{owner}/{repo}", service.getRepoDetailsRaw("octocat", "Hello-World"));
            assertGithub("get", "/repos/{owner}/{repo}/issues", service.getIssuesRaw("octocat", "Hello-World", Map.of()));
            assertGithub("post", "/repos/{owner}/{repo}/issues", service.createIssueRaw("octocat", "Hello-World",
                new GithubIssueBuilder().withTitle("Mock issue").withBody("body").build()));
            assertGithub("patch", "/repos/{owner}/{repo}/issues/{issue_number}", service.updateIssueRaw(
                "octocat",
                "Hello-World",
                1,
                new GithubIssueBuilder().withState("closed").build()
            ));
            assertGithub("get", "/search/repositories", service.searchRepositoriesRaw(Map.of("q", "language:java")));
            assertGithub("get", "/rate_limit", service.getRateLimitRaw());
        }
    }

    private static void assertBooker(String method, String path, Response response) {
        BOOKER_SPEC.assertResponse(method, path, response);
    }

    private static void assertGithub(String method, String path, Response response) {
        GITHUB_SPEC.assertResponse(method, path, response);
    }

    private static com.aria.framework.models.request.BookingRequest booking() {
        return new BookingPayloadBuilder()
            .withFirstname("Jim")
            .withLastname("Brown")
            .withTotalprice(111)
            .withDepositpaid(true)
            .withAdditionalneeds("Breakfast")
            .build();
    }
}
