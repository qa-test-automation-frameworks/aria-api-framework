# Reliability Policy

## Default Gate

`.\gradlew.bat clean check securityScan allureReport -Denv=dev` is the deterministic gate. Tests tagged `live` are excluded and Docker-backed coverage runs in its dedicated CI job.

## Retry Rules

- JUnit tests are not retried. An assertion that fails once is a failing test.
- GET requests may retry transient network exceptions and explicit rate-limit responses.
- Mutations may retry only when the caller supplies an idempotency guarantee.
- Authentication, schema, assertion, and non-rate-limit 4xx failures are not retried.
- Scheduled live smoke failures are investigated separately from deterministic gate failures.
- `Retry-After` accepts nonnegative delta-seconds and the three HTTP-date formats.
  Past dates add no wait. Malformed/negative values use bounded local backoff;
  oversized valid values do not overflow or become an early retry.
- The configured delay cap includes jitter. If a server-requested delay exceeds
  that cap, the original rate-limit response is returned rather than retrying
  before the server permits it. Existing callers can assert/handle that response.
- `retry.totalTimeoutMs` / `RETRY_TOTAL_TIMEOUT_MS` bounds one public operation
  (default 30,000ms), including request execution, body buffering, attempts and
  sleeps. It uses monotonic elapsed time; HTTP dates use a wall clock separately.
  Nested wrappers retain the outer budget. Existing four-argument retry policies
  and twelve-argument runtime configs retain the 30-second default.
- A rate-limit response is returned unchanged if its delay cannot fit the remaining
  budget; the framework does not retry early. An exhausted operation or an
  infeasible transient-exception backoff raises `RetryDeadlineExceededException`.
  Per-request transport failures can still surface before the operation deadline.
- Managed `BaseApiClient` requests receive the remaining connect/socket/pool
  timeout at dispatch, including prebuilt specifications. Response bodies are
  buffered inside the operation. Deadline/interruption cancellation shuts down
  their registered Apache HTTP connections; normal attempts close transports too.
- Operations run on fresh daemon workers with copied MDC and inherited Allure
  context. At most 32 unfinished operations can occupy slots per JVM; excess
  calls fail before starting a supplier. A cancelled supplier that ignores
  interruption keeps its slot until it actually exits. The framework cannot
  forcibly stop arbitrary user code or close transports it does not own; do not
  interpret the caller deadline as universal background cancellation.
- Single-attempt writes and authentication use the same configured operation
  budget without gaining retries. Caller interruption is preserved and starts no
  request when already set. No JUnit test retries were introduced.

## Quarantine Rules

Quarantine is exceptional and tracked in `reliability/quarantine.yml`. Every entry must include the test identifier, owner, issue URL, reason, added date, and an expiry no more than 14 days later. Expired entries block the quality gate. The current register is empty.

## Flake Triage

1. Reproduce with the same Gradle task and environment inputs.
2. Inspect JUnit XML, Allure attachments, and sanitized logs.
3. Classify the failure as product behavior, test defect, target instability, or infrastructure.
4. Fix deterministic test defects before merging. Do not hide them with retries.
5. Quarantine only when an owned issue and removal date exist.

## Runtime Evidence

`portfolioMetrics` reads JUnit XML and writes `build/reports/portfolio-metrics-v1.json`. `testDurationReport` writes the 20 slowest cases to `build/reports/test-duration-report.md`. CI uploads both files.
