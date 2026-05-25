package com.aria.framework.config;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.Config.Sources;

/**
 * Configuration interface utilizing Aeonbits Owner library to map properties,
 * system environment variables, and system properties into a strongly typed interface.
 */
@Sources({
    "system:properties",
    "system:env",
    "file:config/${env}.properties",
    "classpath:config/${env}.properties"
})
public interface EnvironmentConfig extends Config {

    @Key("base.url")
    @DefaultValue("https://restful-booker.herokuapp.com")
    String baseUrl();

    @Key("github.base.url")
    @DefaultValue("https://api.github.com")
    String githubBaseUrl();

    @Key("github.token")
    @DefaultValue("")
    String githubToken();

    @Key("booker.username")
    @DefaultValue("")
    String bookerUsername();

    @Key("booker.password")
    @DefaultValue("")
    String bookerPassword();

    @Key("timeout.seconds")
    @DefaultValue("30")
    int timeoutSeconds();

    @Key("sla.responseTimeMs")
    @DefaultValue("3000")
    long responseTimeSlaMs();

    @Key("retry.maxAttempts")
    @DefaultValue("3")
    int retryMaxAttempts();

    @Key("retry.baseDelayMs")
    @DefaultValue("1000")
    long retryBaseDelayMs();

    @Key("retry.maxDelayMs")
    @DefaultValue("8000")
    long retryMaxDelayMs();

    @Key("retry.jitterMs")
    @DefaultValue("250")
    long retryJitterMs();
}
