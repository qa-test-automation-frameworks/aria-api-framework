package com.aria.framework.reporting;

import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Immutable, pre-rendered-safe view of an HTTP exchange for logs and attachments. */
record DiagnosticSnapshot(
    String method,
    String uri,
    List<String> requestHeaders,
    String requestBody,
    Integer responseStatus,
    List<String> responseHeaders,
    String responseBody,
    String exceptionType,
    String failureCategory
) {

    private static final String OMITTED_URL = "<url omitted>";
    private static final Pattern SAFE_METHOD = Pattern.compile("[A-Za-z][A-Za-z0-9!#$%&'*+.^_`|~-]{0,31}");
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final int MAX_HEADERS_INSPECTED = 64;

    DiagnosticSnapshot {
        requestHeaders = List.copyOf(requestHeaders);
        responseHeaders = List.copyOf(responseHeaders);
    }

    static DiagnosticSnapshot forRequest(FilterableRequestSpecification request) {
        return new DiagnosticSnapshot(
            safeMethod(request.getMethod()),
            safeUri(request.getURI()),
            safeHeaders(request.getHeaders()),
            DiagnosticBodyFormatter.bodyAsString(request.getBody(), headerValue(request.getHeaders(), "Content-Type")),
            null,
            List.of(),
            null,
            null,
            null
        );
    }

    static DiagnosticSnapshot forResponse(FilterableRequestSpecification request, Response response) {
        int status = safeStatus(response.statusCode());
        String contentType = headerValue(response.getHeaders(), "Content-Type");
        String responseBody = safeResponseBody(response, contentType);
        return new DiagnosticSnapshot(
            safeMethod(request.getMethod()),
            safeUri(request.getURI()),
            safeHeaders(request.getHeaders()),
            DiagnosticBodyFormatter.bodyAsString(request.getBody(), headerValue(request.getHeaders(), "Content-Type")),
            status,
            safeHeaders(response.getHeaders()),
            responseBody,
            null,
            status >= 400 ? "http_response" : null
        );
    }

    static DiagnosticSnapshot forException(FilterableRequestSpecification request, Throwable exception) {
        return new DiagnosticSnapshot(
            safeMethod(request.getMethod()),
            safeUri(request.getURI()),
            safeHeaders(request.getHeaders()),
            DiagnosticBodyFormatter.bodyAsString(request.getBody(), headerValue(request.getHeaders(), "Content-Type")),
            null,
            List.of(),
            null,
            exception.getClass().getSimpleName(),
            failureCategory(exception)
        );
    }

    String label() {
        return "HTTP " + method + " " + uri;
    }

    String render() {
        StringBuilder output = new StringBuilder()
            .append("Request\n")
            .append("Method: ").append(method).append('\n')
            .append("URL: ").append(uri).append('\n')
            .append("Headers:\n");
        appendLines(output, requestHeaders);
        output.append("Cookies: omitted\n")
            .append("Body: ").append(requestBody).append('\n');

        if (responseStatus != null) {
            output.append("Response status: ").append(responseStatus).append('\n')
                .append("Response headers:\n");
            appendLines(output, responseHeaders);
            output.append("Response body: ").append(responseBody).append('\n');
            if (failureCategory != null) {
                output.append("Failure category: ").append(failureCategory).append('\n');
            }
        }
        if (exceptionType != null) {
            output.append("Exception type: ").append(exceptionType).append('\n')
                .append("Failure category: ").append(failureCategory).append('\n');
        }
        return output.toString();
    }

    private static void appendLines(StringBuilder output, List<String> lines) {
        if (lines.isEmpty()) {
            output.append("- <headers omitted>\n");
            return;
        }
        lines.forEach(line -> output.append("- ").append(line).append('\n'));
    }

    private static String safeMethod(String method) {
        if (method == null || method.length() > 32 || !SAFE_METHOD.matcher(method).matches()) {
            return "<method omitted>";
        }
        return method.toUpperCase(Locale.ROOT);
    }

    private static int safeStatus(int status) {
        return status >= 100 && status <= 599 ? status : 0;
    }

    private static String safeUri(String rawUri) {
        if (rawUri == null || rawUri.length() > 4096) {
            return OMITTED_URL;
        }
        try {
            URI uri = new URI(rawUri);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getRawPath();
            if (scheme == null || host == null || !("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)) || port > 65535 || port == 0) {
                return OMITTED_URL;
            }
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.length() > 2048) {
                path = "/<path omitted>";
            }
            if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) {
                host = "[" + host + "]";
            }
            return scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT)
                + (port < 0 ? "" : ":" + port) + path;
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return OMITTED_URL;
        }
    }

    private static List<String> safeHeaders(Headers headers) {
        List<String> result = new ArrayList<>();
        if (headers == null) {
            return result;
        }
        int inspected = 0;
        for (Header header : headers) {
            if (result.size() >= 8 || inspected++ >= MAX_HEADERS_INSPECTED) {
                break;
            }
            String name = header.getName();
            String value = header.getValue();
            if (name == null || name.length() > 64) {
                continue;
            }
            switch (name.toLowerCase(Locale.ROOT)) {
                case "content-type" -> result.add("Content-Type: "
                    + (DiagnosticBodyFormatter.isJsonContentType(value) ? "application/json" : "other"));
                case "accept" -> result.add("Accept: "
                    + (value != null && value.length() <= 256
                        && value.toLowerCase(Locale.ROOT).contains("application/json")
                        ? "application/json" : "present"));
                case "x-request-id", "x-correlation-id" -> result.add(name + ": "
                    + (value != null && value.length() <= 64 && SAFE_CORRELATION_ID.matcher(value).matches()
                        ? value : "<omitted>"));
                default -> {
                    // All other header names and values are intentionally omitted.
                }
            }
        }
        return result;
    }

    private static String headerValue(Headers headers, String requestedName) {
        if (headers == null) {
            return null;
        }
        int inspected = 0;
        for (Header header : headers) {
            if (inspected++ >= MAX_HEADERS_INSPECTED) {
                return null;
            }
            if (header.getName() != null && header.getName().length() <= 64
                && requestedName.equalsIgnoreCase(header.getName())) {
                return header.getValue();
            }
        }
        return null;
    }

    private static String safeResponseBody(Response response, String contentType) {
        String contentLength = headerValue(response.getHeaders(), "Content-Length");
        if (contentLength == null) {
            return DiagnosticBodyFormatter.OMITTED_BODY;
        }
        try {
            long length = Long.parseLong(contentLength);
            if (length < 0 || length > DiagnosticBodyFormatter.MAX_DIAGNOSTIC_BODY_BYTES) {
                return DiagnosticBodyFormatter.OMITTED_BODY;
            }
            String contentEncoding = headerValue(response.getHeaders(), "Content-Encoding");
            if (contentEncoding != null && !"identity".equalsIgnoreCase(contentEncoding.trim())) {
                return DiagnosticBodyFormatter.OMITTED_BODY;
            }
            if (length == 0) {
                return DiagnosticBodyFormatter.EMPTY_BODY;
            }
            return DiagnosticBodyFormatter.bodyAsString(response.getBody().asString(), contentType);
        } catch (RuntimeException ignored) {
            return DiagnosticBodyFormatter.OMITTED_BODY;
        }
    }

    private static String failureCategory(Throwable exception) {
        int causesInspected = 0;
        for (Throwable cause = exception; cause != null && causesInspected++ < 8; cause = cause.getCause()) {
            if (cause instanceof IOException || cause instanceof SocketException
                || cause instanceof SocketTimeoutException || cause instanceof UnknownHostException
                || cause instanceof TimeoutException) {
                return "transport";
            }
        }
        return "request";
    }
}
