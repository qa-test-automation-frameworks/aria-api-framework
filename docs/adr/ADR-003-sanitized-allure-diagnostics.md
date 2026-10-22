# ADR-003: Sanitized Allure Diagnostics

## Status

Accepted

## Context

HTTP diagnostics are essential for triage, but attaching raw requests and responses can leak tokens, cookies, passwords, API keys, and sensitive query parameters.

## Decision

All RestAssured clients build immutable, bounded snapshots from request and response objects before rendering logs or attachments. The snapshot renderer removes URL userinfo and query values, omits cookies and unapproved headers, parses supported JSON and recursively redacts sensitive-key values, and omits malformed, unknown, binary, or oversized bodies. Failure-specific snapshots cover 4xx, 5xx, and transport exceptions without including exception messages. `RedactionPolicy.sanitize(String)` remains a conservative fallback for legacy mixed-text diagnostics and omits the whole message when it detects a sensitive signal.

## Consequences

Failures retain useful method, status, path, safe header metadata, and non-sensitive JSON fields in Allure and CI artifacts. This is protection for the supported structured formats, not a guarantee that arbitrary secrets embedded in ordinary text can be discovered; tests should use synthetic data. New HTTP diagnostic attachments must use the snapshot builder.
