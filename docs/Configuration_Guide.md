# Configuration Guide

ARIA loads configuration through `ConfigManager`, Owner properties, system properties, and environment variables. The supported environments are `dev`, `staging`, and `prod`.

## Runtime Files

Environment defaults live in `src/main/resources/config`:

| File | Purpose |
| --- | --- |
| `dev.properties` | Local and deterministic default settings |
| `staging.properties` | Faster staging timeouts and SLA |
| `prod.properties` | Tighter production smoke settings |

Local Java version is pinned with `.java-version` set to `21`.

## Override Order

Use environment variables or system properties for machine-specific and sensitive values. Do not commit credentials.

| Setting | Environment variable | System property |
| --- | --- | --- |
| Restful Booker URL | `BOOKING_BASE_URL` | `booking.base.url` |
| GitHub URL | `GITHUB_BASE_URL` | `github.base.url` |
| GitHub token | `GITHUB_TOKEN` | `github.token` |
| Booker username | `BOOKER_USERNAME` | `booker.username` |
| Booker password | `BOOKER_PASSWORD` | `booker.password` |
| Request timeout | `TIMEOUT_SECONDS` | `timeout.seconds` |
| Retry attempts | `RETRY_MAX_ATTEMPTS` | `retry.maxAttempts` |
| SLA threshold | `RESPONSE_TIME_SLA_MS` | `sla.responseTimeMs` |

## Validation

Configuration fails fast for unsupported environments, blank or malformed base URLs, invalid timeout/SLA values, invalid retry values, and missing live Restful Booker credentials.

## Examples

```powershell
.\gradlew.bat test -Denv=dev
.\gradlew.bat smokeTest -Denv=staging
$env:BOOKER_USERNAME="<restful-booker-user>"
$env:BOOKER_PASSWORD="<restful-booker-password>"
.\gradlew.bat liveSmokeTest -Denv=dev
```
