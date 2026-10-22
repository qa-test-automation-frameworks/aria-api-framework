#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REFERENCE_SOURCE=${1:-reference-app}
if [[ "$REFERENCE_SOURCE" != /* ]]; then
  REFERENCE_SOURCE="$REPO_ROOT/$REFERENCE_SOURCE"
fi
if [[ ! -d "$REFERENCE_SOURCE/.git" ]]; then
  echo "Expected the pinned quality-reference-application checkout at $REFERENCE_SOURCE" >&2
  exit 2
fi
if [[ $(git -C "$REFERENCE_SOURCE" rev-parse HEAD) != 21af1f41561a6d83ae44933dd36d9daa5e6a6e4c ]]; then
  echo "Mutation controls require the audited reference source revision 21af1f41561a6d83ae44933dd36d9daa5e6a6e4c" >&2
  exit 2
fi

cd "$REPO_ROOT"

EVIDENCE_DIR="$REPO_ROOT/work/audit-2026-10-21/A3/controls"
mkdir -p "$EVIDENCE_DIR"
: > "$EVIDENCE_DIR/summary.tsv"

run_control() {
  local name=$1
  local patch_file=$2
  local interaction=$3
  local expected=$4
  local image="quality-reference-application:a3-${name}-${GITHUB_RUN_ID:-local}"
  local candidate
  candidate=$(mktemp -d "${RUNNER_TEMP:-/tmp}/a3-${name}.XXXXXX")
  rm -rf "$EVIDENCE_DIR/$name"
  local log="$EVIDENCE_DIR/$name/hosted-verification.log"
  local report_dir="$EVIDENCE_DIR/$name/test-results"
  mkdir -p "$(dirname "$log")" "$report_dir"

  cleanup_candidate() {
    docker image rm "$image" >/dev/null 2>&1 || true
    rm -rf "$candidate"
  }
  trap cleanup_candidate RETURN

  git -C "$REFERENCE_SOURCE" archive HEAD | tar -x -C "$candidate"
  git -C "$candidate" apply "$REPO_ROOT/src/test/resources/pact-mutations/$patch_file"
  docker build \
    --build-arg "APP_SOURCE_SHA=a3-mutation-$name" \
    --tag "$image" \
    "$candidate"

  local verification_status=0
  set +e
  APP_IMAGE="$image" \
  APP_IMAGE_LOCAL=true \
  APP_SOURCE_REVISION="a3-mutation-$name" \
  APP_FIXTURE_VERSION="${APP_FIXTURE_VERSION:-v0.1.0}" \
  bash "$REPO_ROOT/scripts/with-reference-application.sh" \
    ./gradlew --no-daemon --max-workers=1 sharedReferencePactVerificationTest \
    2>&1 | tee "$log"
  verification_status=$?
  set -e

  if [[ -d "$REPO_ROOT/build/test-results/sharedReferencePactVerificationTest" ]]; then
    cp -R "$REPO_ROOT/build/test-results/sharedReferencePactVerificationTest/." "$report_dir/"
  fi

  python3 - "$report_dir" "$interaction" "$expected" "$verification_status" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

report_dir = pathlib.Path(sys.argv[1])
interaction = sys.argv[2]
expected = sys.argv[3]
command_status = int(sys.argv[4])
reports = sorted(report_dir.rglob("TEST-*.xml"))
if not reports:
    raise SystemExit(f"No native JUnit provider report was saved under {report_dir}")

cases = []
for report in reports:
    root = ET.parse(report).getroot()
    cases.extend(root.iter("testcase"))
matches = [case for case in cases if interaction in case.attrib.get("name", "")]
if not matches:
    raise SystemExit(f"The provider report did not identify interaction {interaction!r}")

failures = [node for case in matches for node in case if node.tag in ("failure", "error")]
if expected == "pass":
    if command_status != 0 or failures:
        raise SystemExit(f"The additive response control should pass; Gradle exit={command_status}")
    if any(node.tag in ("failure", "error") for case in cases for node in case):
        raise SystemExit("The additive response control had an unrelated provider interaction failure")
    print(f"PASS: additive response property kept {interaction!r} compatible")
elif expected == "fail":
    if command_status == 0 or not failures:
        raise SystemExit(f"The breaking mutation should fail {interaction!r}; Gradle exit={command_status}")
    details = "\n".join(
        f"{node.attrib.get('message', '')}\n{node.text or ''}"
        for node in failures
    )
    if "policy limit" in interaction.lower() and "claimAmountLimitMinor" not in details:
        raise SystemExit("The policy mutation failed without identifying claimAmountLimitMinor")
    print(f"PASS: breaking mutation failed the intended interaction {interaction!r}")
else:
    raise SystemExit(f"Unknown expected result {expected!r}")
PY

  printf '%s\t%s\t%s\n' "$name" "$expected" "$interaction" >> "$EVIDENCE_DIR/summary.tsv"
  trap - RETURN
  cleanup_candidate
}

run_control \
  remove-v2-policy-limit \
  a3-remove-v2-policy-limit.patch \
  "an authenticated v2 policy list" \
  fail
run_control \
  reduce-v1-claim-limit \
  a3-change-v1-claim-limit.patch \
  "a v1 claim at the one million minor-unit limit" \
  fail
run_control \
  add-policy-response-field \
  a3-add-policy-response-field.patch \
  "an authenticated v2 policy list" \
  pass
