#!/usr/bin/env bash
# Selftest for loop-state.sh — runs without a device, without Gradle, without a repo state.
# Proves the three things the loop relies on: the cap, the deadline and the queue stop the loop
# with exit 3 (not with a wrong count), and 'done' consumes a queue entry.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export LOOP_STATE="$tmp/run.json"
S="$here/loop-state.sh"
fail=0
check() { # check <what> <expected> <actual>
  if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; fi
}
check_exit() { # check_exit <what> <expected-code> <code>
  if [ "$2" = "$3" ]; then echo "ok   $1 (exit $3)"; else echo "FAIL $1: expected exit $2 got $3"; fail=1; fi
}

bash "$S" init --bugs 2 --minutes 60 --review off >/dev/null
check "budget recorded" "2" "$(jq -r .bugLimit "$LOOP_STATE")"
check "review flag" "off" "$(jq -r .review "$LOOP_STATE")"

set +e
out="$(bash "$S" remaining)"; code=$?
set -e
check_exit "fresh run has budget" 0 "$code"
# the minute count is floor((deadline - now)/60), so assert the shape and the deadline, not the minute
case "$out" in
  "2 bugs left, "*" min left (deadline $(jq -r .deadlineAt "$LOOP_STATE"))")
    echo "ok   remaining line: $out";;
  *) echo "FAIL remaining line: [$out]"; fail=1;;
esac

bash "$S" triage 63 77 81 >/dev/null
check "queue stored" "63 77 81" "$(jq -r '[.queue[].id] | join(" ")' "$LOOP_STATE")"
check "next is first" "63" "$(bash "$S" next)"

bash "$S" start fix-a >/dev/null
check "current set" "fix-a" "$(jq -r .current "$LOOP_STATE")"
bash "$S" done fix-a --result done --todo '§63' >/dev/null
check "current cleared" "null" "$(jq -r '.current' "$LOOP_STATE")"
check "completed has one" "1" "$(jq -r '.completed | length' "$LOOP_STATE")"
check "queue shrank" "77 81" "$(jq -r '[.queue[].id] | join(" ")' "$LOOP_STATE")"

bash "$S" done fix-b --result skipped --todo '§77' --note "needs decision" >/dev/null
check "skipped recorded" "needs decision" "$(jq -r '.skipped[0].note' "$LOOP_STATE")"

bash "$S" done fix-c --result done --todo '§81' >/dev/null
set +e
out="$(bash "$S" remaining 2>&1)"; code=$?
set -e
check_exit "cap reached stops the loop" 3 "$code"
check "cap message" "0 bugs left (cap reached)" "$out"
set +e
out="$(bash "$S" next 2>&1)"; code=$?
set -e
check_exit "next stops on empty queue too" 3 "$code"

# deadline path: a zero-minute budget is refused, a 1-minute budget is live
set +e
bash "$S" init --bugs 1 --minutes 0 >/dev/null 2>&1; code=$?
set -e
check_exit "zero budget refused" 2 "$code"
bash "$S" init --bugs 1 --minutes 1 >/dev/null
bash "$S" triage 5 >/dev/null
check "deadline is future" "true" "$(jq -r '.deadlineEpoch > (now | floor)' "$LOOP_STATE")"

[ "$fail" = 0 ] && echo "selftest: PASS" || { echo "selftest: FAIL"; exit 1; }
