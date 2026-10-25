#!/usr/bin/env bash
# The red cases of `fix-blocked-item-names-its-stop-mechanism` (issue #280), run against whatever tree this
# script sits in — so the same four cases are the red half on the base commit and the green half after the fix:
#
#   bash openspec/changes/<change>/evidence/red-cases-280.sh            # the four cases, one suite line each
#   bash openspec/changes/<change>/evidence/red-cases-280.sh --list     # the names only
#
# Each case is one named selection of `tools/loop-selftest.sh`, so each run prints the suite's own summary line
# (`1 of 1 cases failed` / `all 1 cases passed`), which is the tally this evidence quotes.
set -uo pipefail
root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
cases=(
  'state#aBlockedItemNamesTheMechanismThatStoppedIt'
  'run#reviewRoundsCapAtTwo'
  'run#doesNotCloseOnAMergeTheBaseCannotTake'
  'report#wallTimeAndReasonInTheTable'
)
if [ "${1:-}" = "--list" ]; then printf '%s\n' "${cases[@]}"; exit 0; fi
status=0
for c in "${cases[@]}"; do
  printf '=== %s\n' "$c"
  bash "$root/tools/loop-selftest.sh" --case "$c" 2>&1 | tail -8
  [ "${PIPESTATUS[0]}" -eq 0 ] || status=1
done
exit "$status"
