# ADR-001: Deterministic Default Execution

## Status

Accepted

## Context

Public APIs introduce network, rate-limit, credential, and data volatility. The default test command should be trustworthy in local development and pull requests.

## Decision

The default `test` task excludes `live` tests and runs deterministic coverage through owned-provider, WireMock, Pact, config, security-boundary, and OpenAPI checks. Live tests run only through explicit live Gradle tasks.

## Consequences

Developers get repeatable feedback by default. Public API coverage still exists, but it is opt-in and separated from the main quality gate.
