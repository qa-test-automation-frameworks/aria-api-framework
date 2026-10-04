# ARIA dependency remediation — 2026-10-04

Roadmap R02/F07: the exact-head CI scan of `098ab77b8583da3294762976d515c50f02e99ce0`
reported 28 advisories across 11 locked Maven packages. Selected compatible fixes
within existing major families; no JUnit/Gradle/Testcontainers major migration,
security exception renewal, assertion relaxation or scanner bypass.

| Family | Selected alignment | Reason/scope |
|---|---|---|
| Jackson | 2.22.3 BOM/core/databind/modules | Fixed versions listed in the native OSV finding table; tool classpaths remain aligned |
| Netty | 4.2.17.Final BOM/modules | Patch alignment for the HTTP/compression/handler findings, including the [upstream SNI advisory](https://github.com/netty/netty/security/advisories/GHSA-c4c3-7fpv-j4q5) |
| Apache HTTP 5 | Client/fluent 5.6.3, Core/H2 5.4.3 | Native fix versions; compatible client/core families aligned |
| Log4j | API/core 2.25.5 | Patch alignment includes the SpotBugs tool configuration |
| FreeMarker | 2.3.35 | Native fix for template-loader traversal finding |

Gradle regenerated selected lock families with `dependencies --update-locks`.
The first native source scan still found three HTTP 5 advisories: Allure's
`tmpTestImplementation` did not receive ordinary test constraints. Explicit
configuration-wide alignment corrected that real remaining path; the old
versions are absent from the final lock. Intermediate evidence is preserved.
The expired Jackson record (expiry 2026-08-04) was retired, not extended; its
historical rationale is retained in [exception-history.md](exception-history.md).

The official OSV 2.3.8 executable matched its published SHA256. Its recursive
source scan extracted 295 locked packages and exited zero with no findings and
no active exclusions. Native JSON/stderr, scanner identity and source hashes
are in [scans/2026-10-04-remediation/verification.json](scans/2026-10-04-remediation/verification.json).
This is a dated known-vulnerability scan, not a security certification.

Final local command:

```sh
PATH=<pinned OSV tools> ./gradlew spotlessApply check securityScan allureReport -PrequireOsvScanner=true --no-daemon --max-workers=2
```

The aggregate command passed on Temurin 21.0.12.1+1 / wrapper Gradle 8.7.
Main JUnit execution: 89 passed, one Docker-dependent skip, zero failures;
SpotBugs, formatting, provider verification, OpenAPI and tag checks passed.
The unchanged PIT gate passed (native statuses are in the manifest). Allure
rendering passed. Local `securityScan` actually executed OSV against its generated
SBOM and exited zero; its invocation now uses OSV v2's `scan source --sbom`
syntax. `-PrequireOsvScanner=true` prevents a missing scanner from being
misreported as executed verification.

Remote source scanning and Docker execution at the delivered revision remain
separate required evidence. The prior run's Java/Docker success belongs to its
old SHA and does not replace post-change CI. Scheduled live authentication is a
separate F07 repair. Other repositories' R02/G01 work remains required.
