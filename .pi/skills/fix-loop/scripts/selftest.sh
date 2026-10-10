#!/usr/bin/env bash
# Selftest for loop-state.sh — runs without a device, without Gradle, without a repo state.
# Proves the four things the loop relies on: the cap, the deadline and the queue stop the loop with exit 3
# (never with a wrong count), `done` consumes a queue entry, and a pre-change skip can be recorded by --id
# with its verdict (the shape the gate actually produces).
# Also guards the skill document's structure and the `itemLimit`/`bugLimit` handover, so a revert of the
# widen-fix-loop change makes a named check go red.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
export LOOP_STATE="$tmp/run.json"
S="$here/loop-state.sh"
fail=0
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: expected [$2] got [$3]"; fail=1; fi; }
check_exit() { if [ "$2" = "$3" ]; then echo "ok   $1 (exit $3)"; else echo "FAIL $1: expected exit $2 got $3"; fail=1; fi; }

bash "$S" init --items 2 --minutes 60 --review off >/dev/null
check "budget recorded" "2" "$(jq -r .itemLimit "$LOOP_STATE")"
check "review flag" "off" "$(jq -r .review "$LOOP_STATE")"

set +e
out="$(bash "$S" remaining)"; code=$?
set -e
check_exit "fresh run has budget" 0 "$code"
case "$out" in
  "2 items left, "*" min left (deadline $(jq -r .deadlineAt "$LOOP_STATE"))")
    echo "ok   remaining line: $out";;
  *) echo "FAIL remaining line: [$out]"; fail=1;;
esac

# a state file from before the rename carries bugLimit only: every reader must still resolve the budget
legacy="$tmp/legacy.json"
jq 'del(.itemLimit) + {bugLimit: 3}' "$LOOP_STATE" > "$legacy"
set +e
out="$(LOOP_STATE="$legacy" bash "$S" remaining 2>&1)"; code=$?
set -e
check_exit "legacy bugLimit-only state resolves its budget" 0 "$code"
case "$out" in
  "3 items left, "*) echo "ok   legacy bugLimit-only state: $out";;
  *) echo "FAIL legacy bugLimit-only state: [$out]"; fail=1;;
esac

bash "$S" triage 63 77 81 >/dev/null
check "queue stored" "63 77 81" "$(jq -r '[.queue[].id] | join(" ")' "$LOOP_STATE")"
check "next is first" "63" "$(bash "$S" next)"

bash "$S" start fix-a --id '§63' >/dev/null
check "current set" "fix-a" "$(jq -r .current "$LOOP_STATE")"
check "current id set" "63" "$(jq -r .currentId "$LOOP_STATE")"
check "start logs phase A" "A" "$(jq -r '.steps[-1].phase' "$LOOP_STATE")"

bash "$S" step B --name gate --status ok --note "6/6 conditions" >/dev/null
check "step advances phase" "B" "$(jq -r .currentPhase "$LOOP_STATE")"
check "step records status" "ok" "$(jq -r '.steps[-1].status' "$LOOP_STATE")"
check "step records note" "6/6 conditions" "$(jq -r '.steps[-1].note' "$LOOP_STATE")"
check "step carries current id" "63" "$(jq -r '.steps[-1].id' "$LOOP_STATE")"

sh="$(bash "$S" show)"
case "$sh" in
  *"§63 fix-a · phase B"*) echo "ok   show names issue and phase";;
  *) echo "FAIL show names issue and phase: [$sh]"; fail=1;;
esac
case "$sh" in
  *"B  gate · ok · 6/6 conditions"*) echo "ok   show step row";;
  *) echo "FAIL show step row: [$sh]"; fail=1;;
esac

bash "$S" done fix-a --result done --id '§63' >/dev/null
check "current cleared" "null" "$(jq -r '.current' "$LOOP_STATE")"
check "current phase cleared" "null" "$(jq -r '.currentPhase' "$LOOP_STATE")"
check "steps survive done" "2" "$(jq -r '.steps | length' "$LOOP_STATE")"
sh="$(bash "$S" show)"
case "$sh" in
  *"last: §63 fix-a (closed)"*) echo "ok   show falls back to last iteration";;
  *) echo "FAIL show last iteration: [$sh]"; fail=1;;
esac
case "$sh" in
  *"B  gate · ok · 6/6 conditions"*) echo "ok   show keeps last iteration's steps";;
  *) echo "FAIL show last steps: [$sh]"; fail=1;;
esac
check "completed has one" "1" "$(jq -r '.completed | length' "$LOOP_STATE")"
check "default verdict for done" "closed" "$(jq -r '.completed[0].verdict' "$LOOP_STATE")"
check "queue shrank" "77 81" "$(jq -r '[.queue[].id] | join(" ")' "$LOOP_STATE")"

