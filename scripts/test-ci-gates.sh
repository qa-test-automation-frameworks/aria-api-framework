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

ok=(JAVA_POLICY_RESULT=success TEST_RESULT=success OSV_RESULT=success CONTAINER_RESULT=success SHARED_APP_RESULT=success CONTROL_RESULT=success CANDIDATE_SELECTED=false CANDIDATE_RESULT=skipped)

for event in push pull_request schedule workflow_dispatch; do
  dependency=skipped
  [ "$event" != pull_request ] || dependency=success
  base=(CI_EVENT="$event" "${ok[@]}" DEPENDENCY_REVIEW_RESULT="$dependency")
  check pass '' "${base[@]}"
  for disposition in failure cancelled skipped ''; do
    check fail 'java-runtime-policy must succeed' "${base[@]}" JAVA_POLICY_RESULT="$disposition"
    check fail 'test must succeed' "${base[@]}" TEST_RESULT="$disposition"
    check fail 'osv-scan must succeed' "${base[@]}" OSV_RESULT="$disposition"
    check fail 'container-test must succeed' "${base[@]}" CONTAINER_RESULT="$disposition"
    check fail 'shared-reference-app must succeed' "${base[@]}" SHARED_APP_RESULT="$disposition"
    check fail 'evidence-failure-control must succeed' "${base[@]}" CONTROL_RESULT="$disposition"
  done
done

base=(CI_EVENT=push "${ok[@]}" DEPENDENCY_REVIEW_RESULT=skipped)
for disposition in failure cancelled skipped ''; do
  check fail 'dependency-review must succeed' "${base[@]}" CI_EVENT=pull_request DEPENDENCY_REVIEW_RESULT="$disposition"
done
check fail 'dependency-review must be skipped outside a PR' "${base[@]}" DEPENDENCY_REVIEW_RESULT=success
check fail 'Unsupported or missing CI event' "${base[@]}" CI_EVENT=unknown

candidate=(CI_EVENT=workflow_dispatch CANDIDATE_SELECTED=true CANDIDATE_RESULT=success JAVA_POLICY_RESULT=success TEST_RESULT=success OSV_RESULT=success CONTAINER_RESULT=success SHARED_APP_RESULT=skipped CONTROL_RESULT=success DEPENDENCY_REVIEW_RESULT=skipped)
check pass '' "${candidate[@]}"
for disposition in failure cancelled skipped ''; do
  check fail 'shared-reference-app-candidate must succeed' "${candidate[@]}" CANDIDATE_RESULT="$disposition"
done
check fail 'shared-reference-app must be skipped when a candidate is selected' "${candidate[@]}" SHARED_APP_RESULT=success
check fail 'shared-reference-app-candidate must be skipped without a candidate selection' "${base[@]}" CANDIDATE_RESULT=success
check fail 'candidate_selected must be true or false' "${base[@]}" CANDIDATE_SELECTED=yes
echo "$checks CI gate controls passed"
