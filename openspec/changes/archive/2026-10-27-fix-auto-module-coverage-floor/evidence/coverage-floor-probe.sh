#!/usr/bin/env bash
# coverage-floor-probe.sh — the red case of `fix-auto-module-coverage-floor`.
#
# The sensor registry's `coverage` row (tools/sensors/sensors.json, condition
# {"module":"auto"}, floor 70, signal `below-floor`) reads the per-module instruction
# coverage that tools/build-inputs.sh derives from the module's own Kover report. The
# derivation is in tools/build-inputs.sh:91-114: the *last* INSTRUCTION counter of the
# Kover document, `covered / (covered + missed)`.
#
# This probe applies exactly that arithmetic to a Kover XML and answers in the row's own
# idiom: exit 0 when the module is at or above the floor, exit 1 with the numbers when it
# is below. It writes a JUnit-shaped XML beside the answer so the case is a case: one
# test, failing while the module is below its floor.
#
# Usage: coverage-floor-probe.sh <kover-report.xml> <module> <floor> [<junit-out.xml>]
set -uo pipefail

report="${1:-}"
module="${2:-}"
floor="${3:-}"
out="${4:-}"

if [ -z "$report" ] || [ -z "$module" ] || [ -z "$floor" ]; then
  echo "usage: $0 <kover-report.xml> <module> <floor-percent> [<junit-out.xml>]" >&2
  exit 2
fi
if [ ! -f "$report" ]; then
  echo "coverage-floor-probe: no report at $report" >&2
  exit 2
fi

# The last INSTRUCTION counter of the document — the input tools/build-inputs.sh:91-114 reads.
read -r covered missed < <(
  tr '>' '>\n' < "$report" \
    | grep 'counter type="INSTRUCTION"' \
    | tail -1 \
    | sed -E 's/.*missed="([0-9]+)" covered="([0-9]+)".*/\2 \1/'
)

if [ -z "${covered:-}" ] || [ -z "${missed:-}" ]; then
  echo "coverage-floor-probe: no INSTRUCTION counter in $report" >&2
  exit 2
fi

read -r percentage verdict < <(awk -v c="$covered" -v m="$missed" -v f="$floor" 'BEGIN {
  total = c + m
  pct = (total > 0) ? (100 * c / total) : 0
  printf "%.1f %s", pct, ((pct + 0 >= f + 0) ? "pass" : "fail")
}')

case_name="theAutoModulesInstructionCoverageReachesTheFloor"
classname="coverage-floor-probe"
message="covered=$covered missed=$missed percentage=$percentage floor=$floor verdict=$verdict"

echo "$message"

if [ -n "$out" ]; then
  failures=0
  [ "$verdict" = "pass" ] || failures=1
  # The suite dates itself: without the attribute a reader can date the case only by the file's
  # mtime, which a copy or a checkout moves (round-1 review of this change, NOTE on scenario 1).
  timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  {
    printf '<?xml version="1.0" encoding="UTF-8"?>\n'
    printf '<testsuite name="%s" tests="1" skipped="0" failures="%s" errors="0" time="0.0" timestamp="%s">\n' \
      "$classname" "$failures" "$timestamp"
    printf '  <testcase classname="%s" name="%s" time="0.0">\n' "$classname" "$case_name"
    if [ "$failures" -ne 0 ]; then
      printf '    <failure message="%s">%s module=%s\n' "$message" "$message" "$module"
    fi
    printf '  </testcase>\n</testsuite>\n'
  } > "$out"
fi

[ "$verdict" = "pass" ] || exit 1
exit 0
