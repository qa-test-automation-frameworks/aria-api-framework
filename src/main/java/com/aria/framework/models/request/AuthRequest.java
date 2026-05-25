package com.aria.framework.models.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload class for authenticating with RestfulBooker.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    private String username;
    private String password;

    @Override
    public String toString() {
        return "AuthRequest(username=" + username + ", password=<redacted>)";
    }
}
