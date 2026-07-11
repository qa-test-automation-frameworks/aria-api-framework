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
