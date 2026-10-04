#!/usr/bin/env bash
set -euo pipefail

fail() { echo "$1" >&2; exit 1; }

case "${CI_EVENT:-}" in
  push|pull_request|schedule|workflow_dispatch) ;;
  *) fail 'Unsupported or missing CI event' ;;
esac
case "${RUN_LIVE_SMOKE:-false}" in
  true|false) ;;
  *) fail 'run_live_smoke must be true or false' ;;
esac

require_success() {
  [ "$2" = success ] || fail "$1 must succeed; actual=$2"
}

require_success test "${TEST_RESULT:-missing}"
require_success osv-scan "${OSV_RESULT:-missing}"
require_success container-test "${CONTAINER_RESULT:-missing}"
require_success evidence-failure-control "${CONTROL_RESULT:-missing}"

if [ "$CI_EVENT" = pull_request ]; then
  require_success dependency-review "${DEPENDENCY_REVIEW_RESULT:-missing}"
else
  [ "${DEPENDENCY_REVIEW_RESULT:-missing}" = skipped ] || fail 'dependency-review must be skipped outside a PR'
fi

live_required=false
if [ "$CI_EVENT" = schedule ] || { [ "$CI_EVENT" = workflow_dispatch ] && [ "${RUN_LIVE_SMOKE:-false}" = true ]; }; then
  live_required=true
fi
if [ "$live_required" = true ]; then
  require_success live-smoke "${LIVE_RESULT:-missing}"
  [ "${SKIP_EVIDENCE_RESULT:-missing}" = skipped ] || fail 'Not-requested evidence job must be skipped when live smoke is required'
else
  [ "${LIVE_RESULT:-missing}" = skipped ] || fail 'Unrequested live-smoke must be skipped'
  require_success 'not-requested evidence' "${SKIP_EVIDENCE_RESULT:-missing}"
fi

echo "Required quality gates passed (event=$CI_EVENT, live-required=$live_required)"
