#!/usr/bin/env bash
# coverage-floor-probe.sh — this item's red case, and the case that measures the delta afterwards.
#
# The item is "the osmscout-client-java module's coverage is below its floor". The floor is the coverage
# row's own bound (`tools/sensors/sensors.json`: threshold 70, comparison below-floor) and the number the
# row reads is the module's *instruction* coverage in the JaCoCo report, computed by
# `tools/build-inputs.sh`'s `xml_instruction_percentage` (the last root `<counter type="INSTRUCTION">`).
#
# Why the case is a probe and not a JUnit case: JaCoCo writes its execution data when the test JVM exits and
# `jacocoTestReport` reads it afterwards, so no case inside the suite can read the coverage of the run it is
# part of. The assertion therefore runs over the report, exactly as the sensor does in CI, and fails on a
# tree whose suite leaves the module below the floor.
#
# usage: coverage-floor-probe.sh <tree-root> <out.xml> [<label>]
# exit:  0 at or above the floor, 1 below it, 2 usage or a missing report.
set -uo pipefail

root="${1:-}"
out="${2:-}"
label="${3:-module coverage reaches its floor}"
floor=70

if [ -z "$root" ] || [ -z "$out" ]; then
  sed -n '2,16p' "$0" >&2
  exit 2
fi

report="$root/osmscout-client-java/build/reports/jacoco/test/jacocoTestReport.xml"
if [ ! -f "$report" ]; then
  printf 'coverage-floor-probe: no report at %s — run ./gradlew :osmscout-client-java:jacocoTestReport there first\n' \
    "$report" >&2
  exit 2
fi

counter=$(tr -d '\n' < "$report" | grep -o '<counter type="INSTRUCTION"[^>]*>' | tail -n 1)
missed=$(printf '%s' "$counter" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p')
covered=$(printf '%s' "$counter" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p')
percentage=$(awk -v missed="${missed:-0}" -v covered="${covered:-0}" 'BEGIN {
  total = missed + covered
  if (total <= 0) { print "0.0"; exit 0 }
  printf "%.1f", covered * 100 / total
}')

ok=$(awk -v p="$percentage" -v f="$floor" 'BEGIN { print (p + 0 >= f + 0) ? 1 : 0 }')
at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
if [ "$ok" = 1 ]; then
  verdict=passed
  failures=0
else
  verdict=failed
  failures=1
fi
printf 'LOOP-COVERAGE module=osmscout-client-java covered=%s missed=%s percentage=%s floor=%s verdict=%s at=%s\n' \
  "$covered" "$missed" "$percentage" "$floor" "$verdict" "$at"

mkdir -p "$(dirname "$out")"
cat > "$out" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="coverage-floor-probe" tests="1" failures="$failures" errors="0" time="0" timestamp="$at">
  <testcase name="$label" classname="coverage-floor-probe" time="0">
$(if [ "$failures" = 1 ]; then
  printf '    <failure message="osmscout-client-java instruction coverage %s%% is below the %s%% floor">covered=%s missed=%s of %s instructions (%s%%), floor %s%%, report %s</failure>\n' \
    "$percentage" "$floor" "$covered" "$missed" "$((covered + missed))" "$percentage" "$floor" "$report"
fi)
  </testcase>
  <system-out>LOOP-COVERAGE module=osmscout-client-java covered=$covered missed=$missed percentage=$percentage floor=$floor verdict=$verdict at=$at</system-out>
</testsuite>
XML

[ "$ok" = 1 ] || exit 1
exit 0
