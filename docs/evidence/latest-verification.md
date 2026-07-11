# Current Verification Record

| Field | Value |
|---|---|
| Repository ref | `main` @ [`ab00c63`](https://github.com/qa-test-automation-frameworks/aria-api-framework/commit/ab00c637e820d952ab756f3926255d311485e851) |
| Fast gate | [`ci.yml` run 29138314002](https://github.com/qa-test-automation-frameworks/aria-api-framework/actions/runs/29138314002) — deterministic Gradle quality, security, container, contract, and report gates; completed 2026-07-11T03:40:32Z |
| Full evidence | Weekly scheduled `live-smoke` job; broader live regression remains manual |
| Current state | `review-ready`; refresh after the next scheduled or merged run |
| Target/environment | Owned/container-backed provider plus explicitly configured live smoke target |
| Evidence class | Controlled and scheduled-live |
| Result counts | 66 tests, 66 passed, 0 failed, 0 errors, 0 skipped (13.695s), from JUnit XML |
| Report | [Allure report](https://qa-test-automation-frameworks.github.io/aria-api-framework/) |
| Known limitations | [Known issues](../known-issues.md) and [review guide](../portfolio-review-guide.md) |

The machine-readable record with the exact SHA, run ID/URL, conclusion, and result
counts is published at [`latest-verification.json`](latest-verification.json). This
record does not include the weekly `live-smoke` job's own pass/fail counts; that
scope remains a controlled/scheduled-live distinction, not a production claim.
