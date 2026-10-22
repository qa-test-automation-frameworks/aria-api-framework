package com.aria.framework.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureDiagnosticFilterTest {

    @Test
    void omitsLegacyMixedTextWhenItMayContainSecrets() {
        String raw = """
            Authorization: Bearer mock-github-token
            Cookie: token=mock-cookie-token
            Set-Cookie: refresh_token=mock-refresh-token
            password=mock-password
            api_key=mock-api-key
            client_secret=mock-client-secret
            https://example.test/callback?access_token=mock-access-token
            github.token=mock-github-token
            """;

        String sanitized = FailureDiagnosticFilter.sanitize(raw);

        boolean omittedWithoutCanaries = sanitized.equals("<unstructured diagnostic omitted>")
            && !sanitized.contains("mock-github-token")
            && !sanitized.contains("mock-cookie-token")
            && !sanitized.contains("mock-refresh-token")
            && !sanitized.contains("mock-password")
            && !sanitized.contains("mock-api-key")
            && !sanitized.contains("mock-client-secret")
            && !sanitized.contains("mock-access-token");
        assertThat(omittedWithoutCanaries).as("legacy text containing secrets is omitted").isTrue();
    }
}
