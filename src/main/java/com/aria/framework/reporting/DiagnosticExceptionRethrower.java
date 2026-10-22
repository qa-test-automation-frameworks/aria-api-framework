package com.aria.framework.reporting;

/** Rethrows a checked transport exception unchanged through RestAssured's unchecked filter API. */
final class DiagnosticExceptionRethrower {

    private DiagnosticExceptionRethrower() {}

    static <R> R rethrow(Exception exception) {
        return DiagnosticExceptionRethrower.<RuntimeException, R>throwUnchecked(exception);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R throwUnchecked(Throwable exception) throws T {
        throw (T) exception;
    }

}
