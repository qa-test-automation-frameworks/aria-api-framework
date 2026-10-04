# F05 phase A — HTTP-date and delay arithmetic

Runtime: Linux, Temurin JDK 21.0.12.1+1, declared Gradle wrapper 8.7.
The existing retry suite passed 18 cases before the repair.

## Changed behavior

`Retry-After` supports nonnegative delta-seconds and HTTP dates, including the
obsolete RFC850/asctime forms described in [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-date-time-formats).
An injected clock tests date behavior without depending on wall-clock timing.
Past dates give zero additional wait; positive submillisecond remainders round
up rather than allowing an early retry. Invalid/negative values use local
backoff; huge syntactically valid delta values saturate safely.

Exponential backoff saturates without overflowing. Jitter fits inside the
configured maximum, including when its configured value is Long.MAX_VALUE.
Directly constructed policies validate positive attempts/base delay, max >= base,
and nonnegative jitter. Public GET/idempotency-controlled mutation wrappers and
interrupted-sleep behavior remain covered.

A valid server delay above the policy cap returns the original rate-limit
response without sleeping/retrying early. The former test that expected a
five-second header to be truncated to two seconds now verifies the stronger
no-early-retry behavior. A WireMock fixture requested one second but allowed ten
milliseconds; its test now permits that requested delay and independently asserts
the exact 1,000ms sleep through the existing injected sleeper. Production defaults
and SLO thresholds were not relaxed.

## Regression proof

The initial five new cases failed on the original implementation. Two arithmetic
cases initially had headerless ResponseBuilder fixtures that failed inside the
response library before reaching arithmetic; those failures were not counted as
proof of the intended arithmetic defect. A disposable clean original checkout,
using realistic Content-Type headers, separately reproduced:

- Exponential sleeps `[4611686018427387904, -9223372036854775808, 0]` instead of
  positive saturated delays.
- Maximum jitter throwing `IllegalArgumentException: bound must be positive`.

The original date, negative-delta and huge-header cases also failed as specified.
After correction, `test --tests com.aria.framework.utils.RetryUtilsTest --tests
com.aria.framework.utils.RetryDelaySafetyTest` passed **27/27**; nine date/delay
safety cases cover the new behavior. No real large sleep occurs in these tests.

## Required local gate

Executed `./gradlew spotlessApply check securityScan allureReport --no-daemon
--max-workers=2` with an isolated GRADLE_USER_HOME and supported JDK. Exit 0:

- **74 passed, one skipped**, no failed/error cases. The skip is the
  Testcontainers WireMock case because local Docker is unavailable; dedicated
  remote container verification is still required.
- Formatting, SpotBugs main/test, OpenAPI coverage, native provider verification
  and live-tag-expression validation passed.
- PIT: **100/131 detected** (98 killed, two timed out); 22 survived and nine had no
  coverage. Actual mutated classes were RetryUtils and RedactionPolicy. The
  existing 70% gate passed; this is not whole-framework mutation adequacy.
- Allure report rendered. Tool/dependency downloads require network bootstrap.
- SBOM generated; `securityScan` explicitly reported **no local OSV execution**
  because the binary was unavailable. Its successful task exit is not a clean
  vulnerability scan.

## Still incomplete

F05's total deadline is not implemented by this phase. Supplier/request execution
can still exceed a sleep/attempt budget. The next phase must bound request time
and sleeps together, preserve safe mutation semantics, handle deadline exhaustion
and interruption, and verify real transport behavior before claiming end-to-end
bounds. Remote current-source CI and Docker/OSV results remain separate evidence.
