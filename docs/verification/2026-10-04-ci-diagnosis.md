# ARIA CI diagnosis — 2026-10-04

Roadmap F07 is **in progress**, not repaired. This record separates the original
scheduled live failure from current deterministic and security results.

## Original scheduled live failure

[Run 35489588149](https://github.com/qa-test-automation-frameworks/aria-api-framework/actions/runs/35489588149)
tested `8965530b64b8447ffba87c8e5cf3d4ff8835e11b`. Its `live-smoke` job
106022104562 executed seven cases: four passed and three failed. The three
GitHub checks (`/rate_limit`, `/repos/octocat/Hello-World`, `/users/octocat`)
received `401 Unauthorized`. Booker authentication, creation with cleanup,
booking-list and health checks passed. This was neither empty collection nor
a blanket failure to supply Booker credentials.

The current scheduled job supplies `secrets.ARIA_GITHUB_TOKEN`. Rejected GitHub
credentials are the concrete investigation path; the log alone cannot establish
expiry, revocation, malformed credentials or another precise rejection cause.
No secret value was read or retained. Native outcomes and endpoints are in
[evidence/2026-10-04-ci-diagnosis/live-smoke-summary.json](evidence/2026-10-04-ci-diagnosis/live-smoke-summary.json).

Next: remove unnecessary dependence on a manually maintained PAT for public
read checks, verify the chosen authentication path with a current real run,
retain live diagnostics, and ensure the aggregate gate requires live success
when that tier is requested. Keep live execution separate from deterministic
checks; a credential preflight is not live test execution.

## Current deterministic and scanner results

[Run 37182717897](https://github.com/qa-test-automation-frameworks/aria-api-framework/actions/runs/37182717897)
tested `b3ad23349a6842bbc2f0adfebd31cb592d2865a7`. Java quality and the
Docker-backed container job passed. Dependency review passed. Live smoke was
skipped because this was a PR run; this does not prove the scheduled live repair.

OSV action v2.3.8 executed its scanner successfully; its reporter failed on
**28 vulnerability records across 11 locked Maven packages** (two critical,
12 high, 14 medium). The log lists fixes for all 28. The aggregate gate correctly
failed rather than treating this as healthy. The extracted native finding table,
source SHA and job identity are retained in
[../security/scans/2026-10-04-osv-baseline.json](../security/scans/2026-10-04-osv-baseline.json).

Next R02 action: trace the affected Jackson/Netty/Apache HTTP/Log4j/FreeMarker
versions to their dependency owners, select compatible patched alignments,
regenerate the lock with the native package manager, execute the required Java,
provider and Docker checks, and obtain a fresh OSV scan. An SBOM-only local
`securityScan` task is not vulnerability-clear evidence. No scanner exception,
threshold relaxation or continue-on-error was added.

The September audit remains historical. These retrieved job logs narrow its
previously unavailable causes; they do not retroactively turn those failed runs
into successes or prove either remediation complete.
