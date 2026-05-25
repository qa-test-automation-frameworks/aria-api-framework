package com.aria.framework.models.response;

/**
 * Response payload record for RestfulBooker auth request.
 * Contains only the session token.
 */
public record AuthResponse(String token) {
}
