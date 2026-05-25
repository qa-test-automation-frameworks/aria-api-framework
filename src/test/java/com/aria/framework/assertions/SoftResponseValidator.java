package com.aria.framework.assertions;

import io.restassured.response.Response;
import org.assertj.core.api.SoftAssertions;

import java.util.Locale;

public final class SoftResponseValidator {

    private final Response response;
    private final SoftAssertions softly = new SoftAssertions();

    private SoftResponseValidator(Response response) {
        this.response = response;
    }

    public static SoftResponseValidator softAssert(Response response) {
        return new SoftResponseValidator(response);
    }

    public SoftResponseValidator status(int expectedStatus) {
        softly.assertThat(response.statusCode())
            .as("HTTP status")
            .isEqualTo(expectedStatus);
        return this;
    }

    public SoftResponseValidator jsonContentType() {
        String contentType = response.header("Content-Type");
        softly.assertThat(contentType)
            .as("Content-Type response header")
            .isNotBlank();
        if (contentType != null) {
            softly.assertThat(contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                .as("Content-Type media type")
                .isEqualTo("application/json");
        }
        return this;
    }

    public SoftResponseValidator slaCompliant(long maxMillis) {
        softly.assertThat(response.time())
            .as("response time")
            .isLessThan(maxMillis);
        return this;
    }

    public SoftResponseValidator field(String jsonPath, Object expectedValue) {
        Object actualValue = response.jsonPath().get(jsonPath);
        softly.assertThat(actualValue)
            .as("JSON field " + jsonPath)
            .isEqualTo(expectedValue);
        return this;
    }

    public void verify() {
        softly.assertAll();
    }
}
