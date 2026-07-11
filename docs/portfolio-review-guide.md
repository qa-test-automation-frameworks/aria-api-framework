# ARIA Portfolio Review Guide

This guide is a short evidence-based path through the repository. Claims below link to source, executable checks, or generated artifacts.

## Recommended Review Order

1. Read the [architecture](ARCHITECTURE.md) and [ADR index](adr/README.md).
2. Inspect [`BaseApiClient`](../src/main/java/com/aria/framework/clients/BaseApiClient.java), services, and typed request/response models.
3. Review [`RedactionPolicy`](../src/main/java/com/aria/framework/reporting/RedactionPolicy.java) and the [threat model](security/aria-api-framework-threat-model.md).
4. Read the owned fixture and atomic concurrency test under [`src/test`](../src/test/java/com/aria/framework/).
5. Run the deterministic gate and inspect Allure, OpenAPI coverage, runtime metrics, and duration reports.
6. Review [retry and quarantine rules](RELIABILITY_POLICY.md), [failure triage](failure-example.md), and [seeded defect examples](seeded-defects.md).

## Evidence Map

| Capability | Repository evidence | Generated evidence |
| --- | --- | --- |
| Layered API design | [`clients`](../src/main/java/com/aria/framework/clients), [`services`](../src/main/java/com/aria/framework/services), [`models`](../src/main/java/com/aria/framework/models) | JUnit and Allure results |
| Secret-safe diagnostics | [`RedactionPolicy`](../src/main/java/com/aria/framework/reporting/RedactionPolicy.java), [`FailureDiagnosticFilter`](../src/main/java/com/aria/framework/reporting/FailureDiagnosticFilter.java) | Sanitized Allure attachments |
| Contract coverage | [`contracts`](../src/test/java/com/aria/framework/contracts), [`OpenApiCoverageReporter`](../src/test/java/com/aria/framework/tools/OpenApiCoverageReporter.java) | `build/reports/openapi-coverage.md` |
| Concurrency behavior | [`ConcurrentBookingBoundaryTest`](../src/test/java/com/aria/framework/concurrency/ConcurrentBookingBoundaryTest.java) | One accepted update, one stale conflict |
| Retry boundaries | [`RetryUtils`](../src/main/java/com/aria/framework/utils/RetryUtils.java) and retry tests | Zero JUnit retries in portfolio metrics |
| Supply-chain checks | [`build.gradle.kts`](../build.gradle.kts), [CI workflow](../.github/workflows/ci.yml) | SBOM, OSV, dependency review, SpotBugs |
| Runtime visibility | Gradle `portfolioMetrics` and `testDurationReport` tasks | JSON metrics and Markdown duration report |

## Reproduce the Evidence

```powershell
.\gradlew.bat clean check securityScan allureReport -Denv=dev
```

The default gate excludes live public targets. Docker-backed tests run through `containerTest`; scheduled live smoke tests require explicit credentials and produce a separate signal.

## Review Limits

The owned provider proves framework behavior deterministically; it does not reproduce every production gateway, network, identity-provider, or data-volume condition. Live tests add compatibility evidence but remain dependent on external availability and rate limits. Review the [threat model](security/aria-api-framework-threat-model.md) and ADR trade-offs before adapting the framework to a shared production environment.
