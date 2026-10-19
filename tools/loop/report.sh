#!/usr/bin/env bash
# report.sh — the run's report: what it was allowed to spend, what it spent it on, and what it left open.
#
# The report is the interface. Concept doc §6 asks a loop to justify itself with numbers, and the wrapper
# is the only component that can observe them without trusting a model, so every number below is read
# from the state file (or from git) rather than from a report a child wrote (D11).
#
# Exit: 0 ok, 2 usage.
#
# Usage: report.sh [--json]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

json=0
while [ $# -gt 0 ]; do
  case "$1" in
    --json) json=1; shift ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) loop_die "report: unknown argument $1" ;;
  esac
done

loop_resolve
loop_roles_load
[ -f "$LOOP_STATE_FILE" ] || loop_die "report: no run state at $LOOP_STATE_FILE"

if [ "$json" -eq 1 ]; then
  jq -c '{run, items, excluded, budgetLeft: (.run.items - (.items | length))}' "$LOOP_STATE_FILE"
  exit "$LOOP_OK"
fi

run_epoch=$(jq -r '.run.startedEpoch' "$LOOP_STATE_FILE")
spent_min=$(( ( $(date +%s) - run_epoch ) / 60 ))
items=$(jq -r '.run.items' "$LOOP_STATE_FILE")
minutes=$(jq -r '.run.minutes' "$LOOP_STATE_FILE")
closed=$(jq -r '[.items[] | select(.result == "done")] | length' "$LOOP_STATE_FILE")
skipped=$(jq -r '[.items[] | select(.result == "skipped")] | length' "$LOOP_STATE_FILE")
blocked=$(jq -r '[.items[] | select(.result == "blocked")] | length' "$LOOP_STATE_FILE")

printf '**agent-issue-loop — %s** (budget %s items / %s min · review %s · review rounds %s · may-close %s · max-decisions %s · base %s)\n\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$items" "$minutes" \
  "$(jq -r '.run.review' "$LOOP_STATE_FILE")" "$(jq -r '.run.maxReviewRounds // "?"' "$LOOP_STATE_FILE")" \
  "$(jq -r '.run.mayClose' "$LOOP_STATE_FILE")" \
  "$(jq -r '.run.maxDecisions' "$LOOP_STATE_FILE")" \
  "$(jq -r '.run.base | .[0:12]' "$LOOP_STATE_FILE")"
printf 'closed %s · skipped %s · blocked %s · budget left %s items / %s min · spent %s min\n\n' \
  "$closed" "$skipped" "$blocked" \
  "$((items - closed - skipped - blocked))" "$((minutes - spent_min))" "$spent_min"

# Why the run ended is one of its numbers now: `next` names the condition (item cap, deadline, empty queue,
# two consecutive blocked items, the remaining-time floor) and the run records it, so the report and the state
# file cannot disagree about the end (#184). A run that has not stopped says so, because the report can be
# printed while it is still going.
stop_reason=$(jq -r '.run.stop.reason // ""' "$LOOP_STATE_FILE")
printf 'stop: %s\n\n' "$(if [ -n "$stop_reason" ]; then printf '%s' "$stop_reason";
  else printf 'not stopped — the run is still going, or it ended without recording a reason'; fi)"

bash "$LOOP_DIR/state.sh" report

printf '\nskipped: %s\n' "$(jq -r '[.items[] | select(.result == "skipped") | "#\(.issue) \(.verdict)\(if .note == "" then "" else " (\(.note))" end)"] | if length == 0 then "none" else join(" · ") end' "$LOOP_STATE_FILE")"
printf 'excluded before any work: %s\n' "$(jq -r '[.excluded[] | "#\(.issue) \(.verdict)"] | if length == 0 then "none" else join(" · ") end' "$LOOP_STATE_FILE")"
# The decisions the owner owes are read from what the run *wrote*, never from the verdict a worker chose:
# an item carries the label its own escalation recorded (`state.sh done --decision-label`), and an item the
# pre-screen excluded is escalated by that verdict's value — the one predicate run.sh repeats. Reading the
# verdict instead said `none` while `agent/needs-decision` had just been written (issue #177).
printf 'decisions the owner owes: %s\n' "$(jq -r --arg d "$(loop_role decisionAgent)" '
  [.items[] | select((.escalatedLabel // "") != "") | "#\(.issue)"]
  + [.excluded[] | select(.verdict == $d) | "#\(.issue)"]
  | if length == 0 then "none" else join(" · ") end' "$LOOP_STATE_FILE")"

# The branches are read from git, not from a child's report: the run's own script created them.
branches=$(git -C "$LOOP_ROOT" branch --list 'loop-*' 'fix/issue-*' --format='%(refname:short)')
printf 'branches: %s\n' "$(if [ -n "$branches" ]; then printf '%s' "$branches" | paste -sd' ' -; else printf 'none'; fi)"
if [ -d "$LOOP_WORKTREE" ]; then
  printf 'worktree: %s (kept for the next run)\n' "$LOOP_WORKTREE"
else
  printf 'worktree: not present at run end (%s)\n' "$LOOP_WORKTREE"
fi
printf 'state: %s\n' "$LOOP_STATE_FILE"
