#!/usr/bin/env bash
# Selftest for the forwarder `loop-state.sh` — no device, no Gradle, no repository state.
#
# The loop's own gate is `tools/loop-selftest.sh` (38 cases, offline). This file proves only what the
# forwarder promises: that an invocation written against the *old* interface still works — the old override
# variable, the old command names, the old `--allow-closure` flag, an id written `§N`, and the exit code the
# old caller relied on (`next` = 3 when the budget is spent). A broken forwarder would break a reference
# written before `agent-issue-loop`, which is the whole reason it exists.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export LOOP_STATE="$tmp/run.json"   # the OLD override name: the forwarder must translate it
S="$here/loop-state.sh"
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; fi; }
check_exit() { if [ "$2" = "$3" ]; then echo "ok   $1 (exit $3)"; else echo "FAIL $1: expected exit $2 got $3"; fail=1; fi; }

bash "$S" init --items 2 --minutes 60 --review off --allow-closure no >/dev/null 2>&1 || {
  echo "FAIL init (old flags) failed"; fail=1; }
check "the old override variable still names the state file" "2" "$(jq -r '.run.items' "$LOOP_STATE" 2>/dev/null)"
check "review flag recorded" "off" "$(jq -r '.run.review' "$LOOP_STATE" 2>/dev/null)"
check "--allow-closure was translated to --may-close" "no" "$(jq -r '.run.mayClose' "$LOOP_STATE" 2>/dev/null)"

bash "$S" triage '§63' '§77' >/dev/null 2>&1 || { echo "FAIL triage"; fail=1; }
check "the old id list became the queue" "63" "$(bash "$S" next 2>/dev/null)"

out="$(bash "$S" status 2>&1)"; code=$?
check_exit "status is show" 0 "$code"
case "$out" in
  *"2 items / 60 min"*) echo "ok   status prints the budget";;
  *) echo "FAIL status output: [$out]"; fail=1;;
esac

# the cap stops the loop with exit 3, which is what an old caller checked
bash "$S" start legacy-change --id '§63' >/dev/null 2>&1
bash "$S" done --id '§63' --result done --verdict closed --note "forwarded" >/dev/null 2>&1
bash "$S" start legacy-change --id '§77' >/dev/null 2>&1
bash "$S" done --id '§77' --result skipped --verdict stale --note "the file it cited is gone" >/dev/null 2>&1
set +e
out="$(bash "$S" next 2>&1)"; code=$?
set -e
check_exit "a spent budget still exits 3" 3 "$code"
case "$out" in
  *"item cap reached"*) echo "ok   and says why: $out";;
  *) echo "FAIL the cap message: [$out]"; fail=1;;
esac

check "verdicts prints the old histogram" "closed stale" \
  "$(bash "$S" verdicts 2>/dev/null | awk '{print $2}' | sort | paste -sd' ' -)"

# the old pre-change skip shape (no change name, only --id) is still recorded
bash "$S" triage '§88' >/dev/null 2>&1
bash "$S" done --id '§88' --result skipped --verdict needs-device --note "needs a phone" >/dev/null 2>&1
check "a pre-change skip is recorded by --id" "needs-device" \
  "$(jq -r '.items[] | select(.issue == 88) | .verdict' "$LOOP_STATE" 2>/dev/null)"

set +e
bash "$S" wibble >/dev/null 2>&1; code=$?
set -e
check_exit "an unknown command is a usage error" 2 "$code"

if [ "$fail" -ne 0 ]; then
  echo "selftest: the loop-state forwarder is broken"
  exit 1
fi
echo "selftest: the loop-state forwarder still serves the old interface (the loop's own gate is tools/loop-selftest.sh)"
