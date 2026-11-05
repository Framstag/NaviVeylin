#!/usr/bin/env bash
# red-on-HEAD.sh — the red case run against the run's base, with only the case added.
#
# The case `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` drives the real engine, so its red leg reads
# `tools/loop/run.sh` as the base commit has it: there the guard that keeps a child which never read the item off
# the item's side is keyed on a non-zero status, so an exit-0 child with an empty answer falls through to the
# outcome dispatch and is recorded `blocked` and escalated. The tree this script builds is the base's — its
# `run.sh`, its profile, its prompts, its fixture — plus this change's `tools/loop-cases.sh`, which carries the
# case. Nothing in the working tree is written.
#
# Usage: red-on-HEAD.sh [base] [out-dir]   # default base 412ec81d (the run's own), out-dir beside this script
set -uo pipefail
base="${1:-412ec81d232f1f3428bf23cfb1eafe343cbdeec7}"
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
out_dir="${2:-$here}"
case_id='run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault'

scratch=$(mktemp -d "${TMPDIR:-/tmp}/red-on-HEAD.XXXXXX") || exit 2
trap 'rm -rf "$scratch"' EXIT

# The base's own tree, so every script the case runs is the base's except the case itself.
git -C "$root" archive "$base" | tar -x -C "$scratch" || exit 2
cp "$root/tools/loop-cases.sh" "$scratch/tools/loop-cases.sh" || exit 2

bash "$scratch/tools/loop-selftest.sh" --case "$case_id" > "$scratch/run.log" 2>&1
rc=$?
tests=1
failures=0
[ "$rc" = 0 ] || failures=1
summary=$(sed -n 's/^loop-selftest: //p' "$scratch/run.log" | tail -1)

{
  printf '# %s — run against base %s\n' "$case_id" "$base"
  printf '# %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '# exit=%s\n\n' "$rc"
  cat "$scratch/run.log"
} > "$out_dir/red-on-HEAD.log"
printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="tools/loop-selftest.sh --case %s" tests="%s" failures="%s" errors="0" time="0" timestamp="%s">\n  <system-out>%s</system-out>\n</testsuite>\n' \
  "$case_id" "$tests" "$failures" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(sed 's/[<&>]/ /g' <<< "$summary")" \
  > "$out_dir/red-on-HEAD.xml"

printf 'RED-CHECK %s: exit=%s tests=%s failures=%s\n' "$case_id" "$rc" "$tests" "$failures"
[ "$rc" = 1 ] && [ "$failures" -gt 0 ]
