#!/usr/bin/env bash
# The focused run's tally: one row per result XML the run produced, so the cited aggregate
# (cases, failures, errors) is readable class by class without carrying all 115 XMLs into the
# archive. Four of them are copied whole beside this file — one per seam the split created.
#
# Usage: focused-tally.sh <results-dir>
set -uo pipefail
dir="${1:?usage: focused-tally.sh <results-dir>}"
echo "# focused run tally — $dir"
echo "# file                                             tests failures errors timestamp"
awk '
  /<testsuite / {
    name=""; t=0; f=0; e=0; ts=""
    for (i = 1; i <= NF; i++) {
      if ($i ~ /^name=/)   { name = $i; sub(/^name="/, "", name); sub(/"$/, "", name) }
      if ($i ~ /^tests=/)  { t = $i; gsub(/[^0-9]/, "", t) }
      if ($i ~ /^failures=/) { f = $i; gsub(/[^0-9]/, "", f) }
      if ($i ~ /^errors=/) { e = $i; gsub(/[^0-9]/, "", e) }
      if ($i ~ /^timestamp=/) { ts = $i; sub(/^timestamp="/, "", ts); sub(/"$/, "", ts) }
    }
    printf "%-50s %6d %8d %6d %s\n", name, t, f, e, ts
    T += t; F += f; E += e
  }
  END { printf "# total: %d cases, %d failures, %d errors\n", T, F, E }
' "$dir"/*.xml | sort -k1,1
