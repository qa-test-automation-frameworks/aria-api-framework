package com.aria.framework.reporting;

import com.aria.framework.clients.BaseApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 0)
@Execution(ExecutionMode.SAME_THREAD)
class SanitizedClientDiagnosticsTest {

    private static final String BODY = """
        {
          "profile": {
            "access_token": "body-access-canary-fragment-A1 with \\\"quotes\\\" and \\\\slashes",
            "other": {"Password": "body-password-canary-fragment-B2"},
            "items": [
              {"jwtToken": "body-jwt-canary-fragment-C3"},
              {"GitHub.Token": "body-github-canary-fragment-D4"},
              {"refresh_token": null}
            ]
          },
          "safe": "safe-body-value"
        }
        """;
    private static final String SUCCESS_RESPONSE = """
        {"message":"safe-response-value","token":"response-token-canary-fragment-E5",
         "items":[{"client_secret":"response-secret-canary-fragment-F6"}]}
        """;

    @Test
    void structuralFormatterRedactsNestedJsonAndOmitsMalformedBinaryAndOversizedBodies() {
        String formatted = DiagnosticBodyFormatter.bodyAsString(BODY, "application/json; charset=UTF-8");
        boolean formattedSafely = formatted.contains("<redacted>")
            && formatted.contains("safe-body-value")
            && !formatted.contains("body-access-canary")
            && !formatted.contains("body-password-canary")
            && !formatted.contains("body-jwt-canary")
            && !formatted.contains("body-github-canary");
        assertThat(formattedSafely).as("nested sensitive values are replaced while safe JSON remains").isTrue();

        boolean malformedOmitted = DiagnosticBodyFormatter.bodyAsString("{not valid json", "application/json")
            .equals(DiagnosticBodyFormatter.OMITTED_BODY);
        boolean binaryOmitted = DiagnosticBodyFormatter.bodyAsString(
            "{\"token\":\"hidden\"}".getBytes(StandardCharsets.UTF_8), "application/json"
        ).equals(DiagnosticBodyFormatter.OMITTED_BODY);
        boolean oversizedOmitted = DiagnosticBodyFormatter.bodyAsString(
            "x".repeat(DiagnosticBodyFormatter.MAX_DIAGNOSTIC_BODY_BYTES + 1), "application/json"
        ).equals(DiagnosticBodyFormatter.OMITTED_BODY);
        boolean unknownOmitted = DiagnosticBodyFormatter.bodyAsString(new Object(), "application/json")
            .equals(DiagnosticBodyFormatter.OMITTED_BODY);
        assertThat(malformedOmitted && binaryOmitted && oversizedOmitted && unknownOmitted)
            .as("malformed, binary, oversized, and unknown bodies are omitted").isTrue();
    }

    @Test
    void configuredClientSanitizesEverySinkWithoutChangingTheTransport(WireMockRuntimeInfo wireMock) throws Exception {
        String baseUri = wireMock.getHttpBaseUrl();
        String apiKey = "header-api-key-canary-fragment-K2";
        String bearer = "bearer-canary-fragment-J1";
        String cookie = "cookie-canary-fragment-L3";
        String query = "query-token-canary fragment-M4 /+?&";
        String user = "userinfo-user-canary-fragment-N5";
        String password = "userinfo-password-canary-fragment-O6";
        String errorResponse = "{\"message\":\"safe-error-value\",\"refreshToken\":\"error-refresh-canary-fragment-G7\"}";
        String assertionResponse = "{\"message\":\"safe-assertion-value\",\"jwtToken\":\"assertion-jwt-canary-fragment-H8\"}";
        String transportBody = "{\"password\":\"transport-password-canary-fragment-I9\",\"safe\":\"safe-transport-value\"}";

        stubFor(WireMock.post(urlPathEqualTo("/success")).willReturn(aResponse()
            .withStatus(200).withHeader("Content-Type", "application/json")
            .withHeader("Content-Length", contentLength(SUCCESS_RESPONSE)).withBody(SUCCESS_RESPONSE)));
        stubFor(WireMock.post(urlPathEqualTo("/http-error")).willReturn(aResponse()
            .withStatus(422).withHeader("Content-Type", "application/json")
            .withHeader("Content-Length", contentLength(errorResponse)).withBody(errorResponse)));
        stubFor(WireMock.post(urlPathEqualTo("/assertion-error")).willReturn(aResponse()
            .withStatus(409).withHeader("Content-Type", "application/json")
            .withHeader("Content-Length", contentLength(assertionResponse)).withBody(assertionResponse)));
        stubFor(WireMock.post(urlPathEqualTo("/transport-error")).willReturn(aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER)));

