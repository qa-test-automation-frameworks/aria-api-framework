package com.aria.framework.exceptions;

/** The operation budget expired or cannot cover another required retry delay. */
public final class RetryDeadlineExceededException extends ApiException {
    public RetryDeadlineExceededException(String message) {
        super(message);
    }
}
