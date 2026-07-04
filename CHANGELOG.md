# Changelog

## 1.0.0 - 2026-05-24

- Added layered RestAssured API framework for Restful Booker and GitHub.
- Added schema validation, WireMock tests, Pact consumer tests, Allure reporting, and CI publishing.
- Added Docker, Docker Compose, environment-based configuration, tagged test execution, and generated data factories.
- Hardened audit findings with deterministic default tests, live-test opt-in, config validation, tracked data cleanup, security tests, OpenAPI coverage reporting, enforced quality gates, and dependency review.

## 1.1.0 - 2026-07-04

- Replaced JavaFaker with Datafaker and refreshed Gradle dependency locks.
- Reworked configuration and retry policy injection for easier isolated testing.
- Added PUT idempotency, 429 retry, long-name boundary, strict content-type, and OPTIONS coverage.
- Added test duration reporting, expanded Jenkins parity, and split Allure Pages deployment from the main CI badge path.
- Replaced the README text architecture diagram with a linked SVG architecture diagram.
- Added configuration, execution, writing-tests, debugging, dos/dont, audit-remediation, and ADR documentation.
- Added atomic optimistic-concurrency evidence, portfolio metrics, reliability policy, failure triage, seeded-defect examples, and an application security threat model.
- Added mutation-score publishing through PITest for redaction, retry, and OpenAPI coverage utilities.
- Added Pact provider verification against the owned fixture and corrected the Allure plugin version pin that blocked dependency-lock regeneration.
- Added OpenAPI response body-schema validation via swagger-request-validator, alongside the existing documented-response and media-type checks.
- Forced patched versions for OSV-flagged transitive dependencies reachable only through tool-classpath resolution.
