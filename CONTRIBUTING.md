# Contributing

ARIA is a portfolio-grade API test automation framework. Contributions should preserve deterministic default execution, secret-safe diagnostics, and clear service/client boundaries.

## Local Setup

Prerequisites:

- Java 21
- Docker Desktop or a Docker-compatible runtime for container tests
- Gradle wrapper from this repository

Run the deterministic quality gate before opening a pull request:

```powershell
.\gradlew.bat clean check securityScan allureReport -Denv=dev
```

Run focused suites while developing:

```powershell
.\gradlew.bat test -Denv=dev
.\gradlew.bat smokeTest -Denv=dev
.\gradlew.bat contractTest -Denv=dev
.\gradlew.bat securityTest -Denv=dev
.\gradlew.bat containerTest -Denv=dev
```

## Contribution Rules

- Keep live public API tests tagged with `live`; the default `test` task must stay deterministic.
- Do not commit credentials, `.env`, generated reports, logs, `.gradle/`, `.idea/`, `.codegraph/`, or `build/`.
- Add request/response DTOs, data builders, schema checks, and OpenAPI coverage mappings for new endpoints.
- Prefer service-level tests and reusable assertion helpers over raw RestAssured assertions scattered across test classes.
- Update docs and ADRs when changing a framework-level design decision.

## Pull Request Checklist

- Tests pass locally or the failure is explained.
- New tests are tagged intentionally (`smoke`, `regression`, `contract`, `security`, `container`, or `live`).
- Allure diagnostics remain sanitized.
- OpenAPI endpoint coverage is updated when endpoint behavior changes.
- README or guide updates are included for user-facing workflow changes.
