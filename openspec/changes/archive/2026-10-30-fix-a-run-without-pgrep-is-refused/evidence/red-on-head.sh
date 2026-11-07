#!/usr/bin/env bash
# red-on-head.sh — the red case of fix-a-run-without-pgrep-is-refused (issue #703).
#
# The case is the change's own and the change's only production file is `tools/loop/run.sh`, so its "HEAD" half is
the case run against `run.sh` as the base carries it: with `pgrep` off the case's `PATH` neither `run.sh` nor
`lib.sh` consults it before the item loop starts, the run reaches the pre-screen and stops on an unrelated refusal
(exit 3), while the case requires the run's own usage refusal (exit 2) naming the missing reading. The recorded
run — the tree carrying the case, `run.sh` unmodified — printed `tests=1 failures=1`
(2026-10-30T09:10:53Z). On the change's tree the same command exits 0 with `PASS` for the case.
#
# Usage: bash evidence/red-on-head.sh   (from the change root; prints the XML on stdout, the log on stderr)
set -uo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=$(git -C "$here" rev-parse --show-toplevel 2>/dev/null) || exit 2
cd "$root" || exit 2

cases=(
  'run#aHostWithoutThePlacementReadingIsRefused'
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
    # the case's own line carries the reason it failed; the suite prefixes every line with `<case>|`
    reason=$(printf '%s\n' "$out" | sed -n "s/^$c| *//p" | grep -m1 'expected' || true)
    [ -n "$reason" ] || reason="the case failed"
    printf 'FAIL %s — %s\n' "$c" "$reason" >&2
    messages="$messages<failure message=\"$(printf '%s' "$reason" | sed 's/[&<>"]//g')\">$c: $(printf '%s' "$reason" | sed 's/[&<>"]//g')</failure>
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
