package com.aria.framework.reporting;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class DiagnosticBodyFormatter {

    static final int MAX_DIAGNOSTIC_BODY_BYTES = 16 * 1024;
    private static final int MAX_JSON_DEPTH = 64;
    private static final int MAX_CONTAINER_ENTRIES = 4096;
    static final String OMITTED_BODY = "<body omitted>";
    static final String EMPTY_BODY = "<empty>";

    private static final String REDACTED = "<redacted>";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "token",
        "tokens",
        "accesstoken",
        "accesstokens",
        "refreshtoken",
        "refreshtokens",
        "idtoken",
        "idtokens",
        "jwttoken",
        "jwttokens",
        "apikey",
        "apikeys",
        "xapikey",
        "xapikeys",
        "clientsecret",
        "clientsecrets",
        "password",
        "passwords",
        "secret",
        "secrets",
        "githubtoken"
    );

    private DiagnosticBodyFormatter() {
    }

    static String bodyAsString(Object body, String contentType) {
        if (body == null) {
            return EMPTY_BODY;
        }
        if (body instanceof byte[] || body instanceof char[] || body instanceof java.io.InputStream) {
            return OMITTED_BODY;
        }
        if (body instanceof String text && text.isEmpty()) {
            return EMPTY_BODY;
        }
        if (!isJsonContentType(contentType)) {
            return OMITTED_BODY;
        }

        try {
            JsonNode json;
            if (body instanceof String text) {
                if (text.getBytes(StandardCharsets.UTF_8).length > MAX_DIAGNOSTIC_BODY_BYTES) {
                    return OMITTED_BODY;
                }
                json = parseJson(text);
            } else if (body instanceof JsonNode node) {
                if (!withinBounds(node, 0, new IdentityHashMap<>(), new long[] {0})) {
                    return OMITTED_BODY;
                }
                json = node.deepCopy();
            } else if (body instanceof Map<?, ?> || body instanceof Collection<?> || body.getClass().isArray()) {
                if (!withinBounds(body, 0, new IdentityHashMap<>(), new long[] {0})) {
                    return OMITTED_BODY;
                }
                json = MAPPER.valueToTree(body);
            } else {
                return OMITTED_BODY;
            }

            if (json == null || (!json.isObject() && !json.isArray())) {
                return OMITTED_BODY;
            }
            if (!redactSensitiveFields(json, 0)) {
                return OMITTED_BODY;
            }
            byte[] sanitized = MAPPER.writeValueAsBytes(json);
            if (sanitized.length > MAX_DIAGNOSTIC_BODY_BYTES) {
                return OMITTED_BODY;
            }
            return new String(sanitized, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            return OMITTED_BODY;
        }
    }

    static boolean isJsonContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || contentType.length() > 256) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "application/json".equals(mediaType)
            || (mediaType.startsWith("application/") && mediaType.endsWith("+json"));
    }

    private static JsonNode parseJson(String text) throws IOException {
        try (JsonParser parser = MAPPER.getFactory().createParser(text)) {
            JsonNode value = MAPPER.readTree(parser);
            if (value == null || parser.nextToken() != null) {
                return null;
            }
            return value;
        }
    }

    private static boolean redactSensitiveFields(JsonNode node, int depth) {
        if (node == null || depth > MAX_JSON_DEPTH) {
            return false;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            if (object.size() > MAX_CONTAINER_ENTRIES) {
                return false;
            }
            var fields = object.fieldNames();
            java.util.List<String> names = new java.util.ArrayList<>();
            fields.forEachRemaining(names::add);
            for (String name : names) {
                if (isSensitiveKey(name)) {
                    object.set(name, TextNode.valueOf(REDACTED));
                } else if (!redactSensitiveFields(object.get(name), depth + 1)) {
                    return false;
                }
            }
        } else if (node.isArray()) {
            if (node.size() > MAX_CONTAINER_ENTRIES) {
                return false;
            }
            for (JsonNode child : node) {
                if (!redactSensitiveFields(child, depth + 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean withinBounds(
        Object value,
        int depth,
        IdentityHashMap<Object, Boolean> active,
        long[] encodedBytes
    ) {
        if (depth > MAX_JSON_DEPTH) {
            return false;
        }
        if (value instanceof byte[] || value instanceof char[] || value instanceof java.io.InputStream) {
            return false;
        }
        if (value == null || value instanceof Boolean) {
            return true;
        }
        if (value instanceof String text) {
            encodedBytes[0] += text.getBytes(StandardCharsets.UTF_8).length;
            return encodedBytes[0] <= MAX_DIAGNOSTIC_BODY_BYTES;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            encodedBytes[0] += 32;
            return encodedBytes[0] <= MAX_DIAGNOSTIC_BODY_BYTES;
        }
        if (value instanceof Float number) {
            return Float.isFinite(number) && addNumberBudget(encodedBytes);
        }
        if (value instanceof Double number) {
            return Double.isFinite(number) && addNumberBudget(encodedBytes);
        }
        if (value instanceof JsonNode node) {
            if (node.isObject()) {
                if (node.size() > MAX_CONTAINER_ENTRIES || active.put(node, Boolean.TRUE) != null) {
                    return false;
                }
                try {
                    var fields = node.fields();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        encodedBytes[0] += field.getKey().getBytes(StandardCharsets.UTF_8).length;
                        if (encodedBytes[0] > MAX_DIAGNOSTIC_BODY_BYTES
                            || !withinBounds(field.getValue(), depth + 1, active, encodedBytes)) {
                            return false;
                        }
                    }
                    return true;
                } finally {
                    active.remove(node);
                }
            }
            if (node.isArray()) {
                if (node.size() > MAX_CONTAINER_ENTRIES || active.put(node, Boolean.TRUE) != null) {
                    return false;
                }
                try {
                    for (JsonNode child : node) {
                        if (!withinBounds(child, depth + 1, active, encodedBytes)) {
                            return false;
                        }
                    }
                    return true;
                } finally {
                    active.remove(node);
                }
            }
            if (node.isTextual()) {
                return withinBounds(node.textValue(), depth, active, encodedBytes);
            }
            if (node.isNumber()) {
                return addNumberBudget(encodedBytes);
            }
            return node.isBoolean() || node.isNull();
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_CONTAINER_ENTRIES || active.put(value, Boolean.TRUE) != null) {
                return false;
            }
            try {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        return false;
                    }
                    encodedBytes[0] += key.getBytes(StandardCharsets.UTF_8).length;
                    if (encodedBytes[0] > MAX_DIAGNOSTIC_BODY_BYTES
                        || !withinBounds(entry.getValue(), depth + 1, active, encodedBytes)) {
                        return false;
                    }
                }
                return true;
            } finally {
                active.remove(value);
            }
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > MAX_CONTAINER_ENTRIES || active.put(value, Boolean.TRUE) != null) {
                return false;
            }
            try {
                for (Object child : collection) {
                    if (!withinBounds(child, depth + 1, active, encodedBytes)) {
                        return false;
                    }
                }
                return true;
            } finally {
                active.remove(value);
            }
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            if (length > MAX_CONTAINER_ENTRIES || active.put(value, Boolean.TRUE) != null) {
                return false;
            }
            try {
                for (int index = 0; index < length; index++) {
                    if (!withinBounds(java.lang.reflect.Array.get(value, index), depth + 1, active, encodedBytes)) {
                        return false;
                    }
                }
                return true;
            } finally {
                active.remove(value);
            }
        }
        return false;
    }

    private static boolean addNumberBudget(long[] encodedBytes) {
        encodedBytes[0] += 32;
        return encodedBytes[0] <= MAX_DIAGNOSTIC_BODY_BYTES;
    }

    private static boolean isSensitiveKey(String name) {
        String normalized = NON_ALPHANUMERIC.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
        return SENSITIVE_KEYS.contains(normalized);
    }
}
