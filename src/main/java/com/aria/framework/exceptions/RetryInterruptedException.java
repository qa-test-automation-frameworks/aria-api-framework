package com.aria.framework.exceptions;

/**
 * Raised when a retry backoff sleep is interrupted.
 */
public class RetryInterruptedException extends ApiException {

    public RetryInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
