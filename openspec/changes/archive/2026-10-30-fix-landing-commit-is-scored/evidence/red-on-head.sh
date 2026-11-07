#!/usr/bin/env bash
# red-on-head.sh — the red case of fix-landing-commit-is-scored, run on the base commit's content (issue #709).
#
# It runs the four cases the change's delta names, one suite invocation each, and prints a JUnit tally of them:
# `tests=4 failures=4` on HEAD, because `state.sh done` has no `--merged-sha` and no `score-landings` command and
# `report.sh` prints the tracker pointer instead of a number. It is kept in the change's evidence so the red half
# is repeatable: on the change's own tree the same command prints `tests=4 failures=0`.
#
# Usage: bash evidence/red-on-head.sh   (from the change root; prints the XML on stdout, the log on stderr)
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || exit 2
cd "$root" || exit 2

cases=(
  'state#doneRecordsTheCommitItLanded'
  'state#theLandingScoreIsReadOnceAndStored'
  'state#aLandingWithNoRunIsUnscored'
  'report#landingsAreTheStoredNumber'
)

tests=0
failures=0
messages=""
for c in "${cases[@]}"; do
  tests=$((tests + 1))
  out=$(bash tools/loop-selftest.sh --case "$c" 2>&1)
  if printf '%s\n' "$out" | grep -q "^PASS  $c"; then
    printf 'PASS %s\n' "$c" >&2
  else
    failures=$((failures + 1))
    # the suite prefixes a case's own lines with `<case>|`, so the engine's refusal is found by its own prefix
    reason=$(printf '%s\n' "$out" | grep -m1 -o 'loop: [^|]*' | sed 's/[[:space:]]*$//')
    printf 'FAIL %s — %s\n' "$c" "$reason" >&2
    messages="$messages<failure message=\"$(printf '%s' "$reason" | sed 's/[&<>"]//g')\">$c: $(printf '%s' "$reason" | sed 's/[&<>"]//g')</failure>"
    messages="$messages
"
  fi
done

printf 'tests=%d failures=%d\n' "$tests" "$failures" >&2
printf '<?xml version="1.0" encoding="UTF-8"?>\n'
printf '<testsuite name="%s" tests="%d" failures="%d" errors="0" timestamp="%s">\n' \
  "$(basename "$here")/red-on-head.sh" "$tests" "$failures" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf '%s' "$messages"
printf '</testsuite>\n'
[ "$failures" -eq 0 ]
