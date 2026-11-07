#!/usr/bin/env bash
# red-on-head.sh — the red case of fix-worker-own-shell-write-is-staged-and-guarded, run on the base tree's content
# (issue #704).
#
# It runs the one case the change's delta names and prints a JUnit tally of it: `tests=1 failures=1` on the base,
# because `tools/loop/fixtures/pi` there starts its action *after* a `cd "$LOOP_WORKTREE"` and carries no hook that
# forks a shell while the child still stands in the project — the cwd `run_child` gives every child
# (`tools/loop/run.sh:1140`) — so the case's write never happens and the difference it needs is never recorded.
#
# The base fixture is installed by this script and the tree's own copy is restored on exit, so the red half is
# repeatable from the archive alone: the earlier round ran the restore by hand and the archived script could not
# reproduce its own number (review round 1, note 2).
#
# Usage: bash evidence/red-on-head.sh [--base|--tree]   (from the change root; XML on stdout, log on stderr)
#   --base  (default) install `git show <base>:tools/loop/fixtures/pi`, run the case, restore the tree's fixture.
#   --tree  run the case on the tree as it stands — the green half of the same case.
# `LOOP_RED_ON_HEAD_BASE` names the commit the red half is taken against; it defaults to the run's base
# (`27cf61c`), whose fixture predates the hook this change adds.
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || exit 2
cd "$root" || exit 2

mode=base
case "${1:-}" in
  ''|--base) mode=base ;;
  --tree) mode=tree ;;
  *) printf '%s: %s takes --base or --tree, not %s\n' "$(basename -- "$0")" "${1:-}" "${1:-}" >&2; exit 2 ;;
esac
[ $# -le 1 ] || { printf '%s: at most one mode\n' "$(basename -- "$0")" >&2; exit 2; }

fixture=tools/loop/fixtures/pi
base=${LOOP_RED_ON_HEAD_BASE:-27cf61cb1e4a4864276190957e60b0bfa28192c2}
if [ "$mode" = base ]; then
  saved=$(mktemp) && cp "$fixture" "$saved" || exit 2
  # shellcheck disable=SC2064 # the path is fixed here on purpose: the trap runs after `here` has no reader
  trap 'cp "$saved" "$fixture" && rm -f "$saved"' EXIT INT TERM
  git show "$base:$fixture" > "$fixture" || exit 2
  printf 'base fixture installed from %s; the tree'\''s own copy is restored on exit\n' "$base" >&2
fi

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
  "$(basename "$here")/red-on-head.sh --$mode" "$tests" "$failures" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf '%s' "$messages"
printf '</testsuite>\n'
[ "$failures" -eq 0 ]
