#!/usr/bin/env bash
# The wall times the loop's own finished items cost, by result — the measurement `guidelines/Backlog.md` §7's
# "What an item costs, and the floor it is refused by" quotes, and the source of the "skipped median 107 s" in
# `design.md` and in issue #674's recorded decision.
#
# Run from the project root (the runs' history is the project's, not a change root's):
#   bash openspec/changes/<this change>/evidence/wall-by-result.sh
# It reads `.pi/issue-loop/runs/*.json` — the run states the loop keeps — and reduces them to one line per result.
set -uo pipefail

runs="${1:-.pi/issue-loop/runs}"

jq -r '.items[]? | select((.metrics.wallSeconds // "") != "")
       | [.issue, .result, .metrics.wallSeconds] | @tsv' "$runs"/*.json |
  sort -u |
  while IFS=$'\t' read -r _issue result wall; do
    printf '%s\t%s\n' "$result" "$wall"
  done |
  sort -k1,1 -k2,2g |
  awk -F'\t' '
    $1 != cur { if (cur != "") report(); cur = $1; n = 0; delete a }
    { a[++n] = $2 }
    END { if (cur != "") report() }
    function report(   med) {
      med = (n % 2) ? a[(n + 1) / 2] : (a[n / 2] + a[n / 2 + 1]) / 2
      printf "%s n=%d min=%s median=%s max=%s\n", cur, n, a[1], med, a[n]
    }'
