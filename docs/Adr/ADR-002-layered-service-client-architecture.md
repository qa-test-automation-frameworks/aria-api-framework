# ADR-002: Layered Service And Client Architecture

## Status

Accepted

## Context

Raw HTTP calls scattered through tests make large API suites hard to maintain. The framework needs predictable extension points for new API domains.

## Decision

Tests call service objects. Services orchestrate API clients. API clients own endpoint mappings, RestAssured request specs, auth strategies, retries, timeouts, and diagnostics.

## Consequences

New endpoints can be added by following an established path: config, client, service, DTOs, data factory, schemas, OpenAPI coverage, and tests. Service interfaces document the expected behavior surface without forcing tests to use raw HTTP details.
