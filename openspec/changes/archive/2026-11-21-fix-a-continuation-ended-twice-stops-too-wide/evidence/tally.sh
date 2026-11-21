#!/usr/bin/env bash
# The tallies this change cites, in the shape the gate writes them (`tools/loop/gate.sh`, the tally block): the case
# count and the failure count are the suite driver's own summary line, read from its output and never restated by
# hand, and the timestamp is the instant the run started.
#
#   bash evidence/tally.sh '<group#case>' <out-prefix>
#
# Writes `<out-prefix>.xml` and leaves the driver's own output in `<out-prefix>.log` beside it.
set -uo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)
case_name="$1"
out="${2:-tally}"
started=$(date -u +%Y-%m-%dT%H:%M:%SZ)
started_s=$(date +%s)

bash "$root/tools/loop/tests/loop-selftest.sh" --case "$case_name" > "$out.log" 2>&1
rc=$?
took=$(( $(date +%s) - started_s ))

n=$(sed -n 's/^loop-selftest: [0-9][0-9]* of \([0-9][0-9]*\) cases* failed.*/\1/p' "$out.log" | tail -1)
f=$(sed -n 's/^loop-selftest: \([0-9][0-9]*\) of [0-9][0-9]* cases* failed.*/\1/p' "$out.log" | tail -1)
case "$n" in ''|*[!0-9]*) n=1 ;; esac
case "$f" in ''|*[!0-9]*) if [ "$rc" -eq 0 ]; then f=0; else f=1; fi ;; esac

printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="%s" tests="%s" failures="%s" errors="0" time="%s" run="%s">\n  <system-out>%s</system-out>\n</testsuite>\n' \
  "loop-selftest.sh --case $case_name" "$n" "$f" "$took" "$started" \
  "$(tail -3 "$out.log" | tr '\n' ' ' | cut -c1-400)" > "$out.xml"
printf '%s: tests=%s failures=%s (%s)\n' "$case_name" "$n" "$f" "$started"
exit "$rc"
