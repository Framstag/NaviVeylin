#!/usr/bin/env bash
# The probe the delta owes: the requirement the item names must state the not-planned exemption and name
# the case that exercises it, while that case is green.
set -uo pipefail
spec=openspec/specs/issue-backlog/spec.md
pass=0; fail=0
check() {
  if eval "$2" >/dev/null 2>&1; then printf 'PASS %s\n' "$1"; pass=$((pass+1))
  else printf 'FAIL %s\n' "$1"; fail=$((fail+1)); fi
}
check 'spec#notPlannedExemptionIsStated' "grep -qi 'not-planned' $spec"
check 'spec#notPlannedScenarioNamesItsCase' "grep -q 'close#notPlannedNeedsNoOptIn' $spec"
check 'close#notPlannedNeedsNoOptIn' "bash tools/issue-tooling-selftest.sh --case 'close#notPlannedNeedsNoOptIn'"
printf 'tests=%d failures=%d\n' "$((pass+fail))" "$fail"
[ "$fail" -eq 0 ]
