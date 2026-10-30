#!/usr/bin/env bash
# red-on-head.sh — the red case of fix-worker-own-shell-write-is-staged-and-guarded, run on HEAD's content
# (issue #704).
#
# It runs the one case the change's delta names and prints a JUnit tally of it: `tests=1 failures=1` on HEAD,
# because `tools/loop/fixtures/pi` starts its action *after* a `cd "$LOOP_WORKTREE"` and carries no hook that
# forks a shell while the child still stands in the project — the cwd `run_child` gives every child
# (`tools/loop/run.sh:1140`) — so the case's write never happens and the difference it needs is never recorded.
# It is kept in the change's evidence so the red half is repeatable: on the change's own tree the same command
# prints `tests=1 failures=0`.
#
# Usage: bash evidence/red-on-head.sh   (from the change root; prints the XML on stdout, the log on stderr)
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || exit 2
cd "$root" || exit 2

cases=(
  'run#theWorkersOwnShellWriteIsRecordedAndNotPlaced'
)

tests=0
failures=0
messages=""
for c in "${cases[@]}"; do
  tests=$((tests + 1))
  out=$(bash tools/loop-selftest.sh --case "$c" 2>&1)
  if printf '%s\n' "$out" | grep -qE "^PASS[[:space:]]+$c\$"; then
    printf 'PASS %s\n' "$c" >&2
  else
    failures=$((failures + 1))
    # the suite prefixes a case's own lines with `<case>|`, so the assertion the case failed on is found by that
    # prefix and not by a wording of its own
    reason=$(printf '%s\n' "$out" | grep -m1 -F "$c|" | sed 's/^[^|]*|//; s/^[[:space:]]*//; s/[[:space:]]*$//')
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
