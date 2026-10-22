package com.aria.framework.reporting;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes only the structured, bounded diagnostic snapshot to the configured test log. */
public final class SanitizedDiagnosticLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(SanitizedDiagnosticLoggingFilter.class);

    @Override
    public Response filter(
        FilterableRequestSpecification requestSpec,
        FilterableResponseSpecification responseSpec,
        FilterContext context
    ) {
        try {
            Response response = context.next(requestSpec, responseSpec);
            DiagnosticSnapshot snapshot = DiagnosticSnapshot.forResponse(requestSpec, response);
            if (response.statusCode() >= 400) {
                log.warn("{}", snapshot.render());
            } else {
                log.info("{}", snapshot.render());
            }
            return response;
        } catch (Exception exception) {
            log.warn("{}", DiagnosticSnapshot.forException(requestSpec, exception).render());
            return DiagnosticExceptionRethrower.rethrow(exception);
        }
    }
}
