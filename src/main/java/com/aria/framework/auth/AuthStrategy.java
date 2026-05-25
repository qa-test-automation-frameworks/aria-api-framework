package com.aria.framework.auth;

import io.restassured.specification.RequestSpecification;

/**
 * Applies authentication material to a RestAssured request specification.
 */
@FunctionalInterface
public interface AuthStrategy {

    RequestSpecification apply(RequestSpecification requestSpecification);

    static AuthStrategy none() {
        return requestSpecification -> requestSpecification;
    }
}
