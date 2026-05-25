package com.aria.framework.github;

import com.aria.framework.base.BaseTest;
import com.aria.framework.data.GithubDataFactory;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aria.framework.assertions.ApiResponseAssertions.assertGithubRateLimitHeaders;
import static com.aria.framework.assertions.ApiResponseAssertions.assertJsonContentType;
import static com.aria.framework.assertions.ApiResponseAssertions.assertResponseTimeWithinConfiguredSla;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests demonstrating GitHub repository search pagination and Link header parsing.
 */
@Epic("GitHub REST API")
@Feature("Pagination")
@Tag("live")
class PaginationTests extends BaseTest {

    private static final Pattern LINK_PATTERN = Pattern.compile("<([^>]+)>; rel=\"([^\"]+)\"");

    /**
     * Validates search pagination query parameters and Link header semantics.
     */
    @Test
    @Tag("regression")
    @DisplayName("GET /search/repositories honors per_page, page, sort, and direction")
    @Story("Search pagination")
    void searchRepositoriesWithPagination() {
        Map<String, Object> params = GithubDataFactory.repositorySearchParams();

        Response response = githubService.searchRepositoriesRaw(params);
        Optional<String> nextLink = extractLink(response.header("Link"), "next");

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getInt("total_count")).isGreaterThanOrEqualTo(response.jsonPath().getList("items").size());
        assertThat(response.jsonPath().getList("items")).hasSizeLessThanOrEqualTo((Integer) params.get("per_page"));
        assertThat(nextLink).isPresent();
        assertThat(nextLink.get()).contains("page=2");
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-search-repositories-schema.json"));
    }

    /**
     * Validates GitHub rate-limit endpoint response shape.
     */
    @Test
    @Tag("smoke")
    @DisplayName("GET /rate_limit returns core rate limit information")
    @Story("Rate limit")
    void getRateLimitReturnsCoreLimit() {
        Response response = githubService.getRateLimitRaw();

        assertThat(response.statusCode()).isEqualTo(200);
        assertJsonContentType(response);
        assertGithubRateLimitHeaders(response);
        assertResponseTimeWithinConfiguredSla(response);
        assertThat(response.jsonPath().getInt("resources.core.limit")).isPositive();
        response.then().body(matchesJsonSchemaInClasspath("schemas/github-rate-limit-schema.json"));
    }

    private Optional<String> extractLink(String linkHeader, String rel) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = LINK_PATTERN.matcher(linkHeader);
        while (matcher.find()) {
            if (rel.equals(matcher.group(2))) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }
}
