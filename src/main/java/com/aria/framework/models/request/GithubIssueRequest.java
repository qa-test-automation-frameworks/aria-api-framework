package com.aria.framework.models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Canonical record accessors expose immutable defensive copies."
)
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
