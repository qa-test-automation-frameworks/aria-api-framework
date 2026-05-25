# ADR-003: Sanitized Allure Diagnostics

## Status

Accepted

## Context

HTTP diagnostics are essential for triage, but attaching raw requests and responses can leak tokens, cookies, passwords, API keys, and sensitive query parameters.

## Decision

All RestAssured clients attach diagnostics through centralized filters that apply `RedactionPolicy`. Failure-specific diagnostics are attached for 4xx, 5xx, and transport exceptions.

## Consequences

Failures can be debugged from Allure and CI artifacts without exposing secrets. New diagnostic attachments must reuse the shared redaction policy.
