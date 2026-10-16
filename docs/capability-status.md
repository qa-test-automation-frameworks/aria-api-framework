# Capability Status

| Capability | Status | Evidence |
| --- | --- | --- |
| Deterministic API regression | Enforced | `./gradlew test` |
| Pact consumer contracts | Enforced | `./gradlew contractTest` |
| Owned provider verification | Enforced | `./gradlew pactProviderVerificationTest` |
| OpenAPI endpoint coverage | Enforced | `./gradlew openApiCoverageReport` |
| Mutation testing | Enforced in `check` | `./gradlew pitest` |
| Live external smoke | Optional | `./gradlew liveSmokeTest` |

## Notes

The default gate excludes live tests. External environments are opt-in and must not be required for
public pull requests.

## Feature currency review — 2026-10-16

| Capability | Current implementation | Official support checked | Benefit and compatibility risk | Decision and proof |
| --- | --- | --- | --- | --- |
| Test lifecycle and parallel execution | JUnit Jupiter 5.10.2, Java 21, bounded Gradle test tasks and explicit resource locks | [JUnit 6.1.2 release notes](https://docs.junit.org/6.1.2/release-notes.html) and [Gradle release history](https://gradle.org/releases/) | JUnit 6 has ongoing platform fixes, but this is a major engine change that can affect Pact and Testcontainers extensions. | **Defer JUnit 6.** The existing candidate PR #60 failed both `test` and `container-test`; its old Actions logs have expired. Reproduce the failure on the current branch before adoption. |
| Consumer/provider contracts | Pact JVM 4.6.17 with JUnit 5 consumer and provider tests, including stateful provider verification | [Pact JVM 4.7.1 release notes](https://github.com/pact-foundation/pact-jvm/releases) document external references and matcher improvements; [provider JUnit guide](https://docs.pact.io/implementation_guides/jvm/provider/junit) documents provider states and pending pacts. | 4.7 adds capabilities for contracts that need external references; migrating the engine and broker behavior without an active requirement adds change surface. | **Retain 4.6.17** until a contract needs the added capability; current provider/consumer checks remain the contract proof. |
| Container-backed integration tests | Testcontainers 1.21.4 on the current branch; JUnit 5 extension and Docker-backed tests remain the integration boundary | [Testcontainers 1.21.4 release](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4) states that 1.21.x works with recent Docker Engine versions. | The 1.x update addresses Docker compatibility without the 2.0 package/class renames. | **Adopt 1.21.4.** The initial exact run `38932957427` caught stale strict lock entries (1.19.7), not a runtime regression. After refreshing both lock entries, exact run `38933386105` passed the full Gradle quality gate, Docker-backed `containerTest`, dependency scans and evidence controls; its native log records Testcontainers 1.21.4. Defer 2.0.5 until a separate migration proves module and import changes. |
| OpenAPI request/response validation | Swagger Request Validator 2.45.1 integrated with REST Assured | [Validator 3.0.0 candidate](https://github.com/qa-test-automation-frameworks/aria-api-framework/pull/62) | 3.0 is a major change; the existing candidate has failed `test` and `container-test`, so the current API/schema assertions do not establish migration compatibility. | **Retain 2.45.1.** Reproduce and fix the candidate failures before moving the version. |
| Build and plugin compatibility | Gradle wrapper 8.7 with pinned Allure, PIT, SpotBugs, CycloneDX and formatting plugins | [Gradle 9.7.1 release](https://gradle.org/releases/) is newer than the pinned wrapper. | A Gradle 9 migration may expose plugin API/deprecation issues and changes the build environment for every result. | **Defer to a focused compatibility change.** Do not combine it with library updates; require the full wrapper validation, `check`, contract, container and evidence gates. |

The Testcontainers update is the only adoption in this review. The major JUnit,
OpenAPI validator, Testcontainers 2.x and Gradle migrations stay visible as
compatibility work rather than being described as current or compatible.
