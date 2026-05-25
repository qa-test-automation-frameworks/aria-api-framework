package com.aria.framework.services;

import com.aria.framework.models.request.AuthRequest;
import com.aria.framework.models.response.AuthResponse;
import io.restassured.response.Response;

public interface AuthOperations {

    Response authenticateRaw(AuthRequest request);

    AuthResponse authenticate(AuthRequest request);
}
