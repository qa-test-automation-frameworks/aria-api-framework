package com.aria.framework.reporting;

import io.qameta.allure.Allure;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Attaches sanitized HTTP diagnostics to Allure whenever a request produces an error response.
 */
public final class FailureDiagnosticFilter implements Filter {

    @Override
    public Response filter(
        FilterableRequestSpecification requestSpec,
        FilterableResponseSpecification responseSpec,
        FilterContext ctx
    ) {
        try {
            Response response = ctx.next(requestSpec, responseSpec);
            if (response.statusCode() >= 400) {
                attachDiagnostic(DiagnosticSnapshot.forResponse(requestSpec, response));
            }
            return response;
        } catch (Exception exception) {
            attachDiagnostic(DiagnosticSnapshot.forException(requestSpec, exception));
            return DiagnosticExceptionRethrower.rethrow(exception);
        }
    }

    private static void attachDiagnostic(DiagnosticSnapshot diagnostic) {
        Allure.addAttachment(
            "ARIA HTTP diagnostic",
            "text/plain",
            diagnostic.render(),
            ".txt"
        );
    }

    /** Conservative fallback for legacy mixed-text diagnostics; structured HTTP paths use snapshots. */
    public static String sanitize(String text) {
        return RedactionPolicy.sanitize(text);
    }

}
