# Native execution evidence

The CI workflow emits schema-v3 evidence directly from Gradle's native JUnit
XML. `aria.deterministic` reads only `build/test-results/test`,
`aria.contract` reads only `build/test-results/contractTest`,
`aria.pact-provider` reads only
`build/test-results/pactProviderVerificationTest`, `aria.container` reads only
`build/test-results/containerTest`, and `aria.shared-app` reads only
`build/test-results/sharedReferenceAppTest`. Its target metadata records the
immutable APP image digest and fixture version; its native XML is kept alongside
the sanitized record. `aria.live-smoke` reads only
`build/test-results/liveSmokeTest`. These are distinct scopes; the CI `check`
task's overall status is not presented as one combined test population.

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

Live smoke runs in its own weekly workflow (`live-smoke.yml`) and publishes the
optional `aria.live-smoke` scope. Missing credentials are a failed preflight; they
are never relabeled as a successful skip. The main CI workflow no longer emits a
live-smoke record.

The checked-in JSON schema and freshness policy are snapshots of the E01
contract. Schema SHA-256:
`40502a47e825755d216e35ea19716ad7de69e47accd6aed1cfb3607160e4d1b9`.
Freshness policy SHA-256:
`1313329c1c06bd3a056dd1ed7d25c631036cae58480fdc4e767f22a0a01a568e`.
The Node runtime is pinned in `.nvmrc`; CI invokes the dependency-free contract
validator after setting it up.

## Candidate QRA runs

The `shared-reference-app-candidate` job is an opt-in `workflow_dispatch` on
`main`. Supply `candidate_run_id` and `candidate_artifact_id` from the same
successful QRA main push. The job verifies the descriptor and immutable image,
checks the image's source and version labels, checks readiness source and seed
identity, then emits the same `aria.shared-app` scope with the candidate OCI
digest. With both fields empty, the existing pinned-image job runs as usual.
The verifier tries the public QRA artifact endpoints without credentials; an
already provisioned `QRA_ACTIONS_READ_TOKEN` can be used if GitHub requires
authentication. A denied read fails the candidate job, leaving candidate
evidence unavailable. The workflow does not create or broaden credentials.
