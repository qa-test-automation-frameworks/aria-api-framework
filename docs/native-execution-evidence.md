# Native execution evidence

The CI workflow emits schema-v3 evidence directly from Gradle's native JUnit
XML. `aria.deterministic` reads only `build/test-results/test`,
`aria.container` reads only `build/test-results/containerTest`, and
`aria.live-smoke` reads only `build/test-results/liveSmokeTest`. The CI `check`
task also runs Pact and other gates; its overall status is not presented as a
single combined test population.

The adapter reconciles suite totals against testcase children, hashes the
sorted native XML inputs, and omits testcase names, parameter values, failure
messages, and environment values from the JSON record. Missing, malformed,
unsafe, empty, or inconsistent XML becomes `unavailable` evidence and makes
collection fail. A complete report with failing tests remains `failed`
evidence; the original Gradle task remains failed. Native XML and the sanitized
record are uploaded together as short-lived GitHub artifacts.

An isolated `evidenceFailureControlTest` deliberately fails one JUnit testcase.
The regular test task excludes its tag. The control job requires the Gradle
command to fail, validates the resulting record, and checks its source/run
identity, exact one-failure counts, and native report digest before the quality
gate accepts it.

When live smoke is not requested, CI emits a separate `skipped` record with a
reason and null result counts/timestamps. Requested live smoke with missing
credentials is still a failed preflight; it is never relabeled as a successful
skip. Live execution remains optional outside the scheduled and explicitly
requested workflow events.

The checked-in JSON schema and freshness policy are snapshots of the E01
contract. Schema SHA-256:
`40502a47e825755d216e35ea19716ad7de69e47accd6aed1cfb3607160e4d1b9`.
Freshness policy SHA-256:
`1313329c1c06bd3a056dd1ed7d25c631036cae58480fdc4e767f22a0a01a568e`.
The Node runtime is pinned in `.nvmrc`; CI invokes the dependency-free contract
validator after setting it up.
