# ADR-004: Contract And OpenAPI Quality Gates

## Status

Accepted

## Context

Schema checks in endpoint tests are useful, but they do not prove every documented endpoint has test coverage. Contract drift should fail fast.

## Decision

ARIA uses JSON Schema assertions, Pact consumer contracts, owned-provider verification, and OpenAPI endpoint coverage mapping. `check` fails when endpoints are unmapped, mapped tests are renamed, path parameters are missing, or request/response schemas are incomplete.

## Consequences

Contract coverage becomes visible and enforceable. Adding an endpoint requires updating both executable tests and OpenAPI coverage metadata.
