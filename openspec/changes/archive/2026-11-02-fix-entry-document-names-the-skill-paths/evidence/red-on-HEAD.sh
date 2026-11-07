#!/usr/bin/env bash
# red-on-HEAD.sh — the red case, run against the run's base with only the case added.
#
# The case `routed-read#theEntryDocumentNamesTheLoopSkillsByPath` reads this repository's entry document, so its
# first leg fails for a reason that is in the document, not in the check. The tree this probe builds is the
# base's: its `AGENTS.md`, its `.pi/skills/`, its `tools/check-doc-routes.sh` — plus this change's
# `tools/check-doc-routes-selftest.sh`, which carries the case. Nothing in this working tree is written.
#
# Usage: red-on-HEAD.sh [base] [out-dir]   # default base 9318dad9, out-dir beside this script
set -uo pipefail
base="${1:-9318dad90896e1e14364f71e5d6ae119def8f330}"
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(cd "$here/../../../.." && pwd)
out_dir="${2:-$here}"
case_id='routed-read#theEntryDocumentNamesTheLoopSkillsByPath'

scratch=$(mktemp -d "${TMPDIR:-/tmp}/red-on-HEAD.XXXXXX") || exit 2
trap 'rm -rf "$scratch"' EXIT

git -C "$root" archive "$base" AGENTS.md guidelines tools/check-doc-routes.sh .pi/skills | tar -x -C "$scratch" || exit 2
cp "$root/tools/check-doc-routes-selftest.sh" "$scratch/tools/check-doc-routes-selftest.sh"

bash "$scratch/tools/check-doc-routes-selftest.sh" --case "$case_id" > "$scratch/run.log" 2>&1
rc=$?
tests=1
failures=0
[ "$rc" = 0 ] || failures=1
summary=$(sed -n 's/^check-doc-routes-selftest: //p' "$scratch/run.log" | tail -1)

{
  printf '# %s — run against base %s\n' "$case_id" "$base"
  printf '# %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '# exit=%s\n\n' "$rc"
  cat "$scratch/run.log"
} > "$out_dir/red-on-HEAD.log"
printf '<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="tools/check-doc-routes-selftest.sh --case %s" tests="%s" failures="%s" errors="0" time="0">\n  <system-out>%s</system-out>\n</testsuite>\n' \
  "$case_id" "$tests" "$failures" "$(sed 's/[<&>]/ /g' <<< "$summary")" > "$out_dir/red-on-HEAD.xml"

printf 'RED-CHECK %s: exit=%s tests=%s failures=%s\n' "$case_id" "$rc" "$tests" "$failures"
[ "$rc" = 1 ] && [ "$failures" -gt 0 ]
