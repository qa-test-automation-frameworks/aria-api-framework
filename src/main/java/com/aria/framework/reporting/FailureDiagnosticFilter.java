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
                attachDiagnostic(requestSpec, response, null);
            }
            return response;
        } catch (RuntimeException exception) {
            attachDiagnostic(requestSpec, null, exception);
            throw exception;
        }
    }

    private static void attachDiagnostic(
        FilterableRequestSpecification requestSpec,
        Response response,
        RuntimeException exception
    ) {
        StringBuilder diagnostic = new StringBuilder()
            .append("Request\n")
            .append(requestSpec.getMethod())
            .append(' ')
            .append(requestSpec.getURI())
            .append("\n\nHeaders\n")
            .append(requestSpec.getHeaders())
            .append("\n\nBody\n")
            .append(DiagnosticBodyFormatter.bodyAsString(requestSpec.getBody()));

        if (response != null) {
            diagnostic
                .append("\n\nResponse\n")
                .append(response.statusCode())
                .append("\n\nHeaders\n")
                .append(response.getHeaders())
                .append("\n\nBody\n")
                .append(response.getBody().asString());
        }

        if (exception != null) {
            diagnostic
                .append("\n\nException\n")
                .append(exception.getClass().getName())
                .append(": ")
                .append(exception.getMessage());
        }

        Allure.addAttachment(
            "ARIA HTTP diagnostic",
            "text/plain",
            sanitize(diagnostic.toString()),
            ".txt"
        );
    }

    public static String sanitize(String text) {
        return RedactionPolicy.sanitize(text);
    }

}
