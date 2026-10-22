package com.aria.framework.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Consumer-owned contracts for the shared reference application's supported API behavior. */
@ExtendWith(PactConsumerTestExt.class)
@Execution(ExecutionMode.SAME_THREAD)
class ReferenceApplicationConsumerPactTest {

    static final String POLICY_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    static final String EXAMPLE_TOKEN = "consumer-example-token";
    static final String TOKEN_EXPRESSION = "Bearer ${token}";
    static final String POLICY_ID_EXPRESSION = "${policyId}";
    static final String CLAIM_ID_EXPRESSION = "/api/v2/claims/${claimId}/events";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    @Pact(consumer = "aria-api-framework", provider = "quality-reference-application")
    V4Pact unauthenticatedPolicyListPact(PactDslWithProvider builder) {
        return builder
            .uponReceiving("an unauthenticated policy list request")
            .path("/api/v1/policies")
            .method("GET")
            .willRespondWith()
            .status(401)
            .headers(Map.of("Content-Type", JSON_CONTENT_TYPE))
            .body(Objects.requireNonNull(
                new PactDslJsonBody().object("error").stringValue("code", "AUTHENTICATION_REQUIRED").closeObject(),
                "The authentication error body must close into a Pact DSL part"))
            .toPact(V4Pact.class);
    }

    @Pact(consumer = "aria-api-framework", provider = "quality-reference-application")
    V4Pact v2PolicyListPact(PactDslWithProvider builder) {
        return builder
            .given("a member can list version 2 policies")
            .uponReceiving("an authenticated v2 policy list")
            .path("/api/v2/policies")
            .method("GET")
            .headers(Map.of("Accept", "application/json"))
            .headerFromProviderState("Authorization", TOKEN_EXPRESSION, "Bearer " + EXAMPLE_TOKEN)
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", JSON_CONTENT_TYPE))
            .body(policyListBody())
            .toPact(V4Pact.class);
    }

    @Pact(consumer = "aria-api-framework", provider = "quality-reference-application")
    V4Pact createV2ClaimPact(PactDslWithProvider builder) {
        return builder
            .given("a member and policy exist for a version 2 claim")
            .uponReceiving("a successful v2 claim creation")
            .path("/api/v2/claims")
            .method("POST")
            .headers(Map.of("Accept", "application/json", "Content-Type", "application/json"))
            .headerFromProviderState("Authorization", TOKEN_EXPRESSION, "Bearer " + EXAMPLE_TOKEN)
            .body(new PactDslJsonBody()
                .valueFromProviderState("policyId", POLICY_ID_EXPRESSION, POLICY_ID)
                .numberValue("amountMinor", 250_000)
                .stringValue("currency", "USD")
                .stringValue("category", "auto"))
            .willRespondWith()
            .status(201)
            .headers(Map.of("Content-Type", JSON_CONTENT_TYPE))
            .body(claimResponseBody(250_000))
            .toPact(V4Pact.class);
    }

    @Pact(consumer = "aria-api-framework", provider = "quality-reference-application")
    V4Pact initialV2ClaimEventPact(PactDslWithProvider builder) {
        DslPart eventItems = new PactDslJsonBody()
            .minMaxArrayLike("items", 1, 1)
            .nullValue("fromStatus")
            .stringValue("toStatus", "submitted")
            .numberValue("claimVersion", 1)
            .stringType("createdAt")
            .stringValue("actorDisplayName", "Synthetic Member A")
            .stringValue("actorRole", "member")
            .closeObject();
        DslPart eventBody = Objects.requireNonNull(
            eventItems, "The claim event array must close into its parent object").closeArray();
        if (eventBody == null) {
            throw new IllegalStateException("The claim event body must close into a Pact DSL part");
        }

        return builder
            .given("a member owns a submitted version 2 claim")
            .uponReceiving("the initial event for a v2 claim")
            .pathFromProviderState(CLAIM_ID_EXPRESSION, "/api/v2/claims/cccccccc-cccc-4ccc-8ccc-cccccccccccc/events")
            .method("GET")
            .headers(Map.of("Accept", "application/json"))
            .headerFromProviderState("Authorization", TOKEN_EXPRESSION, "Bearer " + EXAMPLE_TOKEN)
            .willRespondWith()
            .status(200)
            .headers(Map.of("Content-Type", JSON_CONTENT_TYPE))
            .body(eventBody)
            .toPact(V4Pact.class);
    }

    @Pact(consumer = "aria-api-framework", provider = "quality-reference-application")
    V4Pact v1BoundaryClaimPact(PactDslWithProvider builder) {
        return builder
            .given("a member and policy exist for a version 1 boundary claim")
            .uponReceiving("a v1 claim at the one million minor-unit limit")
            .path("/api/v1/claims")
            .method("POST")
            .headers(Map.of("Accept", "application/json", "Content-Type", "application/json"))
            .headerFromProviderState("Authorization", TOKEN_EXPRESSION, "Bearer " + EXAMPLE_TOKEN)
            .body(new PactDslJsonBody()
                .valueFromProviderState("policyId", POLICY_ID_EXPRESSION, POLICY_ID)
                .numberValue("amountMinor", 1_000_000)
                .stringValue("currency", "USD")
                .stringValue("category", "auto"))
            .willRespondWith()
            .status(201)
            .headers(Map.of("Content-Type", JSON_CONTENT_TYPE))
            .body(claimResponseBody(1_000_000))
            .toPact(V4Pact.class);
    }

