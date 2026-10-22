# Claims state-model exploration

The shared application suite includes a bounded, deterministic exploration of
claim journeys. It checks command sequences against a test-owned model of
authorization, lifecycle, API-version limits, event history, and list
visibility. The model is intentionally independent of the provider's transition
function; matching OpenAPI examples or provider output is not used to decide
expected behavior.

## Frozen behavior

- A member creates a claim in `submitted` at version 1, with one creation event.
- A reviewer can move `submitted` to `under_review`, then to `approved` or
  `rejected`. Both terminal states reject further transitions.
- An accepted transition increments the version once and appends one event.
  A rejected request leaves the claim and event history unchanged.
- Members can see their own claims. A different member gets
  `404 RESOURCE_NOT_FOUND` when reading the claim or its events, and the claim
  is omitted from that member's successful list. Members cannot transition a
  claim (`403 ROLE_FORBIDDEN`).
- For reviewers, an incorrect `expectedVersion` is checked before transition
  legality (`409 VERSION_CONFLICT`); a current-version invalid edge returns
  `409 INVALID_TRANSITION`.
- Amounts use integer minor units. The minimum is 1; API v1 allows up to
  1,000,000 and API v2 up to 500,000. Invalid create amounts return
  `422 VALIDATION_ERROR`.
- Test actors are freshly issued through `/api/v1/sessions` for both API
  versions. Claims are created only for member-owned policies.

This contract is deliberately duplicated in test code. When the product
contract changes, review the public API contract and decision documentation,
then update this document, the independent model, the fixed sequence and its
tests together. Do not regenerate the expected transition rules from the
provider implementation.

## Bounded sequences and receipts

The committed seeds are `0x51DE7` and `0xC1A1`. Each runs against API v1 and v2
for four sequences of 30 commands total. Each sequence starts with a fixed
prefix for member ownership, list visibility, role and version precedence,
both terminal branches, and amount boundaries; the remaining commands use
weighted deterministic selection with at most four created claims. This is
repeatable sequence coverage, not exhaustive verification or a claim of
property-based testing.

After each create or transition, the test independently reads the persisted
claim and its event history as reviewer. Rejected mutations also compare the
unchanged state or list. A mismatch stops at the first command and writes a
sanitized receipt under `work/audit-2026-10-21/F2/`: aliases replace runtime
IDs, errors and states are allowlisted, and tokens, headers, and response text
are excluded. The receipt retains the seed, command prefix, revisions, target
image identity, observed difference, and coverage counts. A replay uses a
fresh application namespace and remaps aliases to newly created IDs.

The hosted shared-app job also builds two isolated mutated provider candidates:
one permits reopening an approved claim, and one allows a member to read
another member's claim. Each control must fail at its expected command, retain
native JUnit evidence and a sanitized receipt, then reproduce the same failure
from that receipt in a fresh namespace. These controls test whether the model
detects those specific regressions; they do not measure an overall mutation
score.

The normal deterministic `contractTest` tier runs only the pure model and
receipt tests and does not start Docker. The API sequence and provider mutation
controls run in GitHub's isolated `shared-reference-app` job, with one Gradle
worker and the existing disposable application/database setup.
