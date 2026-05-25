package com.aria.framework.exceptions;

/**
 * Raised when authentication or token acquisition fails.
 */
public class AuthenticationException extends ApiException {

    public AuthenticationException(String message) {
        super(message);
    }
}
