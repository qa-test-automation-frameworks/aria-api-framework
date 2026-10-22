package com.aria.framework.reporting;

import java.util.regex.Pattern;

/**
 * Conservative fallback for legacy unstructured diagnostic text.
 */
public final class RedactionPolicy {

    private static final String OMITTED = "<unstructured diagnostic omitted>";
    private static final Pattern SENSITIVE_SIGNAL = Pattern.compile(
        "(?i)(?:authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[-_.]?key|"
            + "access[-_. ]?token|refresh[-_. ]?token|id[-_. ]?token|jwt[-_. ]?token|"
            + "client[-_. ]?secret|github[._-]token|password|secret|token)\\s*[:=]"
            + "|\\bbearer\\s+\\S+"
    );
    private static final Pattern QUERY_URL = Pattern.compile("(?i)https?://[^\\s]*\\?[^\\s]*");
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)https?://[^/@\\s]+@[^/\\s]+");

    private RedactionPolicy() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        if (SENSITIVE_SIGNAL.matcher(text).find()
            || QUERY_URL.matcher(text).find()
            || URL_USER_INFO.matcher(text).find()) {
            return OMITTED;
        }
        return text;
    }
}
