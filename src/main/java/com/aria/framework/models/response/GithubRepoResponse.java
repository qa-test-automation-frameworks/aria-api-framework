package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepoResponse(
    Long id,
    String name,
    @JsonProperty("full_name") String fullName,
    Boolean fork,
    @JsonProperty("private") Boolean privateRepository,
    String visibility,
    @JsonProperty("default_branch") String defaultBranch
) {
}
