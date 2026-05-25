package com.aria.framework.exceptions;

/**
 * Reserved for callers that need to fail explicitly after exhausting rate-limit retries.
 */
public class RateLimitExceededException extends ApiException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
