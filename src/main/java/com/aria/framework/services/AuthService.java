package com.aria.framework.services;

import com.aria.framework.clients.AuthApiClient;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.models.request.AuthRequest;
import com.aria.framework.models.response.AuthResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.restassured.response.Response;

/**
 * Service Object layer wrapping authentication actions.
 * Separates API request invocation from test assertion logic.
 */
public class AuthService implements AuthOperations {

    private final AuthApiClient authApiClient;

    public AuthService() {
        this(new AuthApiClient());
    }

    public AuthService(FrameworkConfig config) {
        this(new AuthApiClient(config));
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Constructor injection intentionally stores a stateless API client adapter.")
    public AuthService(AuthApiClient authApiClient) {
        this.authApiClient = authApiClient;
    }

    /**
     * Authenticates credentials and returns the raw RestAssured Response.
     * Useful for performing negative assertions, response times, or custom status checks.
     *
     * @param request AuthRequest credentials DTO
     * @return Response RestAssured Response
     */
    public Response authenticateRaw(AuthRequest request) {
        return authApiClient.authenticate(request);
    }

    /**
     * Authenticates credentials and extracts parsed DTO records.
     *
     * @param request AuthRequest credentials DTO
     * @return AuthResponse parsed response record containing the token
     */
    public AuthResponse authenticate(AuthRequest request) {
        return authenticateRaw(request).as(AuthResponse.class);
    }
}
