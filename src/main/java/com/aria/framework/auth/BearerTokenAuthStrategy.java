package com.aria.framework.auth;

import io.restassured.specification.RequestSpecification;

import java.util.function.Supplier;

/**
 * Adds an Authorization bearer token header when a token is available.
 */
public sealed class BearerTokenAuthStrategy implements AuthStrategy permits OAuth2TokenProviderAuthStrategy {

    private final Supplier<String> tokenSupplier;

    public BearerTokenAuthStrategy(String token) {
        this(() -> token);
    }

    public BearerTokenAuthStrategy(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public RequestSpecification apply(RequestSpecification requestSpecification) {
        String token = tokenSupplier.get();
        if (token == null || token.isBlank()) {
            return requestSpecification;
        }
        return requestSpecification.header("Authorization", "Bearer " + token);
    }
}
