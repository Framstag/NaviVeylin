#!/usr/bin/env bash
# Device-free self-test for the gate timing record and its report — change
# `speed-up-build-test-gate`, tasks 1.2/1.3. Runs on checked-in fixtures only, no Gradle,
# no device. `AGENTS.md` requires a self-test that runs without a device for every tool.
#
#   bash tools/gate-timings-selftest.sh
#
# Exit code 0 = every assertion held; 1 = at least one failed (the failing assertions print
# the report output they saw).
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIX="$HERE/fixtures/gate-timings"
REPORT="$HERE/gate-timing-report.sh"

failures=0
ok()  { echo "ok:   $*"; }
bad() { echo "FAIL: $*"; failures=$((failures + 1)); }

assert_contains() { # file pattern label
    if grep -qF -- "$2" "$1"; then
        ok "$3"
    else
        bad "$3 — '$2' is not in the report:"
        sed 's/^/      | /' "$1"
    fi
}

out="$(mktemp)"
trap 'rm -f "$out"' EXIT

# 1. A record with executed tasks: phases, outcome grouping and per-suite numbers.
if bash "$REPORT" "$FIX/gate-timings.json" "$FIX/root" >"$out" 2>&1; then
    ok "report exits 0 on a record with executed tasks"
else
    bad "report exited non-zero on a valid record"
    sed 's/^/      | /' "$out"
fi
assert_contains "$out" "wall 9m02s" "wall time read from the record"
assert_contains "$out" "verdict: no failed task" "verdict read from the record"
assert_contains "$out" "from-cache" "outcome grouping names the cached task"
assert_contains "$out" "6m20s" "test phase sums the three executed test tasks"
assert_contains "$out" "1m01s" "compile phase sums the compile task"
assert_contains "$out" "1m36s" "unaccounted configuration and overhead is reported"
assert_contains "$out" "2 classes  3 tests  1 failures  0 errors" "suite tally read from the result XML"
assert_contains "$out" "15.5s" "suite wall time from the first and last XML timestamp"
assert_contains "$out" "no result XML under" "an executed test task without result XML is flagged"

# 2. The falsification case: a record whose tasks did not execute is not evidence.
if bash "$REPORT" "$FIX/empty-tasks.json" "$FIX/root" >"$out" 2>&1; then
    bad "report exited 0 for a record with no executed task"
else
    ok "report exits non-zero for a record with no executed task"
fi
assert_contains "$out" "no executed task" "the empty-record failure names its reason"

# 3. A missing record is reported, not silently ignored.
if bash "$REPORT" "$FIX/does-not-exist.json" "$FIX/root" >"$out" 2>&1; then
    bad "report exited 0 for a missing record"
else
    ok "report exits non-zero for a missing record"
fi
assert_contains "$out" "no timing record" "the missing-record failure names its reason"

if [ "$failures" -gt 0 ]; then
    echo "gate-timings-selftest: $failures check(s) failed"
    exit 1
fi
echo "gate-timings-selftest: all checks passed"
