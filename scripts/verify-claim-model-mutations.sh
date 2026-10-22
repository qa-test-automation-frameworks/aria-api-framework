#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REFERENCE_SOURCE=${1:-reference-app}
if [[ "$REFERENCE_SOURCE" != /* ]]; then
  REFERENCE_SOURCE="$REPO_ROOT/$REFERENCE_SOURCE"
fi
EXPECTED_REFERENCE_SOURCE=21af1f41561a6d83ae44933dd36d9daa5e6a6e4c
if [[ ! -d "$REFERENCE_SOURCE/.git" ]]; then
  echo "Expected the pinned quality-reference-application checkout at $REFERENCE_SOURCE" >&2
  exit 2
fi
if [[ $(git -C "$REFERENCE_SOURCE" rev-parse HEAD) != "$EXPECTED_REFERENCE_SOURCE" ]]; then
  echo "F2 controls require the audited reference source revision $EXPECTED_REFERENCE_SOURCE" >&2
  exit 2
fi

cd "$REPO_ROOT"
EVIDENCE_ROOT="$REPO_ROOT/work/audit-2026-10-21/F2/controls"
mkdir -p "$EVIDENCE_ROOT"

run_control() {
  local name=$1
  local patch_file=$2
  local expected_index=$3
  local image="quality-reference-application:f2-${name}-${GITHUB_RUN_ID:-local}"
  local candidate
  candidate=$(mktemp -d "${RUNNER_TEMP:-/tmp}/f2-${name}.XXXXXX")
  local control_dir="$EVIDENCE_ROOT/$name"
  rm -rf "$control_dir"
  mkdir -p "$control_dir/test-results/initial" "$control_dir/test-results/replay"

  cleanup_candidate() {
    docker image rm "$image" >/dev/null 2>&1 || true
    rm -rf "$candidate"
  }
  trap cleanup_candidate RETURN

  git -C "$REFERENCE_SOURCE" archive HEAD | tar -x -C "$candidate"
  git -C "$candidate" apply "$REPO_ROOT/src/test/resources/claim-model-mutations/$patch_file"
  docker build \
    --build-arg "APP_SOURCE_SHA=f2-mutation-$name" \
    --tag "$image" \
    "$candidate"
  local image_id
  image_id=$(docker image inspect --format '{{.Id}}' "$image")
  local receipt="$control_dir/counterexample.json"
  local verification_status=0

  set +e
  APP_IMAGE="$image" \
  APP_IMAGE_LOCAL=true \
  APP_SOURCE_REVISION="f2-mutation-$name" \
  APP_IMAGE_DIGEST="$image_id" \
  FRAMEWORK_SOURCE_REVISION="${FRAMEWORK_SOURCE_REVISION:-${GITHUB_SHA:-unknown}}" \
  CLAIM_MODEL_CONTROL_NAME="$name" \
  bash "$REPO_ROOT/scripts/with-reference-application.sh" \
    ./gradlew --no-daemon --max-workers=1 sharedReferenceAppTest \
    --tests com.aria.framework.contracts.claims.ReferenceApplicationStateMachineTest \
    > "$control_dir/initial-run.log" 2>&1
  verification_status=$?
  set -e
  if [[ -d "$REPO_ROOT/build/test-results/sharedReferenceAppTest" ]]; then
    cp "$REPO_ROOT"/build/test-results/sharedReferenceAppTest/TEST-*.xml "$control_dir/test-results/initial/" 2>/dev/null || true
  fi

  if [[ ! -s "$receipt" ]]; then
    echo "The $name mutation did not retain a first-divergence receipt." >&2
    exit 1
  fi

  local replay_status=0
  set +e
  APP_IMAGE="$image" \
  APP_IMAGE_LOCAL=true \
  APP_SOURCE_REVISION="f2-mutation-$name" \
  APP_IMAGE_DIGEST="$image_id" \
  FRAMEWORK_SOURCE_REVISION="${FRAMEWORK_SOURCE_REVISION:-${GITHUB_SHA:-unknown}}" \
  CLAIM_MODEL_CONTROL_NAME="$name" \
  CLAIM_MODEL_REPLAY_RECEIPT="$receipt" \
  bash "$REPO_ROOT/scripts/with-reference-application.sh" \
    ./gradlew --no-daemon --max-workers=1 sharedReferenceAppTest \
    --tests com.aria.framework.contracts.claims.ReferenceApplicationStateMachineTest \
    > "$control_dir/replay-run.log" 2>&1
  replay_status=$?
  set -e
  if [[ -d "$REPO_ROOT/build/test-results/sharedReferenceAppTest" ]]; then
    cp "$REPO_ROOT"/build/test-results/sharedReferenceAppTest/TEST-*.xml "$control_dir/test-results/replay/" 2>/dev/null || true
  fi

  python3 "$REPO_ROOT/scripts/validate-claim-model-control.py" \
    --name "$name" \
    --receipt "$receipt" \
    --replay-receipt "$control_dir/replay.json" \
    --initial-junit "$control_dir/test-results/initial" \
    --replay-junit "$control_dir/test-results/replay" \
    --initial-exit "$verification_status" \
    --replay-exit "$replay_status" \
    --expected-index "$expected_index" \
    --framework-revision "${FRAMEWORK_SOURCE_REVISION:-${GITHUB_SHA:-unknown}}" \
    --target-revision "f2-mutation-$name" \
    --target-digest "$image_id" \
    --output "$control_dir/control-summary.json"
  trap - RETURN
  cleanup_candidate
}

run_control allow-terminal-reopen allow-terminal-reopen.patch 13
run_control allow-foreign-member-read allow-foreign-member-read.patch 2
