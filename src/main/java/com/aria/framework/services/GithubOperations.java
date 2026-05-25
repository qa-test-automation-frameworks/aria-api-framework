package com.aria.framework.services;

import com.aria.framework.models.request.GithubIssueRequest;
import com.aria.framework.models.response.GithubIssueResponse;
import com.aria.framework.models.response.GithubRateLimitResponse;
import com.aria.framework.models.response.GithubRepoResponse;
import com.aria.framework.models.response.GithubSearchRepositoriesResponse;
import com.aria.framework.models.response.GithubUserResponse;
import io.restassured.response.Response;

import java.util.Map;

public interface GithubOperations {

    Response getUserProfileRaw(String username);

    GithubUserResponse getUserProfile(String username);

    Response getRepoDetailsRaw(String owner, String repo);

    GithubRepoResponse getRepoDetails(String owner, String repo);

    Response getIssuesRaw(String owner, String repo, Map<String, Object> queryParams);

    Response createIssueRaw(String owner, String repo, GithubIssueRequest issuePayload);

    GithubIssueResponse createIssue(String owner, String repo, GithubIssueRequest issuePayload);

    Response updateIssueRaw(String owner, String repo, int issueNumber, GithubIssueRequest issuePayload);

    GithubIssueResponse updateIssue(String owner, String repo, int issueNumber, GithubIssueRequest issuePayload);

    Response searchRepositoriesRaw(Map<String, Object> queryParams);

    GithubSearchRepositoriesResponse searchRepositories(Map<String, Object> queryParams);

    Response getRateLimitRaw();

    GithubRateLimitResponse getRateLimit();
}
