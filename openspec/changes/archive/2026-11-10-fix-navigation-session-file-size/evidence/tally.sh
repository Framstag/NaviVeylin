#!/usr/bin/env bash
# tally.sh — sums the JUnit XML tallies of one Gradle test-result directory.
#
# Usage: bash tally.sh <dir>   (default: auto/build/test-results/testDebugUnitTest)
#
# JUnit's `<testsuite>` element carries its attributes in the order
# `name tests skipped failures errors timestamp hostname time`, so splitting the line on `"`
# puts the test count in field 4, skipped in 6, failures in 8 and errors in 10. One line per
# class and one total, so a reader can see which class a number came from.
set -uo pipefail
dir="${1:-auto/build/test-results/testDebugUnitTest}"
awk -F'"' '
  /<testsuite / {
    classes++
    tests += $4; skipped += $6; failures += $8; errors += $10
    printf "%s tests=%s failures=%s errors=%s\n", $2, $4, $8, $10
  }
  END { printf "TOTAL classes=%d tests=%d skipped=%d failures=%d errors=%d\n", classes, tests, skipped, failures, errors }
' "$dir"/*.xml
