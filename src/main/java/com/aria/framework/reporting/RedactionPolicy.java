package com.aria.framework.reporting;

import java.util.regex.Pattern;

/**
 * Central redaction rules for HTTP diagnostics, logs, and exception-safe messages.
 */
public final class RedactionPolicy {

    private static final String REDACTED = "<redacted>";
    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
        "(?im)^\\s*((?:Authorization|Proxy-Authorization|Cookie|Set-Cookie|X-Api-Key|Api-Key)\\s*[:=]\\s*).+$"
    );
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
        "(?i)([\"']?(?:token|access_token|refresh_token|id_token|api_key|apikey|client_secret|clientSecret|password|secret|github\\.token)[\"']?\\s*[:=]\\s*[\"']?)[^\"'\\r\\n,}&\\s]+"
    );
    private static final Pattern SENSITIVE_QUERY_PARAM = Pattern.compile(
        "(?i)([?&](?:token|access_token|refresh_token|id_token|api_key|client_secret|password)=)[^&\\s]+"
    );

    private RedactionPolicy() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return SENSITIVE_QUERY_PARAM
            .matcher(SENSITIVE_KEY_VALUE
                .matcher(SENSITIVE_HEADER
                    .matcher(text)
                    .replaceAll("$1" + REDACTED))
                .replaceAll("$1" + REDACTED))
            .replaceAll("$1" + REDACTED);
    }
}
