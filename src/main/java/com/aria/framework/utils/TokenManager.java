package com.aria.framework.utils;

import com.aria.framework.clients.AuthApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.exceptions.AuthenticationException;
import com.aria.framework.models.request.AuthRequest;
import com.aria.framework.reporting.RedactionPolicy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe, configuration-scoped cache for RestfulBooker auth tokens.
 */
public final class TokenManager {

    private static final Logger log = LoggerFactory.getLogger(TokenManager.class);
    private static final long DEFAULT_TOKEN_TTL_MS = 9 * 60 * 1000L;

    private final AtomicReference<String> restfulBookerToken = new AtomicReference<>();
    private final AtomicReference<Long> restfulBookerTokenExpiryEpochMs = new AtomicReference<>(0L);
    private final FrameworkConfig config;
    private final AuthApiClient authApiClient;

    public TokenManager(FrameworkConfig config) {
        this(config, new AuthApiClient(config));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Constructor injection intentionally stores a stateless API client adapter.")
    public TokenManager(FrameworkConfig config, AuthApiClient authApiClient) {
        this.config = config;
        this.authApiClient = authApiClient;
    }

    /**
     * Retrieves the cached RestfulBooker auth token, or generates a new one if it is missing.
     * Thread-safe double-checked lock pattern.
     *
     * @return String auth token
     */
    public String getRestfulBookerToken() {
        String token = restfulBookerToken.get();
        if (token == null || isRestfulBookerTokenExpired()) {
            synchronized (this) {
                token = restfulBookerToken.get();
                if (token == null || isRestfulBookerTokenExpired()) {
                    token = generateRestfulBookerToken();
                    restfulBookerToken.set(token);
                    restfulBookerTokenExpiryEpochMs.set(System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS);
                }
            }
        }
        return token;
    }

    /**
     * Forces a refresh of the RestfulBooker auth token.
     */
    public void forceRefreshToken() {
        synchronized (this) {
            String token = generateRestfulBookerToken();
            restfulBookerToken.set(token);
            restfulBookerTokenExpiryEpochMs.set(System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS);
        }
    }

    /**
     * Method to fetch a new token from the RestfulBooker /auth endpoint.
     */
    private String generateRestfulBookerToken() {
        log.info("Generating a fresh RestfulBooker authentication token...");

        AuthRequest credentials = AuthRequest.builder()
            .username(config.bookerUsername())
            .password(config.bookerPassword())
            .build();

        Response response = authApiClient.authenticate(credentials);

        if (response.getStatusCode() != 200) {
            log.error("Failed to generate RestfulBooker auth token. HTTP Status Code: {}", response.getStatusCode());
            throw new AuthenticationException("Authentication failed with status " + response.getStatusCode());
        }

        String token = response.jsonPath().getString("token");
        if (token == null || token.trim().isEmpty()) {
            log.error(
                "Auth endpoint did not return a token in response body: {}",
                RedactionPolicy.sanitize(response.getBody().asString())
            );
            throw new AuthenticationException("Auth token is missing from RestfulBooker response");
        }

        log.info("RestfulBooker auth token generated successfully.");
        return token;
    }

    private boolean isRestfulBookerTokenExpired() {
        return System.currentTimeMillis() >= restfulBookerTokenExpiryEpochMs.get();
    }
}
