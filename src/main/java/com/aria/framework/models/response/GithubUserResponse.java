package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response model mapping the GitHub User profile details.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubUserResponse(
    String login,
    Long id,
    String type,
    @JsonProperty("public_repos") Integer publicRepos,
    String name,
    String company,
    String blog,
    String location,
    String email,
    String bio
) {
}
