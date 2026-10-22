package com.aria.framework.contracts;

import com.aria.framework.assertions.OpenApiResponseValidator;
import io.restassured.builder.ResponseBuilder;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiResponseValidatorFixtureTest {

    private static final OpenApiResponseValidator VALIDATOR =
        OpenApiResponseValidator.fromClasspath("fixtures/required-response-contract.yaml");

    @Test
    void acceptsResponseReferenceAndNormalizedJsonContentType() {
        assertValid("post", "/auth", response(200, "application/json; charset=UTF-8", "{\"token\":\"safe-token\"}"));
    }

    @Test
    void acceptsDocumentedErrorResponseShape() {
        assertValid("post", "/auth", response(400, "application/json", "{\"error\":\"denied\"}"));
    }

    @Test
    void acceptsDocumentedPlainTextAndBodylessResponses() {
        assertValid("get", "/ping", response(201, "TEXT/PLAIN; charset=utf-8", "Created"));
        assertValid("options", "/booking", response(204, null, ""));
    }

    @Test
    void rejectsEmptyAndWhitespaceContentBodies() {
        assertFailure("post", "/auth", response(200, "application/json", ""), "POST /auth status 200 response body is present");
        assertFailure("post", "/auth", response(200, "application/json", " \n\t "), "POST /auth status 200 response body is present");
    }

    @Test
    void rejectsMissingAndUndocumentedMediaTypes() {
        assertFailure("post", "/auth", response(200, null, "{\"token\":\"secret-token-value\"}"),
            "POST /auth status 200 response Content-Type is present");
        assertFailure("post", "/auth", response(200, "text/plain", "{\"token\":\"secret-token-value\"}"),
            "POST /auth status 200 response Content-Type matches documented content");
    }

    @Test
    void rejectsMalformedJsonAndSchemaViolationsWithoutEchoingBodyValues() {
        assertSafeFailure(response(200, "application/json", "malformed-secret-value"), "response body is not valid JSON");
        assertSafeFailure(response(200, "application/json", "{\"other\":\"private-secret-value\"}"),
            "required response field is missing");
        assertSafeFailure(response(200, "application/json", "{\"token\":42}"), "response field type does not match");
    }

    @Test
    void rejectsUndocumentedStatusesAndBodiesForDocumentedBodylessResponses() {
        assertFailure("post", "/auth", response(404, "text/plain", "private-body-value"),
            "POST /auth status 404 is documented by an OpenAPI response");
        assertFailure("options", "/booking", response(204, null, "unexpected-body-value"),
            "OPTIONS /booking status 204 documented bodyless response has no body");
    }

    @Test
    void rejectsContradictoryBodylessMethodAndStatusContracts() {
        assertFailure("options", "/bad-booking", response(204, "application/json", "{}"),
            "OPTIONS /bad-booking status 204 bodyless method/status does not document response content");
        assertFailure("head", "/head", response(200, "text/plain", "not-allowed"),
            "HEAD /head status 200 bodyless method/status does not document response content");
        assertFailure("post", "/post-304", response(304, null, ""), "POST /post-304 status 304 is legal only for GET or HEAD");
    }

    private static void assertValid(String method, String path, Response response) {
        assertThatCode(() -> VALIDATOR.assertResponse(method, path, response)).doesNotThrowAnyException();
    }

    private static void assertFailure(String method, String path, Response response, String expectedReason) {
        assertThatThrownBy(() -> VALIDATOR.assertResponse(method, path, response))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining(expectedReason)
            .hasMessageNotContaining("private-secret-value")
            .hasMessageNotContaining("private-body-value")
            .hasMessageNotContaining("unexpected-body-value");
    }

    private static void assertSafeFailure(Response response, String expectedReason) {
        assertThatThrownBy(() -> VALIDATOR.assertResponse("post", "/auth", response))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("POST /auth status 200")
            .hasMessageContaining(expectedReason)
            .hasMessageNotContaining("malformed-secret-value")
            .hasMessageNotContaining("private-secret-value");
    }

    private static Response response(int status, String contentType, String body) {
        ResponseBuilder builder = new ResponseBuilder().setStatusCode(status).setBody(body);
        if (contentType != null) {
            builder.setHeader("Content-Type", contentType);
        }
        return builder.build();
    }
}
