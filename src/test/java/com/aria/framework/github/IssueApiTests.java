package com.aria.framework.github;

import com.aria.framework.base.BaseTest;
import com.aria.framework.data.GithubDataFactory;
import com.aria.framework.models.request.GithubIssueRequest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.aria.framework.assertions.ApiResponseAssertions.assertGithubRateLimitHeaders;
import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GitHub issue write endpoints. Write tests are skipped unless a token and target
 * repository are supplied through environment variables or system properties.
 */
@Epic("GitHub REST API")
@Feature("Issues")
@Tag("live")
class IssueApiTests extends BaseTest {

    /**
     * Validates issue creation and update against a caller-provided repository.
     */
    @Test
    @Tag("regression")
    @DisplayName("POST and PATCH /repos/{owner}/{repo}/issues create and update an issue")
    @Story("Create and update issue")
    void createAndUpdateIssueWhenTokenIsAvailable() {
        String token = CONFIG.getGithubToken();
        String owner = System.getProperty("github.owner", System.getenv("GITHUB_OWNER"));
        String repo = System.getProperty("github.repo", System.getenv("GITHUB_REPO"));
        Assumptions.assumeTrue(token != null && !token.isBlank(), "GitHub token is required for issue write tests");
        Assumptions.assumeTrue(owner != null && !owner.isBlank(), "GitHub owner is required for issue write tests");
        Assumptions.assumeTrue(repo != null && !repo.isBlank(), "GitHub repo is required for issue write tests");
        owner = Objects.requireNonNull(owner);
        repo = Objects.requireNonNull(repo);
        Assumptions.assumeTrue(repo.toLowerCase().contains("aria") && repo.toLowerCase().contains("test"),
            "GitHub issue write tests require a disposable ARIA test repository");

        GithubIssueRequest payload = GithubDataFactory.issuePayload();
        Response createResponse = githubService.createIssueRaw(owner, repo, payload);
        int issueNumber = -1;
        try {
            assertThat(createResponse.statusCode()).isEqualTo(201);
            assertJsonContentType(createResponse);
            assertGithubRateLimitHeaders(createResponse);
            assertResponseTimeWithinConfiguredSla(createResponse);
            assertThat(createResponse.jsonPath().getString("title")).startsWith("ARIA automated issue");
            createResponse.then().body(matchesJsonSchemaInClasspath("schemas/github-issue-schema.json"));
            issueNumber = createResponse.jsonPath().getInt("number");

            Response updateResponse = githubService.updateIssueRaw(owner, repo, issueNumber, GithubDataFactory.closeIssuePayload());
            assertThat(updateResponse.statusCode()).isEqualTo(200);
            assertJsonContentType(updateResponse);
            assertGithubRateLimitHeaders(updateResponse);
            assertResponseTimeWithinConfiguredSla(updateResponse);
            assertThat(updateResponse.jsonPath().getString("state")).isEqualTo("closed");
            updateResponse.then().body(matchesJsonSchemaInClasspath("schemas/github-issue-schema.json"));
        } finally {
            if (issueNumber > 0) {
                Response cleanupResponse = githubService.updateIssueRaw(owner, repo, issueNumber, GithubDataFactory.closeIssuePayload());
                assertThat(cleanupResponse.statusCode())
                    .as("cleanup must close the disposable GitHub issue")
                    .isEqualTo(200);
            }
        }
    }

    /**
     * Validates unauthenticated issue creation is rejected by GitHub.
     */
    @Test
    @Tag("negative")
    @DisplayName("POST /repos/{owner}/{repo}/issues returns 401 when token is absent")
    @Story("Reject unauthenticated issue create")
    void createIssueWithoutTokenReturnsUnauthorized() {
        Assumptions.assumeTrue(CONFIG.getGithubToken() == null || CONFIG.getGithubToken().isBlank(),
            "This negative test requires no GitHub token");

        Response response = githubService.createIssueRaw("octocat", "Hello-World", GithubDataFactory.issuePayload());

        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("message")).containsIgnoringCase("requires authentication");
    }
}
