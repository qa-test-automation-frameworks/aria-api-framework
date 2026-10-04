# ARIA total operation deadline — 2026-10-04

Roadmap F05 phase B completes the request/response deadline that remained
explicitly pending in [phase A](2026-10-04-retry-delays.md). The test inputs are
identified by native source hashes in
[evidence/2026-10-04-operation-deadline/verification.json](evidence/2026-10-04-operation-deadline/verification.json).
Local proof is distinct from later remote CI and from F07/security remediation.

## Behavior

`retry.totalTimeoutMs` / `RETRY_TOTAL_TIMEOUT_MS` defaults to 30,000ms. One
monotonic budget spans request dispatch, body consumption, attempts and sleeps.
Existing constructors retain their compatible default. Nested wrappers keep the
outer budget; a rate-limit delay that cannot fit returns the original response,
and an infeasible transient retry or exhausted operation raises
`RetryDeadlineExceededException`. Wall-clock HTTP-date parsing remains separate.

The managed client factory registers real Apache HTTP connection managers.
Cancellation shuts them down; a dispatch filter updates connect/socket/pool
bounds from the remaining budget even for a prebuilt specification. Bodies are
buffered inside the operation, and healthy buffered responses remain readable
after transport cleanup. Single-attempt writes/authentication receive their own
configuration's budget and do not gain retries.

Fresh daemon workers inherit Allure context and copy MDC. A fixed 32-slot bound
rejects excess work before starting a supplier. An interruption-ignoring supplier
retains its slot until it exits, rather than allowing unlimited replacement
workers. Prior/caller interruption is preserved. Ordinary failures propagate.
The caller deadline cannot forcibly stop arbitrary callbacks or close transports
outside the managed factory; these limits are documented in the reliability
policy. Default wall-clock timing is not a hard real-time scheduling guarantee.

## Executed validation

Runtime: Linux, Temurin 21.0.12.1+1, wrapper Gradle 8.7.

```sh
./gradlew spotlessApply test --tests '*Retry*Test' --tests '*EnvironmentPropertiesTest' --no-daemon --max-workers=2
./gradlew spotlessApply check securityScan allureReport --no-daemon --max-workers=2
```

The focused command passed **45 cases**, with no skips. The final aggregate
command exited zero: the main test task executed **90 cases, 89 passed and one
Docker-dependent skip**, with no failures. SpotBugs, formatting, OpenAPI coverage,
provider verification and tag-expression checks passed. PIT detected 108/142
mutations (106 killed and two timeouts), with 25 survivors and nine uncovered;
the existing 70% threshold was preserved. Actual mutation scope remains
`RetryUtils` and `RedactionPolicy`; this is not whole-framework mutation coverage.

Real loopback regressions cover stalled headers, a continuously trickling body,
a prebuilt specification's reduced transport timeout, connection closure, worker
termination and reading a healthy buffered body. Other cases cover combined
request/retry/sleep time, no early Retry-After retry, nested budgets, unsafe versus
explicitly idempotent writes, interruption, timeout overflow, cyclic cause chains,
MDC/Allure isolation and retained/recovered capacity for 32 controlled
uncooperative suppliers.

The old huge-delay arithmetic regression now calls the package-local calculation
seam directly: a real operation correctly refuses to schedule that impossible
sleep. Exact expected saturation values are retained. No timing threshold,
assertion or policy gate was relaxed to accommodate the implementation.

Two initial test-fixture issues were corrected: response-library initialization
was moved outside a narrow timing control, and the Allure isolation probe now
creates/removes a child step instead of removing the caller's shared step record.
Neither initial failure was counted as product-failure proof.

## Negative control

An isolated checkout kept caller Future cancellation but removed transport
shutdown from the timeout branch. The trickling-body test **failed as intended**:
the deadline exception and one-request assertions passed, but the real server did
not observe connection closure within the two-second cleanup bound. Native
failed JUnit XML is retained as gzip without changing native bytes. Focused,
aggregate and repeat-render logs are retained the same way; the manifest lists
their uncompressed hashes. This specifically rejects a caller-only timeout;
it is not a failure caused by missing dependencies or compilation.

## Remaining boundaries

Local `securityScan` produced an SBOM and explicitly did not execute OSV; no
security-clear claim is made. Docker was unavailable locally. Earlier remote
container proof belongs to its recorded source, not automatically to these new
files. Current vulnerability findings and scheduled live failures remain F07/R02.

Repeated rendering initially failed because the old Allure output directory
already existed. The separate build fix enables the report plugin's native clean
output setting. This removes generated report output, preserves raw results, and
does not claim durable atomic publication or current-run filtering of reused raw
results. Remote CI starts clean; durable evidence remains E01–E04 work.
