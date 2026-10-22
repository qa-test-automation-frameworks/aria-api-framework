package com.aria.framework.contracts;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies ARIA's generated consumer contract against the running pinned APP image. */
@Provider("quality-reference-application")
@PactFolder("build/pacts")
@Tag("shared-app-pact-provider")
@Execution(ExecutionMode.SAME_THREAD)
class ReferenceApplicationPactVerificationTest {

    @BeforeEach
    void targetRunningApplication(PactVerificationContext context) {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("APP_BASE_URL must point to the pinned reference application.");
        }
        URI uri = URI.create(baseUrl);
        String basePath = uri.getPath().isBlank() ? "/" : uri.getPath();
        context.setTarget(new HttpTestTarget(uri.getHost(), uri.getPort(), basePath));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifiesAriaConsumerPactAgainstReferenceApplication(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("a member can list version 2 policies")
    Map<String, Object> memberCanListV2Policies() {
        return memberAndPolicy(2);
    }

    @State("a member and policy exist for a version 2 claim")
    Map<String, Object> memberAndPolicyForV2Claim() {
        return memberAndPolicy(2);
    }

    @State("a member owns a submitted version 2 claim")
    Map<String, Object> memberOwnsSubmittedV2Claim() {
        String token = issueMemberSession();
        String policyId = policyId(token, 2);
        Response created = authorizedRequest(token)
            .contentType("application/json")
            .body(Map.of("policyId", policyId, "amountMinor", 250_000, "currency", "USD", "category", "auto"))
            .post("/api/v2/claims");
        assertThat(created.statusCode()).as("provider-state claim creation").isEqualTo(201);
        String claimId = created.jsonPath().getString("claim.id");
        assertThat(claimId).matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

        Response events = authorizedRequest(token).get("/api/v2/claims/" + claimId + "/events");
        assertThat(events.statusCode()).as("provider-state initial event read").isEqualTo(200);
        assertThat(events.jsonPath().getList("items")).hasSize(1);
        assertThat((Object) events.jsonPath().get("items[0].fromStatus")).isNull();
        assertThat(events.jsonPath().getString("items[0].toStatus")).isEqualTo("submitted");
        assertThat(events.jsonPath().getInt("items[0].claimVersion")).isEqualTo(1);
        return Map.of("token", token, "policyId", policyId, "claimId", claimId);
    }

    @State("a member and policy exist for a version 1 boundary claim")
    Map<String, Object> memberAndPolicyForV1BoundaryClaim() {
        return memberAndPolicy(1);
    }

    private static Map<String, Object> memberAndPolicy(int apiVersion) {
        String token = issueMemberSession();
        return Map.of("token", token, "policyId", policyId(token, apiVersion));
    }

    private static String issueMemberSession() {
        Response response = RestAssured.given()
            .baseUri(applicationBaseUrl())
            .contentType("application/json")
            .accept("application/json")
            .body(Map.of("identityKey", "member-a"))
            .post("/api/v1/sessions");
        assertThat(response.statusCode()).as("provider-state synthetic session creation").isEqualTo(201);
        String token = response.jsonPath().getString("token");
        assertThat(token).isNotBlank();
        return token;
    }

    private static String policyId(String token, int apiVersion) {
        Response response = authorizedRequest(token).get("/api/v" + apiVersion + "/policies");
        assertThat(response.statusCode()).as("provider-state policy lookup").isEqualTo(200);
        assertThat(response.jsonPath().getList("items")).isNotEmpty();
        String policyId = response.jsonPath().getString("items[0].id");
        assertThat(policyId).matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");
        return policyId;
    }

    private static RequestSpecification authorizedRequest(String token) {
        return RestAssured.given()
            .baseUri(applicationBaseUrl())
            .accept("application/json")
            .auth().oauth2(token);
    }

    private static String applicationBaseUrl() {
        String baseUrl = System.getenv("APP_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("APP_BASE_URL must point to the pinned reference application.");
        }
        return baseUrl;
    }
}
