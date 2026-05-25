# Framework Audit Checklist

This checklist defines the review bar for ARIA as a public portfolio repository. The current audit result is maintained in [AUDIT_REPORT.md](AUDIT_REPORT.md).

## Review Dimensions

- Project structure and Java/Gradle conventions
- Framework design, HTTP abstraction, service boundaries, and DTO usage
- Positive, negative, boundary, contract, security, performance, and data-driven test design
- Environment configuration, secret handling, validation, timeout, SLA, and retry tunables
- Test data factories, lifecycle management, and cleanup
- Authentication strategy coverage and security-boundary testing
- Reporting, logging, Allure diagnostics, artifact hygiene, and redaction
- CI/CD quality gates, artifact publishing, dependency scanning, and scheduled live smoke coverage
- Code quality, formatting, static analysis, dependency locking, and vulnerability posture
- Documentation quality, contributor workflows, governance files, and portfolio presentation
- Advanced differentiators such as OpenAPI coverage, Pact contracts, WireMock/Testcontainers, retry behavior, soft assertions, and pagination/rate-limit coverage

## Severity Scale

| Severity | Label | Meaning |
| --- | --- | --- |
| P0 | Critical | Blocks public sharing immediately. |
| P1 | Major | Creates serious doubt about engineering maturity. |
| P2 | Moderate | Should be fixed before using the repository in applications or interviews. |
| P3 | Minor | Polish that raises the repo from solid to excellent. |
| P4 | Enhancement | Differentiator that moves the repo toward staff-level polish. |

## Required Audit Output

Every audit should include:

1. Executive summary with scored dimensions.
2. Findings with severity, location, dimension, observation, impact, and recommendation.
3. Prioritized fix roadmap grouped by severity.
4. Well-implemented aspects worth preserving.

Use file paths, class names, method names, and line numbers wherever practical. Avoid generic praise or generic criticism.
