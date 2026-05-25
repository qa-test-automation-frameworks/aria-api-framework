package com.aria.framework.models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GithubIssueRequest(
    String title,
    String body,
    String state,
    List<String> assignees,
    List<String> labels,
    @JsonProperty("state_reason") String stateReason
) {
    public GithubIssueRequest {
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
