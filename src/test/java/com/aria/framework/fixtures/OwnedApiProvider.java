package com.aria.framework.fixtures;

import com.aria.framework.config.FrameworkConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight owned API fixture used to verify framework behavior without public API dependencies.
 */
public final class OwnedApiProvider implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOOKER_TOKEN = "mock-token";
    private static final String GITHUB_TOKEN = "mock-github-token";
    private static final Set<String> MASS_ASSIGNMENT_FIELDS = Set.of("role", "isAdmin", "ownerId", "internalToken");
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final HttpServer server;
    private final ExecutorService executorService;
    private final Map<Integer, Map<String, Object>> bookings = new LinkedHashMap<>();
    private final Map<Integer, String> bookingOwners = new HashMap<>();
    private final AtomicInteger nextBookingId = new AtomicInteger(2);

    private OwnedApiProvider(HttpServer server, ExecutorService executorService) {
        this.server = server;
        this.executorService = executorService;
        resetState();
    }

    public void resetState() {
        bookings.clear();
        bookingOwners.clear();
        bookings.put(1, booking("Jim", "Brown"));
        bookingOwners.put(1, "user-1");
        nextBookingId.set(2);
    }

    public static OwnedApiProvider start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executorService = Executors.newCachedThreadPool();
            OwnedApiProvider provider = new OwnedApiProvider(server, executorService);
            server.createContext("/", provider::handle);
            server.setExecutor(executorService);
            server.start();
            return provider;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start owned API provider fixture", exception);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public FrameworkConfig frameworkConfig() {
        return new FrameworkConfig(
            "owned-provider",
            baseUrl(),
            baseUrl(),
            GITHUB_TOKEN,
            "admin",
            "mock-booker-password",
            5,
            1_000,
            1,
            10,
            50,
            0
        );
    }

    @Override
    public void close() {
        server.stop(0);
        executorService.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (RuntimeException exception) {
            sendJson(exchange, 500, Map.of("error", "owned provider fixture failure"));
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method) && "/ping".equals(path)) {
            sendText(exchange, 201, "Created");
            return;
        }
        if ("POST".equals(method) && "/auth".equals(path)) {
            handleAuth(exchange);
            return;
        }
        if ("/booking".equals(path)) {
            handleBookingCollection(exchange, method);
            return;
        }
        if (path.startsWith("/booking/")) {
            handleBookingResource(exchange, method, parseId(path, "/booking/"));
            return;
        }
        if ("GET".equals(method) && "/users/octocat".equals(path)) {
            sendJson(exchange, 200, githubUser());
            return;
        }
        if ("GET".equals(method) && "/repos/octocat/Hello-World".equals(path)) {
            sendJson(exchange, 200, githubRepository());
            return;
        }
        if ("/repos/octocat/Hello-World/issues".equals(path)) {
            handleGithubIssues(exchange, method);
            return;
        }
        if ("PATCH".equals(method) && "/repos/octocat/Hello-World/issues/1".equals(path)) {
            if (!hasGithubToken(exchange)) {
                sendJson(exchange, 401, Map.of("message", "Bad credentials"));
                return;
            }
            Map<String, Object> issue = githubIssue("closed");
            sendJson(exchange, 200, issue);
            return;
        }
        if ("GET".equals(method) && "/search/repositories".equals(path)) {
            sendJson(exchange, 200, Map.of(
                "total_count", 1,
                "incomplete_results", false,
                "items", List.of(githubRepository())
            ));
            return;
        }
        if ("GET".equals(method) && "/rate_limit".equals(path)) {
            sendJson(exchange, 200, githubRateLimit());
            return;
        }
        sendJson(exchange, 404, Map.of("message", "Not Found"));
    }

    private void handleAuth(HttpExchange exchange) throws IOException {
        Map<String, Object> payload = readJsonObject(exchange);
        if ("admin".equals(payload.get("username")) && "mock-booker-password".equals(payload.get("password"))) {
            sendJson(exchange, 200, Map.of("token", BOOKER_TOKEN));
            return;
        }
        sendJson(exchange, 200, Map.of("reason", "Bad credentials"));
    }

    private void handleBookingCollection(HttpExchange exchange, String method) throws IOException {
        if ("OPTIONS".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendBytes(exchange, 204, new byte[0]);
            return;
        }
        if ("GET".equals(method)) {
            List<Map<String, Integer>> ids = bookings.keySet().stream()
                .sorted()
                .map(id -> Map.of("bookingid", id))
                .toList();
            sendJson(exchange, 200, ids);
            return;
        }
        if ("POST".equals(method)) {
            Map<String, Object> payload = readJsonObject(exchange);
            if (payload.keySet().stream().anyMatch(MASS_ASSIGNMENT_FIELDS::contains)) {
                sendJson(exchange, 400, Map.of("error", "mass assignment field rejected"));
                return;
            }
            int bookingId = nextBookingId.getAndIncrement();
            Map<String, Object> booking = normalizedBooking(payload);
            bookings.put(bookingId, booking);
            bookingOwners.put(bookingId, "user-1");
            sendJson(exchange, 200, Map.of("bookingid", bookingId, "booking", booking));
            return;
        }
        sendJson(exchange, 405, Map.of("error", "method not allowed"));
    }

    private void handleBookingResource(HttpExchange exchange, String method, int bookingId) throws IOException {
        if ("GET".equals(method)) {
            if (!canReadBooking(exchange, bookingId)) {
                sendJson(exchange, 403, Map.of("error", "forbidden booking access"));
                return;
            }
            Map<String, Object> booking = bookings.get(bookingId);
            if (booking == null) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            sendJson(exchange, 200, booking);
            return;
        }
        if ("PUT".equals(method)) {
            if (!authorizeBookingMutation(exchange)) {
                return;
            }
            if (!bookings.containsKey(bookingId)) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            Map<String, Object> booking = normalizedBooking(readJsonObject(exchange));
            bookings.put(bookingId, booking);
            sendJson(exchange, 200, booking);
            return;
        }
        if ("PATCH".equals(method)) {
            if (!authorizeBookingMutation(exchange)) {
                return;
            }
            Map<String, Object> booking = bookings.get(bookingId);
            if (booking == null) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            booking.putAll(readJsonObject(exchange));
            sendJson(exchange, 200, booking);
            return;
        }
        if ("DELETE".equals(method)) {
            if (!authorizeBookingMutation(exchange)) {
                return;
            }
            if (bookings.remove(bookingId) == null) {
                sendText(exchange, 404, "Not Found");
                return;
            }
            bookingOwners.remove(bookingId);
            sendText(exchange, 201, "Created");
            return;
        }
        sendJson(exchange, 405, Map.of("error", "method not allowed"));
    }

    private void handleGithubIssues(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            sendJson(exchange, 200, List.of(githubIssue("open")));
            return;
        }
        if ("POST".equals(method)) {
            if (!hasGithubToken(exchange)) {
                sendJson(exchange, 401, Map.of("message", "Bad credentials"));
                return;
            }
            sendJson(exchange, 201, githubIssue("open"));
            return;
        }
        sendJson(exchange, 405, Map.of("message", "Method Not Allowed"));
    }

    private boolean authorizeBookingMutation(HttpExchange exchange) throws IOException {
        String role = firstHeader(exchange, "X-Role");
        if ("viewer".equals(role)) {
            sendJson(exchange, 403, Map.of("error", "insufficient role"));
            return false;
        }
        String cookie = firstHeader(exchange, "Cookie");
        if (cookie != null && cookie.contains("token=expired-token")) {
            sendJson(exchange, 403, Map.of("error", "expired token"));
            return false;
        }
        if (cookie == null || !cookie.contains("token=" + BOOKER_TOKEN)) {
            sendJson(exchange, 403, Map.of("error", "invalid token"));
            return false;
        }
        return true;
    }

    private boolean canReadBooking(HttpExchange exchange, int bookingId) {
        String requestedUser = firstHeader(exchange, "X-User-Id");
        return requestedUser == null || requestedUser.equals(bookingOwners.get(bookingId));
    }

    private boolean hasGithubToken(HttpExchange exchange) {
        String authorization = firstHeader(exchange, "Authorization");
        return ("Bearer " + GITHUB_TOKEN).equals(authorization);
    }

    private static String firstHeader(HttpExchange exchange, String headerName) {
        List<String> values = exchange.getRequestHeaders().get(headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private static int parseId(String path, String prefix) {
        return Integer.parseInt(path.substring(prefix.length()));
    }

    private static Map<String, Object> normalizedBooking(Map<String, Object> payload) {
        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("firstname", payload.getOrDefault("firstname", "Jim"));
        booking.put("lastname", payload.getOrDefault("lastname", "Brown"));
        booking.put("totalprice", payload.getOrDefault("totalprice", 111));
        booking.put("depositpaid", payload.getOrDefault("depositpaid", true));
        booking.put("bookingdates", payload.getOrDefault("bookingdates", Map.of(
            "checkin", "2026-05-24",
            "checkout", "2026-05-31"
        )));
        booking.put("additionalneeds", payload.getOrDefault("additionalneeds", "Breakfast"));
        return booking;
    }

    private static Map<String, Object> booking(String firstname, String lastname) {
        return new LinkedHashMap<>(Map.of(
            "firstname", firstname,
            "lastname", lastname,
            "totalprice", 111,
            "depositpaid", true,
            "bookingdates", Map.of(
                "checkin", "2026-05-24",
                "checkout", "2026-05-31"
            ),
            "additionalneeds", "Breakfast"
        ));
    }

    private static Map<String, Object> githubUser() {
        return Map.of(
            "login", "octocat",
            "id", 583231,
            "type", "User",
            "name", "The Octocat",
            "public_repos", 8
        );
    }

    private static Map<String, Object> githubRepository() {
        Map<String, Object> repository = new LinkedHashMap<>();
        repository.put("id", 1);
        repository.put("name", "Hello-World");
        repository.put("full_name", "octocat/Hello-World");
        repository.put("private", false);
        repository.put("fork", false);
        repository.put("default_branch", "main");
        repository.put("visibility", "public");
        repository.put("description", "Owned provider fixture repo");
        repository.put("stargazers_count", 80);
        repository.put("forks_count", 9);
        repository.put("open_issues_count", 1);
        return repository;
    }

    private static Map<String, Object> githubIssue(String state) {
        return Map.of(
            "id", 1,
            "number", 1,
            "title", "Mock issue",
            "state", state,
            "body", "body",
            "url", "https://api.github.com/repos/octocat/Hello-World/issues/1",
            "labels", List.of(Map.of("name", "automation"))
        );
    }

    private static Map<String, Object> githubRateLimit() {
        Map<String, Object> core = Map.of(
            "limit", 60,
            "remaining", 59,
            "reset", 1_893_456_000,
            "used", 1
        );
        return Map.of(
            "resources", Map.of("core", core, "search", core),
            "rate", core
        );
    }

    private static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(body, JSON_OBJECT);
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendBytes(exchange, status, bytes);
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=ISO-8859-1");
        sendBytes(exchange, status, body.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void sendBytes(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
