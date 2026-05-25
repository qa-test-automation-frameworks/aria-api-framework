package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Canonical record accessors expose immutable defensive copies."
)
public record GithubSearchRepositoriesResponse(
    @JsonProperty("total_count") Integer totalCount,
    @JsonProperty("incomplete_results") Boolean incompleteResults,
    List<GithubRepoResponse> items
) {
    public GithubSearchRepositoriesResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
