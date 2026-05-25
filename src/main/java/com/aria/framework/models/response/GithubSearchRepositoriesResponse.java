package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubSearchRepositoriesResponse(
    @JsonProperty("total_count") Integer totalCount,
    @JsonProperty("incomplete_results") Boolean incompleteResults,
    List<GithubRepoResponse> items
) {
    public GithubSearchRepositoriesResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
