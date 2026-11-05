#!/usr/bin/env bash
# The structural checks this change owes (no compiled file changed, so these stand in for a build — see
# `guidelines/Build.md` §2, the scope block). Writes `checks.log` beside this script.
#
# Usage: bash openspec/changes/<change>/evidence/checks.sh
set -uo pipefail
root="$(cd "$(dirname "$0")/../../../.." && pwd)"
cd "$root" || exit 1
ev="$root/openspec/changes/integrate-sonar-analysis/evidence"
status=0
{
  echo "# structural checks for integrate-sonar-analysis ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  echo
  for cmd in \
    "bash tools/check-sonar-workflow.sh" \
    "bash tools/check-sonar-workflow-selftest.sh" \
    "bash tools/check-action-runtimes.sh" \
    "bash tools/check-action-runtimes-selftest.sh" \
    "bash tools/check-doc-routes.sh" \
    "bash tools/check-rule-routes.sh" \
    "bash tools/check-change-readiness.sh openspec/changes/integrate-sonar-analysis" \
    "bash tools/check-openspec-hygiene.sh" \
    "bash tools/gen-feature-list.sh --check-classification" \
    "bash tools/check-jvm-test-graph-selftest.sh" \
    "bash tools/check-dex-gate-graph-selftest.sh"
  do
    echo "\$ $cmd"
    $cmd 2>&1
    echo "exit=$?"
    echo
  done
} > "$ev/checks.log" 2>&1
cat "$ev/checks.log"
