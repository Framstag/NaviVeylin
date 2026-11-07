#!/usr/bin/env bash
# round-2-gate-sum.sh — the gate's own summary line, as one tally, so the number a note quotes has a carrier XML
# beside it (`tools/loop/evidence-check.sh`, C2: a cited `tests=N failures=M` pair must be carried by a JUnit XML
# under the change's evidence/).
#
# `tools/loop/gate.sh` prints one `LOOP-GATE:` line summing the suites it ran and writes one tally per suite; it
# writes no combined file. This sums the tallies the round-2 gate left in `gate-tallies-round-2/` with the same
# rule the gate uses — `tests` and `failures` added up over every suite, an `errors` count beside them — and
# prints the XML to stdout. It derives, it does not invent: every number is one of the tallies' own.
#
# Usage: round-2-gate-sum.sh [<tally dir>]      # default: gate-tallies-round-2 beside this script
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
dir="${1:-$here/gate-tallies-round-2}"
[ -d "$dir" ] || { echo "round-2-gate-sum: no tally directory at $dir" >&2; exit 2; }

awk '
  /<testsuite / {
    name = ""; tests = 0; failures = 0; errors = 0; time = 0; suites++
    if (match($0, /name="[^"]*"/))  { name = substr($0, RSTART + 6, RLENGTH - 7) }
    if (match($0, /tests="[0-9]+"/))    { tests    = substr($0, RSTART + 7, RLENGTH - 8) + 0 }
    if (match($0, /failures="[0-9]+"/)) { failures = substr($0, RSTART + 10, RLENGTH - 11) + 0 }
    if (match($0, /errors="[0-9]+"/))   { errors   = substr($0, RSTART + 8, RLENGTH - 9) + 0 }
    if (match($0, /time="[0-9.]+"/))    { time     = substr($0, RSTART + 6, RLENGTH - 7) + 0 }
    total_tests += tests; total_failures += failures; total_errors += errors; total_time += time
    suite_list = suite_list (suite_list == "" ? "" : ", ") name " tests=" tests
  }
  END {
    printf "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    printf "<testsuite name=\"tools/loop/gate.sh\" tests=\"%d\" failures=\"%d\" errors=\"%d\" time=\"%.0f\">\n",
      total_tests, total_failures, total_errors, total_time
    printf "  <system-out>the gate summary summed over %d suite(s), each tally kept beside this file: %s</system-out>\n",
      suites, suite_list
    printf "</testsuite>\n"
  }' "$dir"/*.xml
