package com.aria.framework.reporting;

import java.nio.charset.StandardCharsets;

final class DiagnosticBodyFormatter {

    private DiagnosticBodyFormatter() {
    }

    static String bodyAsString(Object body) {
        if (body == null) {
            return "";
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (body instanceof char[] characters) {
            return new String(characters);
        }
        return body.toString();
    }
}
