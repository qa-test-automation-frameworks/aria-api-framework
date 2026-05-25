package com.aria.framework.auth;

import io.restassured.specification.RequestSpecification;

import java.util.function.Supplier;

/**
 * Adds an API key to a named HTTP header when a key is available.
 */
public final class ApiKeyAuthStrategy implements AuthStrategy {

    private final String headerName;
    private final Supplier<String> apiKeySupplier;

    public ApiKeyAuthStrategy(String headerName, String apiKey) {
        this(headerName, () -> apiKey);
    }

    public ApiKeyAuthStrategy(String headerName, Supplier<String> apiKeySupplier) {
        this.headerName = headerName;
        this.apiKeySupplier = apiKeySupplier;
    }

    @Override
    public RequestSpecification apply(RequestSpecification requestSpecification) {
        String apiKey = apiKeySupplier.get();
        if (apiKey == null || apiKey.isBlank()) {
            return requestSpecification;
        }
        return requestSpecification.header(headerName, apiKey);
    }
}
