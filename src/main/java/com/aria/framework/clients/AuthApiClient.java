package com.aria.framework.clients;

import com.aria.framework.config.ConfigManager;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.models.request.AuthRequest;
import com.aria.framework.utils.JsonUtils;
import com.aria.framework.utils.RetryUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * REST Client mapping restful-booker /auth API endpoint actions.
 */
public class AuthApiClient extends BaseApiClient {

    private final String baseUrl;

    public AuthApiClient() {
        this(ConfigManager.defaults().getFrameworkConfig());
    }

    public AuthApiClient(ConfigManager configManager) {
        this(configManager.getFrameworkConfig());
    }

    public AuthApiClient(FrameworkConfig config) {
        super(config);
        this.baseUrl = config.baseUrl();
    }

    /**
     * Sends POST /auth to generate a RestfulBooker session token.
     *
     * @param request AuthRequest credentials DTO
     * @return Response raw API Response
     */
    public Response authenticate(AuthRequest request) {
        return RetryUtils.executeWithoutRetry(() -> RestAssured.given()
            .spec(getRequestSpec(baseUrl))
            .body(JsonUtils.serialize(request))
            .post("/auth"));
    }
}
