#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
checks=0

check() {
  local expected=$1 diagnostic=$2
  shift 2
  local output status=0
  output=$(env -i PATH="$PATH" "$@" bash "$script_dir/verify-ci-gates.sh" 2>&1) || status=$?
  if [ "$expected" = pass ]; then
    [ "$status" -eq 0 ] && [[ "$output" == *'Required quality gates passed'* ]] || { echo "Positive control failed: $output" >&2; exit 1; }
  else
    [ "$status" -eq 1 ] && [[ "$output" == *"$diagnostic"* ]] || { echo "Wrong negative outcome: status=$status, output=$output" >&2; exit 1; }
  fi
  checks=$((checks + 1))
}

for event in push pull_request schedule workflow_dispatch; do
  dependency=skipped
  live=skipped
  skip_evidence=success
  [ "$event" != pull_request ] || dependency=success
  [ "$event" != schedule ] || live=success
  [ "$event" != schedule ] || skip_evidence=skipped
  base=(CI_EVENT="$event" RUN_LIVE_SMOKE=false TEST_RESULT=success OSV_RESULT=success CONTAINER_RESULT=success CONTROL_RESULT=success SKIP_EVIDENCE_RESULT="$skip_evidence" DEPENDENCY_REVIEW_RESULT="$dependency" LIVE_RESULT="$live")
  check pass '' "${base[@]}"
  for disposition in failure cancelled skipped ''; do
    check fail 'test must succeed' "${base[@]}" TEST_RESULT="$disposition"
    check fail 'osv-scan must succeed' "${base[@]}" OSV_RESULT="$disposition"
    check fail 'container-test must succeed' "${base[@]}" CONTAINER_RESULT="$disposition"
    check fail 'evidence-failure-control must succeed' "${base[@]}" CONTROL_RESULT="$disposition"
  done
done

base=(CI_EVENT=workflow_dispatch RUN_LIVE_SMOKE=true TEST_RESULT=success OSV_RESULT=success CONTAINER_RESULT=success CONTROL_RESULT=success SKIP_EVIDENCE_RESULT=skipped DEPENDENCY_REVIEW_RESULT=skipped LIVE_RESULT=success)
check pass '' "${base[@]}"
for event in schedule workflow_dispatch; do
  for disposition in failure cancelled skipped ''; do
    check fail 'live-smoke must succeed' "${base[@]}" CI_EVENT="$event" LIVE_RESULT="$disposition"
  done
done
check fail 'Not-requested evidence job must be skipped' "${base[@]}" SKIP_EVIDENCE_RESULT=success
for disposition in failure cancelled skipped ''; do
  check fail 'dependency-review must succeed' "${base[@]}" CI_EVENT=pull_request RUN_LIVE_SMOKE=false LIVE_RESULT=skipped DEPENDENCY_REVIEW_RESULT="$disposition"
done
check fail 'Unrequested live-smoke must be skipped' "${base[@]}" RUN_LIVE_SMOKE=false
check fail 'run_live_smoke must be true or false' "${base[@]}" RUN_LIVE_SMOKE=yes
check fail 'Unsupported or missing CI event' "${base[@]}" CI_EVENT=unknown
echo "$checks CI gate controls passed"
