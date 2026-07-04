package com.aria.framework.contracts;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.aria.framework.fixtures.OwnedApiProvider;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.URI;

/**
 * Pact provider verification for consumer pacts generated under build/pacts.
 */
@Epic("Contracts")
@Feature("Pact provider verification")
@Provider("restful-booker")
@PactFolder("build/pacts")
@Tag("pact-provider")
@Execution(ExecutionMode.SAME_THREAD)
class OwnedProviderPactVerificationTest {

    private static OwnedApiProvider provider;

    @BeforeAll
    static void startProvider() {
        provider = OwnedApiProvider.start();
    }

    @AfterAll
    static void stopProvider() {
        if (provider != null) {
            provider.close();
        }
    }

    @BeforeEach
    void before(PactVerificationContext context) {
        provider.resetState();
        int port = URI.create(provider.baseUrl()).getPort();
        context.setTarget(new HttpTestTarget("127.0.0.1", port, "/"));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifiesGeneratedConsumerPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("booking 1 exists")
    void bookingOneExists() {
        provider.resetState();
    }

    @State("booking 999 does not exist")
    void booking999DoesNotExist() {
        provider.resetState();
    }
}
