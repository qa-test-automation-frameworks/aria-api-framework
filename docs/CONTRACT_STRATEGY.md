# Contract Strategy

ARIA uses four contract layers:

1. JSON Schema assertions validate response shape in live endpoint tests.
2. Pact consumer tests (`BookingConsumerPactTest`, `GithubConsumerPactTest`) define deterministic provider expectations for core flows, and `OwnedProviderPactVerificationTest` replays the generated pacts through the real Pact provider verifier against the owned fixture.
3. `OwnedProviderStateContractTest` verifies provider-state-style behavior (mass-assignment rejection, ownership checks, role/expiry handling) that sits outside what a Pact interaction expresses.
4. `OpenApiRuntimeValidationTest` validates every documented operation's live response against its OpenAPI schema (status, media type, and full body-schema conformance via `swagger-request-validator`), and OpenAPI coverage mapping verifies every endpoint in the checked-in API subsets has mapped default-CI tests and complete request/response contracts.

Response DTOs for public third-party APIs intentionally tolerate unknown fields where the provider may add fields without a breaking change. GitHub response schemas are therefore permissive by policy and should not use `additionalProperties: false` unless a field subset is explicitly owned by ARIA.

For owned APIs, use strict JSON Schema (`additionalProperties: false`), strict response DTOs, and avoid `@JsonIgnoreProperties(ignoreUnknown = true)` unless the API contract explicitly allows additive fields.

Pact provider verification runs against the owned in-JVM fixture by default, so it is part of the deterministic gate rather than a live, deployed-provider check. For a deployed owned provider, add a provider-verification job that downloads the pact artifacts or broker pacts, starts the real provider, and runs the Pact verifier before deployment.
