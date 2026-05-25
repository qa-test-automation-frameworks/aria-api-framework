package com.aria.framework.reporting;

import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Allure RestAssured filter that records every HTTP exchange after applying ARIA redaction rules.
 */
public final class SanitizedAllureRestAssured extends AllureRestAssured {

    @Override
    public Response filter(
        FilterableRequestSpecification requestSpec,
        FilterableResponseSpecification responseSpec,
        FilterContext filterContext
    ) {
        return Allure.step(stepName(requestSpec), () -> {
            attach("ARIA HTTP request", requestDiagnostic(requestSpec));
            Response response = filterContext.next(requestSpec, responseSpec);
            attach("ARIA HTTP response", responseDiagnostic(response));
            return response;
        });
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }

    private static void attach(String name, String diagnostic) {
        Allure.addAttachment(name, "text/plain", RedactionPolicy.sanitize(diagnostic), ".txt");
    }

    private static String stepName(FilterableRequestSpecification requestSpec) {
        return RedactionPolicy.sanitize("HTTP " + requestSpec.getMethod() + " " + requestSpec.getURI());
    }

    private static String requestDiagnostic(FilterableRequestSpecification requestSpec) {
        return new StringBuilder()
            .append(requestSpec.getMethod())
            .append(' ')
            .append(requestSpec.getURI())
            .append("\n\nHeaders\n")
            .append(requestSpec.getHeaders())
            .append("\n\nCookies\n")
            .append(requestSpec.getCookies())
            .append("\n\nBody\n")
            .append(DiagnosticBodyFormatter.bodyAsString(requestSpec.getBody()))
            .toString();
    }

    private static String responseDiagnostic(Response response) {
        return new StringBuilder()
            .append(response.statusLine())
            .append("\n\nHeaders\n")
            .append(response.getHeaders())
            .append("\n\nBody\n")
            .append(response.getBody().asString())
            .toString();
    }

}
