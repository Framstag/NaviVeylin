#!/usr/bin/env bash
# check-gate-coverage-selftest.sh — the focused run of the cases that own `tools/check-gate-coverage.sh` (issue #943).
#
# The cases live in `tools/loop/tests/loop-cases.sh`, beside the `gate#*` group they belong to, because they are cases
# about what the gate owes for a path and `bash tools/loop/tests/loop-selftest.sh` is what CI and the gate run. A second
# driver here would be a second place for the assertions to live and a suite nothing runs; what a reader needs instead
# is the name of the subset, so the check can be iterated without knowing a case name:
#
#   gate#aPathNoGlobNamesIsRefused                an unnamed path fails a throwaway tree, an allowlisted one passes,
#                                                 and an entry that states no reason is refused
#   gate#aDeltaTouchingAnyPathOwesTheCoverageStep  the profile owes the check for a path no other step is owed for
#
# Usage: bash tools/tests/check-gate-coverage-selftest.sh [--list] [--case <id>]
# Exit: the driver's own status — 0 every case passed, 1 a case failed, 2 the environment is unusable.
set -uo pipefail

root=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel 2>/dev/null) || root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
driver="$root/tools/loop/tests/loop-selftest.sh"
[ -f "$driver" ] || { printf 'check-gate-coverage-selftest: no driver at %s\n' "$driver" >&2; exit 2; }

filters=('gate#aPathNoGlobNamesIsRefused' 'gate#aDeltaTouchingAnyPathOwesTheCoverageStep')
while [ $# -gt 0 ]; do
  case "$1" in
    --list) printf '%s\n' "${filters[@]}"; exit 0 ;;
    --case) [ $# -ge 2 ] && [ "${2#--}" = "$2" ] || { printf 'check-gate-coverage-selftest: %s needs a value\n' "$1" >&2; exit 2; }
            filters=("$2"); shift 2 ;;
    *) printf 'check-gate-coverage-selftest: unknown argument %s\n' "$1" >&2; exit 2 ;;
  esac
done

# One case set per invocation: the driver refuses a second suite in one repository (issue #295), so the filters are run
# one after another and never at once. The first failure is kept and the rest still run.
status=0
for filter in "${filters[@]}"; do
  bash "$driver" --case "$filter" || status=$?
done
exit "$status"
