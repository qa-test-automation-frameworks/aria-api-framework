package com.aria.framework.clients;

import com.aria.framework.utils.RetryDeadline;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/** Apply remaining request time at dispatch, including prebuilt specifications. */
final class DeadlineTimeoutFilter implements Filter {
    private final int timeoutSeconds;

    DeadlineTimeoutFilter(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response, FilterContext context) {
        int remaining = RetryDeadline.requestTimeoutMs(timeoutSeconds);
        request.config(request.getConfig().httpClient(request.getConfig().getHttpClientConfig()
            .setParam("http.connection.timeout", remaining)
            .setParam("http.socket.timeout", remaining)
            .setParam("http.connection-manager.timeout", (long) remaining)));
        try {
            Response result = context.next(request, response);
            // Buffer inside the operation budget; response decoding must not defer a network read to the caller.
            result.asByteArray();
            return result;
        } finally {
            RetryDeadline.closeAttemptClients();
        }
    }
}
