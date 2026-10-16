# Contract Strategy

ARIA uses three contract layers:

1. JSON Schema assertions validate response shape in live endpoint tests.
2. Pact consumer tests define deterministic provider expectations for core flows.
3. The owned in-memory provider fixture verifies provider behavior for default-CI endpoint coverage and provider-state style contract checks.
4. OpenAPI coverage mapping verifies every endpoint in the checked-in API subsets has mapped default-CI tests and complete request/response contracts.

Response DTOs for public third-party APIs intentionally tolerate unknown fields where the provider may add fields without a breaking change. GitHub response schemas are therefore permissive by policy and should not use `additionalProperties: false` unless a field subset is explicitly owned by ARIA.

The Restful Booker response schema follows that rule: required consumer fields remain explicit, while additive unknown properties are accepted. [`BookingCompatibilityExperimentTest`](../src/test/java/com/aria/framework/contracts/BookingCompatibilityExperimentTest.java) proves an additive response is accepted and removal of required `bookingdates` is rejected against the independently maintained schema. See the [compatibility experiment](compatibility-experiment.md) for rollout and rollback sequencing.

For owned APIs, use strict JSON Schema (`additionalProperties: false`), strict response DTOs, and avoid `@JsonIgnoreProperties(ignoreUnknown = true)` unless the API contract explicitly allows additive fields.

Current third-party Pact tests are consumer-side checks and are published as CI artifacts. ARIA also includes `OwnedProviderPactVerificationTest`, which starts the owned fixture and verifies the provider states represented by the core Restful Booker consumer contracts. For a deployed owned provider, add a provider-verification job that downloads those artifacts or broker pacts, starts the real provider, and runs the Pact verifier before deployment.
