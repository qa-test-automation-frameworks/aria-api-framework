package com.aria.framework.clients;

import com.aria.framework.auth.AuthStrategy;
import com.aria.framework.auth.BearerTokenAuthStrategy;
import com.aria.framework.config.ConfigManager;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.utils.RetryUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import com.aria.framework.models.request.GithubIssueRequest;

import java.util.Map;

/**
 * REST Client mapping GitHub REST API endpoints.
 * Handles automatic bearer token authentication.
 */
public class GithubApiClient extends BaseApiClient {

    private final String baseUrl;
    private final AuthStrategy authStrategy;

    public GithubApiClient() {
        this(ConfigManager.defaults().getFrameworkConfig());
    }

    public GithubApiClient(ConfigManager configManager) {
        this(configManager.getFrameworkConfig());
    }

    public GithubApiClient(FrameworkConfig config) {
        super(config);
        this.baseUrl = config.githubBaseUrl();
        this.authStrategy = new BearerTokenAuthStrategy(config::githubToken);
    }

    public GithubApiClient(FrameworkConfig config, AuthStrategy authStrategy) {
        super(config);
        this.baseUrl = config.githubBaseUrl();
        this.authStrategy = authStrategy == null ? AuthStrategy.none() : authStrategy;
    }

    /**
     * Builds and returns a request specification that automatically injects
     * the GitHub personal access token (Bearer Token) if available.
     *
     * @return RequestSpecification authorized spec
     */
    private RequestSpecification getAuthorizedSpec() {
        return authStrategy.apply(getRequestSpec(baseUrl));
    }

    /**
     * GET /users/{username} - Retrieve GitHub user profile details.
     */
    public Response getUserProfile(String username) {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(getAuthorizedSpec())
            .pathParam("username", username)
            .get("/users/{username}"));
    }

    /**
     * GET /repos/{owner}/{repo} - Retrieve repository details.
     */
    public Response getRepoDetails(String owner, String repo) {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(getAuthorizedSpec())
            .pathParam("owner", owner)
            .pathParam("repo", repo)
            .get("/repos/{owner}/{repo}"));
    }

    /**
     * GET /repos/{owner}/{repo}/issues - Retrieve issues in a repository.
     */
    public Response getIssues(String owner, String repo, Map<String, Object> queryParams) {
        RequestSpecification spec = RestAssured.given()
            .spec(getAuthorizedSpec())
            .pathParam("owner", owner)
            .pathParam("repo", repo);

        if (queryParams != null && !queryParams.isEmpty()) {
            spec.queryParams(queryParams);
        }

        return RetryUtils.executeGetWithRetry(config, () -> spec.get("/repos/{owner}/{repo}/issues"));
    }

    /**
     * POST /repos/{owner}/{repo}/issues - Create a new repository issue.
     */
    public Response createIssue(String owner, String repo, GithubIssueRequest issuePayload) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(getAuthorizedSpec())
            .pathParam("owner", owner)
            .pathParam("repo", repo)
            .body(issuePayload)
            .post("/repos/{owner}/{repo}/issues"));
    }

    /**
     * PATCH /repos/{owner}/{repo}/issues/{n} - Update an existing issue.
     */
    public Response updateIssue(String owner, String repo, int issueNumber, GithubIssueRequest issuePayload) {
        return RetryUtils.executeMutationWithRetry(config, "PATCH", true, () -> RestAssured.given()
            .spec(getAuthorizedSpec())
            .pathParam("owner", owner)
            .pathParam("repo", repo)
            .pathParam("n", issueNumber)
            .body(issuePayload)
            .patch("/repos/{owner}/{repo}/issues/{n}"));
    }

    /**
     * GET /search/repositories - Search repositories with query filters and pagination.
     */
    public Response searchRepositories(Map<String, Object> queryParams) {
        RequestSpecification spec = RestAssured.given()
            .spec(getAuthorizedSpec());

        if (queryParams != null && !queryParams.isEmpty()) {
            spec.queryParams(queryParams);
        }

        return RetryUtils.executeGetWithRetry(config, () -> spec.get("/search/repositories"));
    }

    /**
     * GET /rate_limit - Check current rate limit window.
     */
    public Response getRateLimit() {
        return RetryUtils.executeGetWithRetry(config, () -> RestAssured.given()
            .spec(getAuthorizedSpec())
            .get("/rate_limit"));
    }
}
