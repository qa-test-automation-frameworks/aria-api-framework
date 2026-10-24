#!/usr/bin/env bash
set -euo pipefail

fail() { echo "$1" >&2; exit 1; }

case "${CI_EVENT:-}" in
  push|pull_request|schedule|workflow_dispatch) ;;
  *) fail 'Unsupported or missing CI event' ;;
esac
case "${CANDIDATE_SELECTED:-false}" in
  true|false) ;;
  *) fail 'candidate_selected must be true or false' ;;
esac

require_success() {
  [ "$2" = success ] || fail "$1 must succeed; actual=$2"
}

require_success java-runtime-policy "${JAVA_POLICY_RESULT:-missing}"
require_success test "${TEST_RESULT:-missing}"
require_success osv-scan "${OSV_RESULT:-missing}"
require_success container-test "${CONTAINER_RESULT:-missing}"
if [ "${CANDIDATE_SELECTED:-false}" = true ]; then
  [ "${SHARED_APP_RESULT:-missing}" = skipped ] || fail 'shared-reference-app must be skipped when a candidate is selected'
  require_success shared-reference-app-candidate "${CANDIDATE_RESULT:-missing}"
else
  require_success shared-reference-app "${SHARED_APP_RESULT:-missing}"
  [ "${CANDIDATE_RESULT:-missing}" = skipped ] || fail 'shared-reference-app-candidate must be skipped without a candidate selection'
fi
require_success evidence-failure-control "${CONTROL_RESULT:-missing}"

if [ "$CI_EVENT" = pull_request ]; then
  require_success dependency-review "${DEPENDENCY_REVIEW_RESULT:-missing}"
else
  [ "${DEPENDENCY_REVIEW_RESULT:-missing}" = skipped ] || fail 'dependency-review must be skipped outside a PR'
fi

echo "Required quality gates passed (event=$CI_EVENT)"