# the pre-change skip shape: no change name, --id + --verdict
bash "$S" done --id '§77' --result skipped --verdict needs-decision --note "which start is authoritative" >/dev/null
check "skip needs no change name" "77" "$(jq -r '.skipped[0].id' "$LOOP_STATE")"
check "skip verdict stored" "needs-decision" "$(jq -r '.skipped[0].verdict' "$LOOP_STATE")"
check "skip note stored" "which start is authoritative" "$(jq -r '.skipped[0].note' "$LOOP_STATE")"
check "§-prefix stripped" "" "$(jq -r '.skipped[0].id | select(test("§"))' "$LOOP_STATE")"
check "queue shrank again" "81" "$(jq -r '[.queue[].id] | join(" ")' "$LOOP_STATE")"

bash "$S" done fix-c --result blocked --verdict blocked --note "unrelated flake" >/dev/null
check "blocked recorded" "1" "$(jq -r '.blocked | length' "$LOOP_STATE")"

# report + verdicts
rep="$(bash "$S" report)"
case "$rep" in
  *"closed 1 · skipped 1 · blocked 1"*) echo "ok   report header";;
  *) echo "FAIL report header: [$rep]"; fail=1;;
esac
case "$rep" in
  *"skipped  §77	needs-decision"*) echo "ok   report skip row";;
  *) echo "FAIL report skip row"; fail=1;;
esac
case "$rep" in
  *"B/gate · ok · 6/6 conditions"*) echo "ok   report step row";;
  *) echo "FAIL report step row: [$rep]"; fail=1;;
esac
check "verdict histogram" "1	blocked
1	needs-decision" "$(bash "$S" verdicts | sort)"

# the cap counts COMPLETED only: two skips and three blocks must not stop the run
set +e
out="$(bash "$S" remaining)"; code=$?
set -e
check_exit "cap not reached by skips" 0 "$code"
bash "$S" done fix-d --result done --id '§81' >/dev/null
set +e
out="$(bash "$S" remaining 2>&1)"; code=$?
set -e
check_exit "cap reached stops the loop" 3 "$code"
check "cap message" "0 items left (cap reached)" "$out"

# next stops on the empty queue as well
bash "$S" triage 5 >/dev/null
bash "$S" done --id '§5' --result skipped --verdict stale --note x >/dev/null
set +e
out="$(bash "$S" next 2>&1)"; code=$?
set -e
check_exit "next stops on empty queue" 3 "$code"

# argument validation
set +e
bash "$S" init --items 1 --minutes 0 >/dev/null 2>&1; code=$?
set -e
check_exit "zero budget refused" 2 "$code"
set +e
bash "$S" init --bugs 1 --minutes 1 >/dev/null 2>&1; code=$?
set -e
check_exit "the old --bugs flag is refused" 2 "$code"
set +e
bash "$S" done --result skipped --note "no id and no change" >/dev/null 2>&1; code=$?
set -e
check_exit "done without id/change refused" 2 "$code"
set +e
bash "$S" done --id '§9' --result maybe >/dev/null 2>&1; code=$?
set -e
check_exit "bad result refused" 2 "$code"
set +e
bash "$S" step X --name boom >/dev/null 2>&1; code=$?
set -e
check_exit "bad phase refused" 2 "$code"
set +e
bash "$S" step A --status ok >/dev/null 2>&1; code=$?
set -e
check_exit "step without name refused" 2 "$code"
set +e
bash "$S" step A --name x --status maybe >/dev/null 2>&1; code=$?
set -e
check_exit "bad step status refused" 2 "$code"

# a fresh 1-minute run is live
bash "$S" init --items 1 --minutes 1 >/dev/null
bash "$S" triage 5 >/dev/null
check "deadline is future" "true" "$(jq -r '.deadlineEpoch > (now | floor)' "$LOOP_STATE")"

# the skill document's structure — guards the restructure, so a re-added duplicate or a restored
# `## ` template heading makes a named check fail
skill="$here/../SKILL.md"
if [ ! -f "$skill" ]; then
  echo "FAIL skill document not found: $skill"; fail=1
else
  check "no report-template heading is a section" "0" "$(grep -c '^## .*— <date>' "$skill" || true)"
  check "report template still present" "1" "$(grep -c 'fix-loop — <date>' "$skill" || true)"
  check "pre-screen admits improvements" "1" "$(grep -c 'In scope: `bug`, and `improvement`' "$skill" || true)"
  check "copy-out rule stated once" "1" "$(grep -c 'Copy the cited XMLs' "$skill" || true)"
  check "gate table has seven conditions" "7" "$(grep -c '^| [1-7] |' "$skill" || true)"
fi

[ "$fail" = 0 ] && echo "selftest: PASS" || { echo "selftest: FAIL"; exit 1; }
