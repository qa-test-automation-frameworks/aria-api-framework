package com.aria.framework.assertions;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.restassured.response.Response;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
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
        Map<String, Object> operation = operation(method, path);
        Map<String, Object> responses = childMap(operation, "responses");
        String statusCode = String.valueOf(response.statusCode());
        assertThat(responses)
            .as(method.toUpperCase(Locale.ROOT) + " " + path + " documented responses")
            .containsKey(statusCode);

        String contentType = response.header("Content-Type");
        if (contentType == null || response.body().asString().isBlank()) {
            return;
        }

        Map<String, Object> documentedStatus = childMap(responses, statusCode);
        Map<String, Object> content = childMap(documentedStatus, "content");
        if (!content.isEmpty()) {
            String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            assertThat(content.keySet())
                .as(method.toUpperCase(Locale.ROOT) + " " + path + " documented response media types")
                .anyMatch(mediaType::equalsIgnoreCase);
        }

        assertBodyMatchesSchema(method, path, response);
    }

    private void assertBodyMatchesSchema(String method, String path, Response response) {
        SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.statusCode())
            .withBody(response.body().asString());
        for (String headerName : response.headers().asList().stream().map(header -> header.getName()).distinct().toList()) {
            builder.withHeader(headerName, response.headers().getValues(headerName));
        }

        ValidationReport report = bodyValidator.validateResponse(
            path,
            Request.Method.valueOf(method.toUpperCase(Locale.ROOT)),
            builder.build()
        );

        assertThat(report.hasErrors())
            .as(method.toUpperCase(Locale.ROOT) + " " + path + " response body matches the OpenAPI schema: "
                + report.getMessages().stream()
                    .map(ValidationReport.Message::getMessage)
                    .collect(Collectors.joining("; ")))
            .isFalse();
    }

    private Map<String, Object> operation(String method, String path) {
        Map<String, Object> paths = childMap(spec, "paths");
        Map<String, Object> pathItem = childMap(paths, path);
        assertThat(pathItem)
            .as("OpenAPI path " + path)
            .isNotEmpty();
        Map<String, Object> operation = childMap(pathItem, method.toLowerCase(Locale.ROOT));
        assertThat(operation)
            .as("OpenAPI operation " + method.toUpperCase(Locale.ROOT) + " " + path)
            .isNotEmpty();
        return operation;
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
