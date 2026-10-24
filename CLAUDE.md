# Repository guide

## Stack and purpose

Java 21 API testing framework with layered clients and services, JUnit Jupiter
6.1.2, RestAssured 6.0.0, WireMock 3.13.2, Pact 4.6.17, and Testcontainers
1.21.4. The version catalog in `gradle/libs.versions.toml` is authoritative.

Owned deterministic targets, container checks, consumer contracts, and provider
verification run in GitHub Actions. External live smoke is isolated in
`.github/workflows/live-smoke.yml`; its availability does not determine the
owned-target quality gate. The Jenkinsfile is a reference pipeline; this
repository executes GitHub Actions.

## Commands and verification

Use the Gradle wrapper with Java 21. Verify changes in GitHub CI and preserve
the required job names and checks.

- `./gradlew test` — deterministic API and framework checks.
- `./gradlew contractTest` — consumer contract and schema checks.
- `./gradlew pactProviderVerificationTest` — owned provider verification.
- `./gradlew containerTest` — container-backed checks.
- `./gradlew sharedReferenceAppTest` — pinned reference-application integration.
- `./gradlew evidenceFailureControlTest` — intentional failure evidence control.
- `./gradlew liveSmokeTest -Denv=dev` — external smoke, only through its explicit workflow.
- `./gradlew spotlessCheck spotbugsMain spotbugsTest` — formatting and static checks.
- `./gradlew cyclonedxBom` — dependency inventory.

## Layout

- `src/main/java` — clients, services, configuration, assertions, and diagnostics.
- `src/test/java` — deterministic, contract, provider, container, and control tests.
- `src/test/resources` — synthetic data, schemas, and test configuration.
- `scripts/` — evidence adapters and controlled mutation verifiers.
- `docs/` — architecture, reviewer paths, evidence, and dated verification records.
- `reliability/quarantine.yml` — reviewed quarantine entries with expiries.
- `portfolio/` — portfolio metadata and evidence schema snapshots.

## Evidence and working rules

Preserve historical evidence bytes. Generate execution records from native test
results, with the real source SHA, run, attempt, target, and result counts.
Expected raw failures remain optional; a required verifier must prove detection.
Keep credentials out of artifacts and retain diagnostic redaction. Never weaken
a gate or invent a passing result to make CI green.

Use scoped searches for configuration, documentation, and small edits. An
existing codebase index can help structural work; refresh it only when needed.
Do not commit generated code indexes or machine-specific settings.

Machine-specific tool names and preferences belong in the ignored
`CLAUDE.local.md`, which is not part of the shared repository instructions.
