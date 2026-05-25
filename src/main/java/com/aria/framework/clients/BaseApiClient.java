package com.aria.framework.clients;

import com.aria.framework.config.ConfigManager;
import com.aria.framework.config.FrameworkConfig;
import com.aria.framework.reporting.FailureDiagnosticFilter;
import com.aria.framework.reporting.SanitizedAllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.Filter;
import io.restassured.filter.log.LogDetail;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Base Client class responsible for creating and configuring RestAssured RequestSpecifications.
 * Implements centralized logging, headers, and filter mechanisms.
 */
public abstract class BaseApiClient {

    private static final Logger log = LoggerFactory.getLogger(BaseApiClient.class);
    protected final FrameworkConfig config;
    private final List<Filter> clientFilters = new ArrayList<>();

    protected BaseApiClient() {
        this(ConfigManager.defaults().getFrameworkConfig());
    }

    protected BaseApiClient(FrameworkConfig config) {
        this.config = config;
    }

    public FrameworkConfig config() {
        return config;
    }

    /**
     * Builds a RequestSpecification dynamically for the target microservice/API.
     * Sets Content-Type and Accept headers to JSON by default.
     * Configures logging to only output full request and response streams on assertion failure.
     *
     * @param baseUri String API domain base URI
     * @return RequestSpecification fully constructed spec
     */
    protected RequestSpecification getRequestSpec(String baseUri) {
        int timeoutMs = config.timeoutSeconds() * 1000;
        RestAssuredConfig config = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", timeoutMs)
                .setParam("http.socket.timeout", timeoutMs))
            .logConfig(LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL));

        RequestSpecBuilder builder = new RequestSpecBuilder()
            .setBaseUri(baseUri)
            .setContentType(ContentType.JSON)
            .addHeader("Accept", "application/json")
            .setConfig(config)
            .addFilter(new SanitizedAllureRestAssured())
            .addFilter(new FailureDiagnosticFilter())
            .addFilter(new RequestLoggingFilter(LogDetail.METHOD))
            .addFilter(new RequestLoggingFilter(LogDetail.URI))
            .addFilter(new ResponseLoggingFilter(LogDetail.STATUS));

        // Inject all registered client filters
        for (Filter filter : clientFilters) {
            builder.addFilter(filter);
        }

        return builder.build();
    }

    /**
     * Adds a dynamic RestAssured Filter (e.g. Auth filter, Allure filter, Custom logger).
     *
     * @param filter RestAssured Filter interface
     */
    public void addFilter(Filter filter) {
        if (filter != null) {
            this.clientFilters.add(filter);
            log.debug("Successfully added filter: {}", filter.getClass().getSimpleName());
        }
    }

    /**
     * Clear all custom filters registered in this client.
     */
    public void clearFilters() {
        this.clientFilters.clear();
        log.debug("All registered filters cleared.");
    }
}
