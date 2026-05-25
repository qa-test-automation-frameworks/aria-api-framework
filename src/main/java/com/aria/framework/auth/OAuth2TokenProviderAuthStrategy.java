package com.aria.framework.auth;

import java.util.function.Supplier;

/**
 * Named OAuth2-style bearer-token strategy for clients that obtain tokens from a provider.
 */
public final class OAuth2TokenProviderAuthStrategy extends BearerTokenAuthStrategy {

    public OAuth2TokenProviderAuthStrategy(Supplier<String> accessTokenSupplier) {
        super(accessTokenSupplier);
    }
}
