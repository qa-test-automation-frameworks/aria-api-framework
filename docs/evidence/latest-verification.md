# Current Verification Record

| Field | Value |
|---|---|
| Repository ref | `main` (refresh after the next weekly scheduled smoke) |
| Fast gate | `ci.yml` — deterministic Gradle quality, security, container, contract, and report gates |
| Full evidence | Weekly scheduled `live-smoke` job; broader live regression remains manual |
| Current state | `evidence-stale`; this record must be refreshed by the next weekly run |
| Target/environment | Owned/container-backed provider plus explicitly configured live smoke target |
| Evidence class | Controlled and scheduled-live |
| Report | [Allure report](https://qa-test-automation-frameworks.github.io/aria-api-framework/) |
| Known limitations | [Known issues](../known-issues.md) and [review guide](../Portfolio_Review_Guide.md) |

The next record must include the exact SHA, workflow run URL, completion time,
deterministic/container/live scope, OpenAPI/Pact/mutation results, artifact links,
and any external-target limitation.
