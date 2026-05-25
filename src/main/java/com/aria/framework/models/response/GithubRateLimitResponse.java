package com.aria.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRateLimitResponse(Resources resources) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Resources(RateLimit core, RateLimit search) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RateLimit(Integer limit, Integer remaining, Long reset, Integer used) {
    }
}