        ProbeClient client = new ProbeClient(baseUri);
        Set<Path> attachmentsBefore = attachmentFiles();
        Path testLogFile = Path.of("build/logs/aria-test.log");
        long testLogSizeBefore = fileSizeIfPresent(testLogFile);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        boolean assertionFailed = false;
        boolean transportFailed = false;
        Response success;
        Response error;
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);

            success = post(client, baseUri, "/success", BODY, request -> request
                .header("Authorization", "Bearer " + bearer)
                .header("X-Api-Key", apiKey)
                .cookie("session_cookie", cookie)
                .queryParam("access_token", query));
            error = post(client, userInfoUri(baseUri, user, password), "/http-error", BODY, request -> request
                .auth().preemptive().basic(user, password)
                .header("X-Api-Key", apiKey)
                .cookie("session_cookie", cookie)
                .queryParam("access_token", query));

            try {
                post(client, baseUri, "/assertion-error", BODY, request -> request
                    .header("Authorization", "Bearer " + bearer)
                    .header("X-Api-Key", apiKey)
                    .cookie("session_cookie", cookie)
                    .queryParam("access_token", query))
                    .then().statusCode(200);
            } catch (AssertionError expected) {
                assertionFailed = true;
            }

            try {
                post(client, baseUri, "/transport-error", transportBody, request -> request
                    .header("Authorization", "Bearer " + bearer)
                    .header("X-Api-Key", apiKey)
                    .cookie("session_cookie", cookie)
                    .queryParam("access_token", query));
            } catch (Exception expected) {
                assertThat(expected).isInstanceOf(IOException.class);
                transportFailed = true;
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(error.statusCode()).isEqualTo(422);
        assertThat(assertionFailed).as("the validation assertion failure reaches the caller").isTrue();
        assertThat(transportFailed).as("the transport failure reaches the caller").isTrue();
        assertThat(success.getBody().asString().contains("response-token-canary-fragment-E5"))
            .as("the response body remains unchanged for the caller").isTrue();
        verifyOriginalRequests(apiKey, bearer, cookie, query, user, password, transportBody);

        Set<Path> newAttachments = attachmentFiles();
        newAttachments.removeAll(attachmentsBefore);
        List<String> attachmentContents = new ArrayList<>();
        for (Path attachment : newAttachments) {
            attachmentContents.add(Files.readString(attachment, StandardCharsets.UTF_8));
        }
        assertThat(attachmentContents.size()).as("persisted Allure HTTP diagnostic attachments").isGreaterThan(0);

        String persistedTestLog = readAppendedText(testLogFile, testLogSizeBefore);
        String persistedAttachments = String.join("\n", attachmentContents);
        String capturedStreams = stdout.toString(StandardCharsets.UTF_8) + stderr.toString(StandardCharsets.UTF_8);
        assertThat(!persistedTestLog.isBlank()).as("the configured test log captured HTTP summaries").isTrue();
        String allSinks = persistedTestLog + "\n" + persistedAttachments + "\n" + capturedStreams;
        List<String> canaryValues = List.of(
            bearer, apiKey, cookie, query, user, password,
            "body-access-canary-fragment-A1", "body-password-canary-fragment-B2",
            "body-jwt-canary-fragment-C3", "body-github-canary-fragment-D4",
            "response-token-canary-fragment-E5", "response-secret-canary-fragment-F6",
            "error-refresh-canary-fragment-G7", "assertion-jwt-canary-fragment-H8",
            "transport-password-canary-fragment-I9"
        );
        List<String> canaryFragments = List.of(
            "bearer-canary", "header-api-key", "cookie-canary", "query-token-canary",
            "userinfo-user-canary", "userinfo-password-canary", "body-access-canary",
            "body-password-canary", "body-jwt-canary", "body-github-canary",
            "response-token-canary", "response-secret-canary", "error-refresh-canary",
            "assertion-jwt-canary", "transport-password-canary"
        );
        assertThat(canaryValues.stream().noneMatch(allSinks::contains))
            .as("no complete synthetic credential or payload canary is persisted").isTrue();
        assertThat(canaryFragments.stream().noneMatch(allSinks::contains))
            .as("no deliberate canary fragment is persisted").isTrue();
        List<String> usefulEvidence = List.of(
            "Method: POST", "/success", "Response status: 200", "safe-body-value", "<redacted>",
            "Response status: 422", "safe-error-value", "safe-assertion-value",
            "Failure category: http_response", "Failure category: transport"
        );
        assertThat(usefulEvidence.stream().allMatch(allSinks::contains))
            .as("diagnostics keep safe method, path, status, and JSON context").isTrue();
    }

    private static void verifyOriginalRequests(
        String apiKey,
        String bearer,
        String cookie,
        String query,
        String user,
        String password,
        String transportBody
    ) {
        try {
            verify(postRequestedFor(urlPathEqualTo("/success"))
                .withHeader("Authorization", equalTo("Bearer " + bearer))
                .withHeader("X-Api-Key", equalTo(apiKey))
                .withCookie("session_cookie", equalTo(cookie))
                .withQueryParam("access_token", equalTo(query))
                .withRequestBody(equalToJson(BODY, true, true)));
            verify(postRequestedFor(urlPathEqualTo("/http-error"))
                .withHeader("Authorization", equalTo("Basic " + java.util.Base64.getEncoder().encodeToString(
                    (user + ":" + password).getBytes(StandardCharsets.UTF_8))))
                .withHeader("X-Api-Key", equalTo(apiKey))
                .withCookie("session_cookie", equalTo(cookie))
                .withQueryParam("access_token", equalTo(query))
                .withRequestBody(equalToJson(BODY, true, true)));
            verify(postRequestedFor(urlPathEqualTo("/assertion-error"))
                .withHeader("Authorization", equalTo("Bearer " + bearer))
                .withHeader("X-Api-Key", equalTo(apiKey))
                .withCookie("session_cookie", equalTo(cookie))
                .withQueryParam("access_token", equalTo(query))
                .withRequestBody(equalToJson(BODY, true, true)));
            verify(postRequestedFor(urlPathEqualTo("/transport-error"))
                .withHeader("Authorization", equalTo("Bearer " + bearer))
                .withHeader("X-Api-Key", equalTo(apiKey))
                .withCookie("session_cookie", equalTo(cookie))
                .withQueryParam("access_token", equalTo(query))
                .withRequestBody(equalToJson(transportBody, true, true)));
        } catch (AssertionError ignored) {
            throw new AssertionError("WireMock did not receive the original synthetic request values");
        }
    }

    private static Response post(
        ProbeClient client,
        String baseUri,
        String path,
        String body,
        Consumer<RequestSpecification> configure
    ) {
        RequestSpecification request = RestAssured.given()
            .spec(client.requestSpec(baseUri))
            .body(body);
        configure.accept(request);
        return request.post(path);
    }

    private static String contentLength(String body) {
        return Integer.toString(body.getBytes(StandardCharsets.UTF_8).length);
    }

    private static String userInfoUri(String baseUri, String user, String password) {
        return baseUri.replace("http://", "http://" + user + ":" + password + "@");
    }

    private static Set<Path> attachmentFiles() throws IOException {
        Set<Path> attachments = new HashSet<>();
        String configured = System.getProperty("allure.results.directory", "allure-results");
        for (Path directory : List.of(Path.of(configured), Path.of("allure-results"), Path.of("build/allure-results"))) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-attachment.txt"))
                    .map(Path::toAbsolutePath)
                    .forEach(attachments::add);
            }
        }
        return attachments;
    }

    private static long fileSizeIfPresent(Path path) throws IOException {
        return Files.exists(path) ? Files.size(path) : 0;
    }

    private static String readAppendedText(Path path, long start) throws IOException {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(Math.min(start, file.length()));
            ByteArrayOutputStream appended = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = file.read(buffer)) >= 0) {
                appended.write(buffer, 0, count);
            }
            return appended.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class ProbeClient extends BaseApiClient {
        private ProbeClient(String baseUri) {
            super(new FrameworkConfig("test", baseUri, baseUri, "", "", "", 5, 1_000, 1, 1, 10, 0));
        }

        private RequestSpecification requestSpec(String baseUri) {
            return getRequestSpec(baseUri);
        }
    }
}
