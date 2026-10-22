package com.aria.framework.assertions;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.restassured.response.Response;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public final class OpenApiResponseValidator {

    private final Map<String, Object> spec;
    private final OpenApiInteractionValidator bodyValidator;

    private OpenApiResponseValidator(Map<String, Object> spec, OpenApiInteractionValidator bodyValidator) {
        this.spec = spec;
        this.bodyValidator = bodyValidator;
    }

    public static OpenApiResponseValidator fromClasspath(String resourcePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
            assertThat(inputStream)
                .as("OpenAPI resource " + resourcePath)
                .isNotNull();
            Map<String, Object> spec = new Yaml().load(inputStream);
            OpenApiInteractionValidator bodyValidator = OpenApiInteractionValidator
                .createFor(resourcePath)
                .build();
            return new OpenApiResponseValidator(spec, bodyValidator);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to load OpenAPI resource " + resourcePath, exception);
        }
    }

    public void assertResponse(String method, String path, Response response) {
        String normalizedMethod = method.toUpperCase(Locale.ROOT);
        String operationName = normalizedMethod + " " + path;
        Map<String, Object> operation = operation(normalizedMethod, path);
        Map<?, ?> responses = childMap(operation, "responses");
        String statusCode = String.valueOf(response.statusCode());
        Object responseDefinition = documentedResponse(responses, statusCode, operationName);
        Map<String, Object> documentedResponse = resolveResponse(
            responseDefinition, operationName, statusCode, new HashSet<>()
        );
        Map<String, Object> content = responseContent(documentedResponse, operationName, statusCode);

        assertBodylessContractIsConsistent(normalizedMethod, statusCode, content, operationName);
        String body = responseBody(response);
        if (content.isEmpty()) {
            assertThat(body.isEmpty())
                .as(operationName + " status " + statusCode + " documented bodyless response has no body")
                .isTrue();
            return;
        }

        assertThat(body)
            .as(operationName + " status " + statusCode + " response body is present for documented content")
            .isNotBlank();

        String contentType = normalizedMediaType(responseHeader(response, "Content-Type"));
        assertThat(contentType)
            .as(operationName + " status " + statusCode + " response Content-Type is present")
            .isNotBlank();
        assertThat(content.keySet())
            .as(operationName + " status " + statusCode + " response Content-Type matches documented content")
            .anyMatch(documentedMediaType -> contentType != null
                && contentType.equals(normalizedMediaType(documentedMediaType)));

        assertBodyMatchesSchema(normalizedMethod, path, statusCode, response, body);
    }

    private Object documentedResponse(Map<?, ?> responses, String statusCode, String operationName) {
        Object response = responseForKey(responses, statusCode);
        if (response == null) {
            response = responseForKey(responses, statusCode.substring(0, 1) + "XX");
        }
        if (response == null) {
            response = responseForKey(responses, "default");
        }

        assertThat(response)
            .as(operationName + " status " + statusCode + " is documented by an OpenAPI response")
            .isNotNull();
        return response;
    }

    private static Object responseForKey(Map<?, ?> responses, String expectedKey) {
        for (Map.Entry<?, ?> entry : responses.entrySet()) {
            if (expectedKey.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> resolveResponse(Object response, String operationName, String statusCode,
                                                Set<String> references) {
        Object resolved = response;
        while (resolved instanceof Map<?, ?> responseMap && responseMap.containsKey("$ref")) {
            String reference = String.valueOf(responseMap.get("$ref"));
            assertThat(reference)
                .as(operationName + " status " + statusCode + " response reference is local")
                .startsWith("#/");
            assertThat(references.add(reference))
                .as(operationName + " status " + statusCode + " response references do not contain a cycle")
                .isTrue();
            resolved = resolveLocalReference(reference, operationName, statusCode);
        }

        assertThat(resolved)
            .as(operationName + " status " + statusCode + " response definition is an object")
            .isInstanceOf(Map.class);
        return castMap(resolved);
    }

    private Object resolveLocalReference(String reference, String operationName, String statusCode) {
        Object current = spec;
        String pointer = reference.substring(2);
        for (String segment : pointer.split("/", -1)) {
            String key = segment.replace("~1", "/").replace("~0", "~");
            assertThat(current)
                .as(operationName + " status " + statusCode + " response reference resolves through objects")
                .isInstanceOf(Map.class);
            Map<?, ?> currentMap = (Map<?, ?>) current;
            assertThat(currentMap.containsKey(key))
                .as(operationName + " status " + statusCode + " local response reference target exists")
                .isTrue();
            current = currentMap.get(key);
        }
        return current;
    }

    private Map<String, Object> responseContent(Map<String, Object> response, String operationName, String statusCode) {
        if (!response.containsKey("content")) {
            return Map.of();
        }

        Object content = response.get("content");
        assertThat(content)
            .as(operationName + " status " + statusCode + " documented response content is an object")
            .isInstanceOf(Map.class);
        return castMap(content);
    }

    private void assertBodylessContractIsConsistent(String method, String statusCode,
                                                    Map<String, Object> content, String operationName) {
        boolean headResponse = method.equals("HEAD");
        boolean noContentStatus = statusCode.equals("204") || statusCode.equals("304");
        if (statusCode.equals("304")) {
            assertThat(method)
                .as(operationName + " status 304 is legal only for GET or HEAD")
                .isIn("GET", "HEAD");
        }
        if (headResponse || noContentStatus) {
            assertThat(content)
                .as(operationName + " status " + statusCode + " bodyless method/status does not document response content")
                .isEmpty();
        }
    }

    private void assertBodyMatchesSchema(String method, String path, String statusCode, Response response, String body) {
        SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.statusCode())
            .withBody(body);
        for (String headerName : response.headers().asList().stream().map(header -> header.getName()).distinct().toList()) {
            builder.withHeader(headerName, response.headers().getValues(headerName));
        }

        ValidationReport report = bodyValidator.validateResponse(
            path,
            Request.Method.valueOf(method.toUpperCase(Locale.ROOT)),
            builder.build()
        );

        String failureCategories = report.getMessages().stream()
            .map(ValidationReport.Message::getMessage)
            .map(OpenApiResponseValidator::safeCategory)
            .collect(Collectors.joining("; "));
        if (failureCategories.isBlank()) {
            failureCategories = "response body does not match the documented schema";
        }
        assertThat(report.hasErrors())
            .as(method + " " + path + " status " + statusCode + " response body matches the documented schema: "
                + failureCategories)
            .isFalse();
    }

    private static String safeCategory(String message) {
        if (message == null || message.isBlank()) {
            return "schema validation failed";
        }
        String lowerCase = message.toLowerCase(Locale.ROOT);
        if (lowerCase.contains("required")) {
            return "required response field is missing";
        }
        if (lowerCase.contains("type")) {
            return "response field type does not match";
        }
        if (lowerCase.contains("json")) {
            return "response body is not valid JSON";
        }
        return "response body does not match the documented schema";
    }

    private static String normalizedMediaType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        return mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String responseHeader(Response response, String name) {
        return response.headers().asList().stream()
            .filter(header -> name.equalsIgnoreCase(header.getName()))
            .map(header -> header.getValue())
            .findFirst()
            .orElse(null);
    }

    private static String responseBody(Response response) {
        return response.getBody() == null ? "" : response.getBody().asString();
    }

    private Map<String, Object> operation(String method, String path) {
        Map<String, Object> paths = childMap(spec, "paths");
        Map<String, Object> pathItem = childMap(paths, path);
        assertThat(pathItem)
            .as("OpenAPI path " + path + " is documented")
            .isNotEmpty();
        Map<String, Object> operation = childMap(pathItem, method.toLowerCase(Locale.ROOT));
        assertThat(operation)
            .as("OpenAPI operation " + method + " " + path + " is documented")
            .isNotEmpty();
        return operation;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childMap(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
