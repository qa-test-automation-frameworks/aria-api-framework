package com.aria.framework.auth;

import io.restassured.specification.RequestSpecification;

import java.util.function.Supplier;

/**
 * Adds a token as a Cookie header value.
 */
public final class CookieTokenAuthStrategy implements AuthStrategy {

    private final String cookieName;
    private final Supplier<String> tokenSupplier;

    public CookieTokenAuthStrategy(String cookieName, String token) {
        this(cookieName, () -> token);
    }

    public CookieTokenAuthStrategy(String cookieName, Supplier<String> tokenSupplier) {
        this.cookieName = cookieName;
        this.tokenSupplier = tokenSupplier;
    }

    @Override
    public RequestSpecification apply(RequestSpecification requestSpecification) {
        String token = tokenSupplier.get();
        if (token == null || token.isBlank()) {
            return requestSpecification;
        }
        return requestSpecification.header("Cookie", cookieName + "=" + token);
    }
}
