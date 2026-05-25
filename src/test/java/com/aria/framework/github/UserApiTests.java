package com.aria.framework.github;

import com.aria.framework.base.BaseTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.aria.framework.assertions.ApiResponseAssertions.assertGithubRateLimitHeaders;
import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GitHub user profile endpoints.
 */
@Epic("GitHub REST API")
@Feature("Users")
@Tag("live")
class UserApiTests extends BaseTest {

    /**
     * Validates that an existing GitHub user can be fetched.
     */
    @Test
    @Tag("smoke")
    @DisplayName("GET /users/{username} returns a public GitHub user profile")
    @Story("Get user profile")
    void getPublicUserProfile() {
        Response response = githubService.getUserProfileRaw("octocat");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("login")).isEqualTo("octocat");
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-user-schema.json"));
    }

    /**
     * Validates not-found behavior for a user that should not exist.
     */
    @Test
    @Tag("negative")
    @DisplayName("GET /users/{username} returns 404 for an unknown GitHub user")
    @Story("User not found")
    void getUnknownUserReturnsNotFound() {
        Response response = githubService.getUserProfileRaw("aria-framework-user-that-should-not-exist-404");

        assertThat(response.statusCode()).isEqualTo(404);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getString("message")).containsIgnoringCase("not found");
    }
}
