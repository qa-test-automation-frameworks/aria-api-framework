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
        DiagnosticSnapshot request = DiagnosticSnapshot.forRequest(requestSpec);
        attach("ARIA HTTP request", request.render());
        try {
            Response response = filterContext.next(requestSpec, responseSpec);
            attach("ARIA HTTP response", DiagnosticSnapshot.forResponse(requestSpec, response).render());
            Allure.step(request.label() + " returned HTTP " + response.statusCode());
            return response;
        } catch (Exception exception) {
            attach("ARIA HTTP exception", DiagnosticSnapshot.forException(requestSpec, exception).render());
            return DiagnosticExceptionRethrower.rethrow(exception);
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }

    private static void attach(String name, String diagnostic) {
        Allure.addAttachment(name, "text/plain", diagnostic, ".txt");
    }

}
