package com.aria.framework.github;

import com.aria.framework.base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.aria.framework.assertions.ApiResponseAssertions.assertGithubRateLimitHeaders;
import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GitHub repository endpoints.
 */
@Epic("GitHub REST API")
@Feature("Repositories")
@Tag("live")
class RepoApiTests extends BaseTest {

    /**
     * Validates repository metadata for a public repository.
     */
    @Test
    @Tag("smoke")
    @DisplayName("GET /repos/{owner}/{repo} returns public repository details")
    @Story("Get repository")
    void getPublicRepositoryDetails() {
        Response response = githubService.getRepoDetailsRaw("octocat", "Hello-World");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("full_name")).isEqualTo("octocat/Hello-World");
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-repo-schema.json"));
    }

    /**
     * Validates listing repository issues with query parameters.
     */
    @Test
    @Tag("regression")
    @DisplayName("GET /repos/{owner}/{repo}/issues lists repository issues")
    @Story("List issues")
    void listRepositoryIssues() {
        Response response = githubService.getIssuesRaw("octocat", "Hello-World", Map.of("state", "all", "per_page", 5));

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getList("$")).hasSizeLessThanOrEqualTo(5);
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-issues-schema.json"));
    }
}
