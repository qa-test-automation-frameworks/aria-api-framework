package com.aria.framework.services;

import com.aria.framework.clients.GithubApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.models.request.GithubIssueRequest;
import com.aria.framework.models.response.GithubIssueResponse;
import com.aria.framework.models.response.GithubRateLimitResponse;
import com.aria.framework.models.response.GithubRepoResponse;
import com.aria.framework.models.response.GithubSearchRepositoriesResponse;
import com.aria.framework.models.response.GithubUserResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.restassured.response.Response;

import java.util.Map;

/**
 * Service Object layer orchestrating GitHub REST API actions.
 * Integrates RetryUtils to handle rate limits transparently.
 */
public class GithubService implements GithubOperations {

    private final GithubApiClient apiClient;

    public GithubService() {
        this(new GithubApiClient());
    }

    public GithubService(FrameworkConfig config) {
        this(new GithubApiClient(config));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Constructor injection intentionally stores a stateless API client adapter.")
    public GithubService(GithubApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Retrieves raw profile details for a given GitHub username with retries on rate limits.
     *
     * @param username String target profile name
     * @return Response raw response
     */
    public Response getUserProfileRaw(String username) {
        return apiClient.getUserProfile(username);
    }

    /**
     * Retrieves parsed GitHub User profile DTO details.
     *
     * @param username String target profile name
     * @return GithubUserResponse DTO model
     */
    public GithubUserResponse getUserProfile(String username) {
        return getUserProfileRaw(username).as(GithubUserResponse.class);
    }

    /**
     * Retrieves repository details raw response.
     */
    public Response getRepoDetailsRaw(String owner, String repo) {
        return apiClient.getRepoDetails(owner, repo);
    }

    public GithubRepoResponse getRepoDetails(String owner, String repo) {
        return getRepoDetailsRaw(owner, repo).as(GithubRepoResponse.class);
    }

    /**
     * Retrieves issues for a given repository raw response.
     */
    public Response getIssuesRaw(String owner, String repo, Map<String, Object> queryParams) {
        return apiClient.getIssues(owner, repo, queryParams);
    }

    /**
     * Creates a new issue in a repository raw response.
     */
    public Response createIssueRaw(String owner, String repo, GithubIssueRequest issuePayload) {
        return apiClient.createIssue(owner, repo, issuePayload);
    }

    public GithubIssueResponse createIssue(String owner, String repo, GithubIssueRequest issuePayload) {
        return createIssueRaw(owner, repo, issuePayload).as(GithubIssueResponse.class);
    }

    /**
     * Updates an existing issue in a repository raw response.
     */
    public Response updateIssueRaw(String owner, String repo, int issueNumber, GithubIssueRequest issuePayload) {
        return apiClient.updateIssue(owner, repo, issueNumber, issuePayload);
    }

    public GithubIssueResponse updateIssue(
        String owner,
        String repo,
        int issueNumber,
        GithubIssueRequest issuePayload
    ) {
        return updateIssueRaw(owner, repo, issueNumber, issuePayload).as(GithubIssueResponse.class);
    }

    /**
     * Performs a repository search raw response.
     */
    public Response searchRepositoriesRaw(Map<String, Object> queryParams) {
        return apiClient.searchRepositories(queryParams);
    }

    public GithubSearchRepositoriesResponse searchRepositories(Map<String, Object> queryParams) {
        return searchRepositoriesRaw(queryParams).as(GithubSearchRepositoriesResponse.class);
    }

    /**
     * Checks current rate limits raw response.
     */
    public Response getRateLimitRaw() {
        return apiClient.getRateLimit();
    }

    public GithubRateLimitResponse getRateLimit() {
        return getRateLimitRaw().as(GithubRateLimitResponse.class);
    }
}