    @Test
    @Tag("contract")
    @DisplayName("Unauthenticated policy access satisfies the shared APP consumer contract")
    @PactTestFor(pactMethod = "unauthenticatedPolicyListPact")
    void unauthenticatedPolicyListMatchesContract(MockServer mockServer) {
        var response = RestAssured.given()
            .baseUri(mockServer.getUrl())
            .accept("application/json")
            .get("/api/v1/policies");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.jsonPath().getString("error.code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    @Tag("contract")
    @DisplayName("Version 2 policy listing returns its integer claim limit")
    @PactTestFor(pactMethod = "v2PolicyListPact")
    void v2PolicyListMatchesContract(MockServer mockServer) {
        Response response = authorizedRequest(mockServer, EXAMPLE_TOKEN)
            .get("/api/v2/policies");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("items")).hasSize(1);
        assertThat(response.jsonPath().getString("items[0].id")).isEqualTo(POLICY_ID);
        assertThat(response.jsonPath().getString("items[0].policyNumber")).isEqualTo("POL-SYN-A-001");
        assertThat(response.jsonPath().getInt("items[0].claimAmountLimitMinor")).isEqualTo(500_000);
    }

    @Test
    @Tag("contract")
    @DisplayName("Version 2 claim creation returns its persisted fields")
    @PactTestFor(pactMethod = "createV2ClaimPact")
    void createV2ClaimMatchesContract(MockServer mockServer) {
        Response response = authorizedRequest(mockServer, EXAMPLE_TOKEN)
            .contentType("application/json")
            .body(Map.of("policyId", POLICY_ID, "amountMinor", 250_000, "currency", "USD", "category", "auto"))
            .post("/api/v2/claims");

        assertThat(response.statusCode()).isEqualTo(201);
        String claimId = response.jsonPath().getString("claim.id");
        assertThat(UUID.fromString(claimId)).isNotNull();
        assertThat(response.jsonPath().getString("claim.policyId")).isEqualTo(POLICY_ID);
        assertThat(response.jsonPath().getString("claim.status")).isEqualTo("submitted");
        assertThat(response.jsonPath().getInt("claim.amountMinor")).isEqualTo(250_000);
        assertThat(response.jsonPath().getInt("claim.version")).isEqualTo(1);
    }

    @Test
    @Tag("contract")
    @DisplayName("The created version 2 claim exposes its initial submitted event")
    @PactTestFor(pactMethod = "initialV2ClaimEventPact")
    void initialV2ClaimEventMatchesContract(MockServer mockServer) {
        Response response = authorizedRequest(mockServer, EXAMPLE_TOKEN)
            .get("/api/v2/claims/cccccccc-cccc-4ccc-8ccc-cccccccccccc/events");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getList("items")).hasSize(1);
        assertThat((Object) response.jsonPath().get("items[0].fromStatus")).isNull();
        assertThat(response.jsonPath().getString("items[0].toStatus")).isEqualTo("submitted");
        assertThat(response.jsonPath().getInt("items[0].claimVersion")).isEqualTo(1);
    }

    @Test
    @Tag("contract")
    @DisplayName("Version 1 still accepts the one million minor-unit boundary")
    @PactTestFor(pactMethod = "v1BoundaryClaimPact")
    void v1BoundaryClaimMatchesContract(MockServer mockServer) {
        Response response = authorizedRequest(mockServer, EXAMPLE_TOKEN)
            .contentType("application/json")
            .body(Map.of("policyId", POLICY_ID, "amountMinor", 1_000_000, "currency", "USD", "category", "auto"))
            .post("/api/v1/claims");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.jsonPath().getInt("claim.amountMinor")).isEqualTo(1_000_000);
        assertThat(response.jsonPath().getString("claim.status")).isEqualTo("submitted");
        assertThat(response.jsonPath().getInt("claim.version")).isEqualTo(1);
    }

    private static DslPart policyListBody() {
        DslPart policyItems = new PactDslJsonBody()
            .minMaxArrayLike("items", 1, 1)
            .valueFromProviderState("id", POLICY_ID_EXPRESSION, POLICY_ID)
            .stringValue("policyNumber", "POL-SYN-A-001")
            .stringValue("seedVersion", "v0.1.0")
            .numberValue("claimAmountLimitMinor", 500_000)
            .closeObject();
        DslPart policyBody = Objects.requireNonNull(
            policyItems, "The policy array must close into its parent object").closeArray();
        if (policyBody == null) {
            throw new IllegalStateException("The policy list body must close into a Pact DSL part");
        }
        return policyBody;
    }

    private static DslPart claimResponseBody(int amountMinor) {
        return new PactDslJsonBody()
            .object("claim")
            .uuid("id", "cccccccc-cccc-4ccc-8ccc-cccccccccccc")
            .valueFromProviderState("policyId", POLICY_ID_EXPRESSION, POLICY_ID)
            .stringValue("status", "submitted")
            .numberValue("amountMinor", amountMinor)
            .stringValue("currency", "USD")
            .stringValue("category", "auto")
            .numberValue("version", 1)
            .stringType("createdAt")
            .stringType("updatedAt")
            .closeObject();
    }

    private static io.restassured.specification.RequestSpecification authorizedRequest(MockServer mockServer, String token) {
        return RestAssured.given()
            .baseUri(mockServer.getUrl())
            .accept("application/json")
            .auth().oauth2(token);
    }
}
