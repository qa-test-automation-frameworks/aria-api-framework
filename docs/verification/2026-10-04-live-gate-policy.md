# Live-smoke authentication and gate policy — 2026-10-04

Roadmap F07: implemented locally, pending actual requested remote execution.
The original scheduled failure was three public GitHub GETs receiving 401,
while four Booker cases passed. Its immutable diagnosis remains in
[2026-10-04-ci-diagnosis.md](2026-10-04-ci-diagnosis.md).

Public GitHub smoke now receives `github.token` from its own Actions job instead
of a manually maintained `ARIA_GITHUB_TOKEN` secret. The workflow retains
`contents: read`; private/write scenarios are not included in this smoke tier.
This follows [GitHub's job-token authentication guidance](https://docs.github.com/en/actions/tutorials/authenticate-with-github_token).
Local/private scenarios still accept their explicitly configured token.

Scheduled live smoke remains enabled. Manual dispatch defaults to no live test;
`run_live_smoke=true` requests the same bounded public-demo suite. Existing
Booker test credentials remain required; missing prerequisites fail explicitly
without printing their values. JUnit/Allure/log diagnostics upload with `always()`
even if the requested job fails. A preflight or artifact upload cannot substitute
for actual successful test execution.

The aggregate gate now waits for live-smoke and requires success when scheduled
or manually requested. Unrequested PR/push/manual live jobs must remain skipped.
Dependency review must succeed on PRs and be skipped elsewhere. Test, source
OSV and Docker jobs always require success. No continue-on-error or test retry
was introduced.

Validation:

```sh
bash -n scripts/verify-ci-gates.sh scripts/test-ci-gates.sh
bash scripts/test-ci-gates.sh
actionlint -shellcheck= -pyflakes= .github/workflows/ci.yml
```

Native Bash controls passed **68 cases**, including positive PR/push/scheduled/
manual dispositions and intended negative failures for missing, failed,
cancelled or improperly skipped required tiers. Each negative checks its exact
reason and exit code; an unrelated setup failure cannot count as success.
Actionlint 1.7.7's official archive checksum was verified and its workflow check
passed. ShellCheck/Pyflakes were not invoked; Bash syntax/control verification
is separate, not claimed as those tools' coverage.

Remaining exit: dispatch the delivered branch with live smoke requested; inspect
the actual native cases, source SHA, token-backed public results and retained
artifact. A successful deterministic PR run with skipped live smoke does not
satisfy that exit. Security remediation and other portfolio work remain separate.
