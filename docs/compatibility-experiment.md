# Booking API compatibility experiment

This deterministic experiment uses the repository-owned `OwnedApiProvider` and the separately maintained consumer schema at `src/test/resources/schemas/booking-schema.json`. The provider emits two candidate revisions for `GET /booking/1`; the consumer contract remains fixed.

| Candidate | Provider change | Expected consumer result |
| --- | --- | --- |
| Compatible additive revision | Adds the optional top-level `contractrevision` field while preserving all documented booking fields. | The JSON Schema accepts the response, Jackson maps the known consumer fields, and the consumer observes its expected booking values. |
| Breaking revision | Removes required `bookingdates`. | The JSON Schema rejects the response and identifies the missing required field. |

The consumer schema intentionally permits unknown fields for this external provider contract, while continuing to require the fields the consumer relies on. This aligns the schema with `BookingResponse`, which ignores unknown fields. It avoids confusing a harmless provider addition with a breaking change. Owned application APIs continue to use strict schemas where ARIA owns both sides.

## Rollout and rollback

The safe sequence is consumer-first: deploy consumers that tolerate the optional additive field, then add the provider field. For a breaking change, publish a new versioned endpoint or response contract, migrate consumers, verify the new provider contract, and retire the old version only after its consumers have moved. If the new provider revision fails compatibility verification, roll the provider back while preserving the old response shape; do not patch the consumer expectation to match an unreviewed response.

The fixture experiment demonstrates contract detection and the supported compatibility rule. It does not prove a production provider migration or broker-based coordination. Pact consumer/provider checks and the existing retry tests remain separate: `RetryUtilsTest`, `RetryDelaySafetyTest`, and `RetryTransportDeadlineTest` cover rate limits, deadline bounds, interruption, and safe idempotent retry behavior. Non-idempotent mutations remain single-attempt without explicit idempotency control.
