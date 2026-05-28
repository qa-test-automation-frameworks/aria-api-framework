# ARIA API Framework - Portfolio Review Guide

Welcome! This guide is designed as an interactive walkthrough for developers, engineering managers, and architects who are interested in exploring the **ARIA (Automated REST Interface Assertion Framework)** codebase. 

The primary goal of this repository is to showcase **expert-level software engineering principles applied to test automation**, focusing on extensibility, security, testability, and CI/CD quality gates.

---

## 🌟 Senior / Staff-Level Differentiators

Unlike typical test automation suites that act as simple wrappers around HTTP clients, ARIA is built as a robust, production-ready framework. Here are the core pillars of engineering maturity implemented in this repository:

### 1. Architectural Cleanliness (Layered Pattern)
We separate concerns strictly using a layered architecture:
*   **Request/Response DTOs (`models`)**: Immutable data transfer objects mapped with Jackson.
*   **Fluent Payload Builders (`builders`)**: Builders with valid, production-like defaults so tests only specify fields relevant to their test case.
*   **Low-level API Clients (`clients`)**: RestAssured wrappers that handle HTTP specifics, standard headers, pluggable auth, and logging filters.
*   **Business-level Services (`services`)**: Orchestrate clients, perform token retrieval, and abstract environment details from the actual tests.
*   **JUnit Tests**: Free of raw endpoints, HTTP verbs, or JSON string manipulation.

### 2. Custom OpenAPI Coverage Reporter
*   **The Problem**: How do you guarantee your tests actually cover all public endpoints defined in your API contract?
*   **The Solution**: A custom reporter under `src/test/java/com/aria/framework/tools/OpenApiCoverageReporter.java`.
*   **How it works**: It parses OpenAPI specification files and maps them to a test registration registry (`endpoint-test-coverage.csv`). Running `./gradlew.bat openApiCoverageReport` validates that every endpoint has corresponding tests, fail-fast matches schemas, and prints an elegant markdown summary under `build/reports/openapi-coverage.md`.

### 3. Bulletproof Diagnostics & Zero Leakage
*   **The Problem**: Allure reports and CI logs often accidentally leak authorization headers, tokens, and PII.
*   **The Solution**: Customized Redaction filters (`src/main/java/com/aria/framework/utils/RedactionFilter.java`).
*   **How it works**: RestAssured request/response exchanges are automatically intercepted. Sensitive headers (`Authorization`, `Cookie`) and JSON paths are obfuscated *before* they hit logs or Allure report attachments, ensuring secure diagnostics.

### 4. Pluggable Auth Orchestration
*   **The Solution**: Interfaces for pluggable `AuthStrategy` (`src/main/java/com/aria/framework/auth/`).
*   **How it works**: We support Bearer, Basic, Cookie-based, and Key-based authentication models natively without hardcoding headers, keeping auth logic completely reusable and unit-testable.

### 5. Advanced Contract & Container Testing
*   **Consumer Contract Testing**: Implemented using **Pact JVM** (`src/test/java/com/aria/framework/contracts/`) to verify consumer-provider expectations before deployment.
*   **Docker-backed Testcontainers**: Used to run deterministic containerized dependencies (`src/test/java/com/aria/framework/fixtures/` and `@Tag("container")`) directly inside the CI runner, eliminating external environment flakiness.

---

## 🔍 Guided Tour of the Codebase

Use this quick-access table to inspect the core implementations:

| Concept / Feature | Key Location in Repository | Highlights |
| :--- | :--- | :--- |
| **Pluggable Auth Strategy** | [Auth Directory](file:///c:/Docs/training/pract/aria-api-framework/src/main/java/com/aria/framework/auth) | Interfaces and cookie/token providers. |
| **Custom Redaction Filter** | [RedactionFilter.java](file:///c:/Docs/training/pract/aria-api-framework/src/main/java/com/aria/framework/utils/RedactionFilter.java) | Prevents API secrets from leaking into Allure diagnostics. |
| **OpenAPI Coverage Tool** | [OpenApiCoverageReporter.java](file:///c:/Docs/training/pract/aria-api-framework/src/test/java/com/aria/framework/tools/OpenApiCoverageReporter.java) | Validates test coverage directly against OpenAPI specs. |
| **Retry & Backoff Logic** | [RetryExecutor.java](file:///c:/Docs/training/pract/aria-api-framework/src/main/java/com/aria/framework/utils/RetryExecutor.java) | Thread-safe exponential backoff with randomized jitter. |
| **Deterministic WireMock** | [Mock Directory](file:///c:/Docs/training/pract/aria-api-framework/src/test/java/com/aria/framework/mocks) | Simulates real-world API failures, delays, and rate limits. |
| **CI/CD Quality Gates** | [CI Workflow](file:///c:/Docs/training/pract/aria-api-framework/.github/workflows/ci.yml) | Enforces SPOTBUGs static analysis, Spotless formatting, and dependency reviews. |

---

## 🏆 Key Competency Mapping

When reviewing this codebase, you will see direct evidence of the following competencies:

### 🛡️ Secure Engineering Posture
*   Strict exclusion of local `.env` and `build/` files via `.gitignore` and `.dockerignore`.
*   Auto-remediation of third-party vulnerabilities through Gradle dependency constraints locking down secure libraries (e.g., `commons-lang3`, `netty`, `log4j-core` constraints in `build.gradle.kts`).
*   Automated OSV scanning and Dependency Reviews in GitHub Actions.

### ⚡ Resilience & Reliability Design
*   Pluggable, thread-safe request retries with randomized jitter to handle micro-network blips gracefully without failing builds.
*   Deterministic default execution: The main `./gradlew test` task does not rely on third-party public API availability (avoiding flaky builds due to rate limits or sandbox outages).

### 📈 Observable Systems
*   Dynamic Allure categories and tags (`smoke`, `regression`, `contract`, `security`, `live`).
*   Centralized, customized Logback configurations writing clean, structured diagnostic traces.
