# Contributor Architecture Guide

## First Run

```bash
./gradlew test
```

The default `test` task is deterministic — it runs against the owned in-JVM provider fixture and
does not require external credentials.

## Project Map

| Area                         | Purpose                                                                   |
| ---------------------------- | ------------------------------------------------------------------------- |
| `src/main/java`              | Reusable API framework code: clients, config, reporting helpers           |
| `src/test/java`              | JUnit 5 API, contract, reliability, and seeded-defect tests               |
| `src/test/resources`         | Test data, schemas, Allure/JUnit resources                                |
| `docs/`                      | Architecture, execution, reliability, writing-tests, and debugging guides |
| `reliability/quarantine.yml` | Quarantine policy and known reliability exceptions                        |
| `portfolio/manifest.yml`     | Portfolio metadata                                                        |

## Commands

- Main verification: `./gradlew test`
- Tagged tests: `./gradlew test -DincludeTags=smoke`
- Format: `./gradlew spotlessApply`
- Format check: `./gradlew spotlessCheck`
- Static checks: `./gradlew spotbugsMain spotbugsTest`
- Mutation score: `./gradlew pitest`
- Full quality gate: `./gradlew check`
- Dependency/security artifacts: `./gradlew cyclonedxBom`

## Change Workflow

1. Keep changes scoped to the framework layer under test; avoid unrelated cleanup.
2. Prefer targeted Gradle tasks and tagged tests over full-suite reruns while iterating.
3. Run `./gradlew check` before opening a PR — it aggregates format, static analysis, mutation
   score, OpenAPI coverage, and Pact provider verification.
