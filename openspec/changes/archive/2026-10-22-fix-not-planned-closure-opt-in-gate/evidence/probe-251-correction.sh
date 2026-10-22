#!/usr/bin/env bash
# The probe review round 1 owes: the requirement the delta rewrites must not carry the two claims the
# reviewer falsified — a completed closure that needs the opt-in "still" even when a declared check owns the
# item (`tools/sensor-cases.sh:798-809` closes its own issue with no `agent/eligible`), and a not-planned
# closure that "writes the settled state before the close" (the named case's fixture already carries the
# state, so `tools/issues/update.sh:149-151` writes nothing). The two cases that carry the evidence are run
# on the same probe.
set -uo pipefail
spec=openspec/specs/issue-backlog/spec.md
delta=openspec/changes/archive/2026-10-22-fix-not-planned-closure-opt-in-gate/specs/issue-backlog/spec.md
pass=0; fail=0
check() {
  if eval "$2" >/dev/null 2>&1; then printf 'PASS %s\n' "$1"; pass=$((pass+1))
  else printf 'FAIL %s\n' "$1"; fail=$((fail+1)); fi
}
# The requirement paragraph as both files state it, so a correction that lands in one and not the other fails.
req() { awk '/^The tooling SHALL act/{f=1} f && /^$/{exit} f' "$1"; }
check 'spec#completedClosureIsScopedToUnownedItems' "grep -q 'no declared check owns' $spec"
check 'spec#sensorPathNamesItsAuthorisation' "grep -q 'authorised by that check.s row' $spec"
check 'spec#scenarioDropsTheUnassertedWrite' "! grep -q 'the settled state is written before the close' $spec"
check 'spec#deltaMatchesTheMergedRequirement' 'diff <(req "$spec") <(req "$delta")'
check 'close#notPlannedNeedsNoOptIn' "bash tools/issue-tooling-selftest.sh --case 'close#notPlannedNeedsNoOptIn'"
check 'close#sensorScopeAllowsItsOwnIssue' "bash tools/sensor-selftest.sh --case 'close#sensorScopeAllowsItsOwnIssue'"
printf 'tests=%d failures=%d\n' "$((pass+fail))" "$fail"
[ "$fail" -eq 0 ]
