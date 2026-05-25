package com.aria.framework.reporting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FailureDiagnosticFilterTest {

    @Test
    void sanitizesSecretsFromDiagnosticAttachments() {
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

        assertThat(sanitized)
            .doesNotContain("mock-github-token", "mock-cookie-token", "mock-refresh-token", "mock-password")
            .doesNotContain("mock-api-key", "mock-client-secret", "mock-access-token");
        assertThat(sanitized).contains("<redacted>");
    }
}
