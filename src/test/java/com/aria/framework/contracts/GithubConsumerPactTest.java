package com.aria.framework.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactBuilder;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static com.aria.framework.reporting.AllureTestDiagnostics.log;
import static com.aria.framework.reporting.AllureTestDiagnostics.step;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consumer contract tests for the GitHub users API.
 */
@Epic("Contracts")
@Feature("GitHub Pact")
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
class GithubConsumerPactTest {

    /**
     * Defines a contract for fetching a GitHub user.
     *
     * @param builder Pact V4 DSL builder
     * @return request-response pact
     */
    @Pact(consumer = "aria-api-framework", provider = "github-api")
    V4Pact getUserPact(PactBuilder builder) {
        return builder
            .given("user octocat exists")
            .expectsToReceiveHttpInteraction("a request for octocat", interaction -> interaction
                .withRequest(request -> request
                    .method("GET")
                    .path("/users/octocat"))
                .willRespondWith(response -> response
                    .status(200)
                    .header("Content-Type", "application/json")
                    .body("""
                        {
                          "login": "octocat",
                          "id": 583231,
                          "type": "User",
                          "public_repos": 8
                        }
                        """, "application/json")))
            .toPact();
    }

    /**
     * Verifies the GitHub user consumer expectation against the Pact mock server.
     *
     * @param mockServer Pact mock server
     */
    @Test
    @Tag("contract")
    @DisplayName("GET /users/{username} satisfies the GitHub consumer pact")
    @Story("GitHub user consumer contract")
    @PactTestFor(pactMethod = "getUserPact")
    void getUserMatchesConsumerContract(MockServer mockServer) {
        Response response = step("Call Pact mock server GET /users/octocat", () -> RestAssured.given()
            .baseUri(mockServer.getUrl())
            .accept("application/json")
            .get("/users/octocat"));
        log("Pact GET /users/octocat returned {}", response.statusCode());

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("login")).isEqualTo("octocat");
    }
}
