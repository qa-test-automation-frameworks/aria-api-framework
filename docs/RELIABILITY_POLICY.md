# Reliability Policy

## Default Gate

`.\gradlew.bat clean check securityScan allureReport -Denv=dev` is the deterministic gate. Tests tagged `live` are excluded and Docker-backed coverage runs in its dedicated CI job.

## Retry Rules

- JUnit tests are not retried. An assertion that fails once is a failing test.
- GET requests may retry transient network exceptions and explicit rate-limit responses.
- Mutations may retry only when the caller supplies an idempotency guarantee.
- Authentication, schema, assertion, and non-rate-limit 4xx failures are not retried.
- Scheduled live smoke failures are investigated separately from deterministic gate failures.

## Quarantine Rules

Quarantine is exceptional and tracked in `reliability/quarantine.yml`. Every entry must include the test identifier, owner, issue URL, reason, added date, and an expiry no more than 14 days later. Expired entries block the quality gate. The current register is empty.

## Flake Triage

1. Reproduce with the same Gradle task and environment inputs.
2. Inspect JUnit XML, Allure attachments, and sanitized logs.
3. Classify the failure as product behavior, test defect, target instability, or infrastructure.
4. Fix deterministic test defects before merging. Do not hide them with retries.
5. Quarantine only when an owned issue and removal date exist.

## Runtime Evidence

`portfolioMetrics` reads JUnit XML and writes `build/reports/portfolio-metrics-v1.json`. `testDurationReport` writes the 20 slowest cases to `build/reports/test-duration-report.md`. CI uploads both files.
