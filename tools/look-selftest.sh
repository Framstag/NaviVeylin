#!/usr/bin/env bash
# look-selftest.sh — the focused run of the cases that own `tools/look.sh` and `tools/pi-shell-refusal.mjs` (issue #687).
#
# Why this file exists and is not a suite of its own: those cases belong to the loop's suite, in `tools/loop-cases.sh`,
# because they are cases about what a loop child is started with and what its answer may carry — the same subject as
# the `adapter#*` and `trim#*` cases beside them — and `bash tools/loop-selftest.sh` is what CI and the gate run. A
# second driver here would be a second place for the assertions to live and a suite nothing runs. What a reader needs
# instead is the name of that subset, so the two tools can be iterated without knowing a case name:
#
#   look#*  one call answers a tree question across the worktree and the submodule, inside the recorded bound
#   policy#* a shell tree search is refused, a range read and a stream filter are not
#   adapter#* what the child is started with, the shell policy among it
#
# Usage: bash tools/look-selftest.sh [--list]
# Exit: the driver's own status — 0 every case passed, 1 a case failed, 2 the environment is unusable.
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
driver="$here/loop-selftest.sh"
[ -f "$driver" ] || { printf 'look-selftest: no driver at %s\n' "$driver" >&2; exit 2; }

filters=('look#*' 'policy#*' 'adapter#*')
case "${1:-}" in
  '') ;;
  --list) printf '%s\n' "${filters[@]}"; exit 0 ;;
  *) printf 'look-selftest: unknown argument %s\n' "$1" >&2; exit 2 ;;
esac

# One case set per invocation: the driver refuses a second suite in one repository (issue #295), so the filters are
# run one after another and never at once. The first failure is kept and the rest still run, because a red case in one
# group is no reason to hide the verdict of the others.
status=0
for filter in "${filters[@]}"; do
  bash "$driver" --case "$filter" || status=$?
done
exit "$status"
