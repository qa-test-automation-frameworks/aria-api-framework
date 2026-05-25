package com.aria.framework.auth;

import io.restassured.specification.RequestSpecification;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Adds an HTTP Basic Authorization header.
 */
public final class BasicAuthStrategy implements AuthStrategy {

    private final String username;
    private final String password;

    public BasicAuthStrategy(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public RequestSpecification apply(RequestSpecification requestSpecification) {
        if (username == null || username.isBlank() || password == null) {
            return requestSpecification;
        }
        String credential = username + ":" + password;
        String encodedCredential = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        return requestSpecification.header("Authorization", "Basic " + encodedCredential);
    }
}
