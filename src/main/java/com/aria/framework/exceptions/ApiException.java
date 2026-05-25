package com.aria.framework.exceptions;

/**
 * Base runtime exception for framework-level API client and utility failures.
 */
public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
