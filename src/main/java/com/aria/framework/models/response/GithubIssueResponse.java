package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubIssueResponse(
    Integer id,
    Integer number,
    String title,
    String state,
    String body,
    String url
) {
}
