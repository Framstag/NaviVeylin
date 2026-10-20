#!/usr/bin/env bash
# report.sh — the run's report: what it was allowed to spend, what it spent it on, and what it left open.
#
# The report is the interface. Concept doc §6 asks a loop to justify itself with numbers, and the wrapper
# is the only component that can observe them without trusting a model, so every number below is read
# from the state file (or from git) rather than from a report a child wrote (D11).
#
# It answers the three questions a finished run has to answer on its own (#208): which items were handled
# and how each ended (the table, with the item's own reason when the outcome is not a closure), what each
# one cost (the table's `wall` column, and the run-level `spent`), and what the caller must now do — the
# named action list at the end, built from the state and from git, never from a count.
#
# `--state <file>` reports on a state `state.sh init` kept before it replaced it, so a finished run's
# counters, verdicts and stop reason survive the next run starting (issue #207). Without it the report is
# the run in place, exactly as before.
#
# A run's spend is its own. The difference between the clock and `run.startedEpoch` describes the run only
# while its window is open, so the spend is measured to the stop the run recorded once it closed; a state
# whose window closed without a stop cannot say what it spent, and the report names the run its numbers
# belong to instead of printing a budget left below zero.
#
# Exit: 0 ok, 2 usage.
#
# Usage: report.sh [--state <file>] [--json]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

json=0
state_opt=""
while [ $# -gt 0 ]; do
  case "$1" in
    --json) json=1; shift ;;
    --state) state_opt="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) loop_die "report: unknown argument $1" ;;
  esac
done

loop_resolve
loop_roles_load
# An archived run is named on the command line; a relative name is relative to the project, as every other
# path the loop takes from its environment is.
state_file="$LOOP_STATE_FILE"
if [ -n "$state_opt" ]; then
  case "$state_opt" in /*) state_file="$state_opt" ;; *) state_file="$LOOP_ROOT/$state_opt" ;; esac
fi
[ -f "$state_file" ] || loop_die "report: no run state at $state_file"

if [ "$json" -eq 1 ]; then
  jq -c '{run, items, excluded, budgetLeft: (.run.items - (.items | length))}' "$state_file"
  exit "$LOOP_OK"
fi

items=$(jq -r '.run.items' "$state_file")
minutes=$(jq -r '.run.minutes' "$state_file")
closed=$(jq -r '[.items[] | select(.result == "done")] | length' "$state_file")
skipped=$(jq -r '[.items[] | select(.result == "skipped")] | length' "$state_file")
blocked=$(jq -r '[.items[] | select(.result == "blocked")] | length' "$state_file")

printf '**agent-issue-loop — %s** (budget %s items / %s min · review %s · review rounds %s · may-close %s · max-decisions %s · base %s)\n\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$items" "$minutes" \
  "$(jq -r '.run.review' "$state_file")" "$(jq -r '.run.maxReviewRounds // "?"' "$state_file")" \
  "$(jq -r '.run.mayClose' "$state_file")" \
  "$(jq -r '.run.maxDecisions' "$state_file")" \
  "$(jq -r '.run.base | .[0:12]' "$state_file")"

# The budget left subtracts one run's spend from the same run's budget, so the spend has to be that run's:
# from its start to the stop it recorded when it ended, and from its start to the clock only inside the
# window it was given. Past the deadline with no recorded stop, the clock no longer belongs to this run —
# measured 2026-10-20, a later 30-minute run had re-initialised the state file and the report printed
# `budget left -421 min` about a 480-minute run it knew nothing about (issue #207).
is_num() { case "${1:-}" in ''|*[!0-9]*) return 1 ;; *) return 0 ;; esac; }

run_epoch=$(jq -r '.run.startedEpoch // ""' "$state_file")
deadline_epoch=$(jq -r '.run.deadlineEpoch // ""' "$state_file")
deadline_at=$(jq -r '.run.deadlineAt // "an unrecorded time"' "$state_file")
stop_at=$(jq -r '.run.stop.at // ""' "$state_file")
spent_min=""
if is_num "$run_epoch" && is_num "$deadline_epoch"; then
  if [ -n "$stop_at" ] && stop_epoch=$(date -u -d "$stop_at" +%s 2>/dev/null); then
    spent_min=$(( (stop_epoch - run_epoch) / 60 ))
  elif [ "$(date +%s)" -le "$deadline_epoch" ]; then
    spent_min=$(( ( $(date +%s) - run_epoch ) / 60 ))
  fi
fi
if [ -n "$spent_min" ]; then
  printf 'closed %s · skipped %s · blocked %s · budget left %s items / %s min · spent %s min\n\n' \
    "$closed" "$skipped" "$blocked" \
    "$((items - closed - skipped - blocked))" "$((minutes - spent_min))" "$spent_min"
else
  printf 'closed %s · skipped %s · blocked %s · budget left unknown: these numbers belong to another run — its window closed at %s and it recorded no stop\n\n' \
    "$closed" "$skipped" "$blocked" "$deadline_at"
fi

# Why the run ended is one of its numbers now: `next` names the condition (item cap, deadline, empty queue,
# two consecutive blocked items, the remaining-time floor) and the run records it, so the report and the state
# file cannot disagree about the end (#184). A run that has not stopped says so, because the report can be
# printed while it is still going.
stop_reason=$(jq -r '.run.stop.reason // ""' "$state_file")
printf 'stop: %s\n\n' "$(if [ -n "$stop_reason" ]; then printf '%s' "$stop_reason";
  else printf 'not stopped — the run is still going, or it ended without recording a reason'; fi)"

# The run's own table is the same reader as `state.sh report`, pointed at the same file.
LOOP_STATE_FILE="$state_file" bash "$LOOP_DIR/state.sh" report

printf '\nskipped: %s\n' "$(jq -r '[.items[] | select(.result == "skipped") | "#\(.issue) \(.verdict)\(if .note == "" then "" else " (\(.note))" end)"] | if length == 0 then "none" else join(" · ") end' "$state_file")"
printf 'excluded before any work: %s\n' "$(jq -r '[.excluded[] | "#\(.issue) \(.verdict)"] | if length == 0 then "none" else join(" · ") end' "$state_file")"
# The decisions the owner owes are read from what the run *wrote*, never from the verdict a worker chose:
# an item carries the label its own escalation recorded (`state.sh done --decision-label`), and an item the
# pre-screen excluded is escalated by that verdict's value — the one predicate run.sh repeats. Reading the
# verdict instead said `none` while `agent/needs-decision` had just been written (issue #177).
printf 'decisions the owner owes: %s\n' "$(jq -r --arg d "$(loop_role decisionAgent)" '
  [.items[] | select((.escalatedLabel // "") != "") | "#\(.issue)"]
  + [.excluded[] | select(.verdict == $d) | "#\(.issue)"]
  | if length == 0 then "none" else join(" · ") end' "$state_file")"

# The branches are read from git, not from a child's report: the run's own script created them.
branches=$(git -C "$LOOP_ROOT" branch --list 'loop-*' 'fix/issue-*' --format='%(refname:short)')
printf 'branches: %s\n' "$(if [ -n "$branches" ]; then printf '%s' "$branches" | paste -sd' ' -; else printf 'none'; fi)"
if [ -d "$LOOP_WORKTREE" ]; then
  printf 'worktree: %s (kept for the next run)\n' "$LOOP_WORKTREE"
else
  printf 'worktree: not present at run end (%s)\n' "$LOOP_WORKTREE"
fi
printf 'state: %s\n' "$state_file"

# ── the actions the caller owes ───────────────────────────────────────────────────────────────────
# The report's third answer (#208). A count is not an answer: the caller has to know *which* decision is
# owed and what it asks, *which* branch carries work the base does not have and *whose* item it is, where
# the kept worktree is, and which item never got a gate result and where its transcript sits to say why.
# Every line comes from the state or from git — the same two sources as everything above it — and a
# question the state does not hold says where it is (`gh issue view <n>`) instead of inventing one.
printf '\n'
decisions() {
  jq -r --arg d "$(loop_role decisionAgent)" '
    (.items[] | select((.escalatedLabel // "") != "")
      | ["item", (.issue | tostring), .verdict, (.note // ""), (.metrics.rootCause // "")]),
    (.excluded[] | select($d != "" and .verdict == $d)
      | ["excluded", (.issue | tostring), .verdict, (.reason // ""), (.evidence // "")])
    | @tsv' "$state_file"
}

state_dir=$(dirname "$state_file")
base=$(jq -r '.run.base // ""' "$state_file")
actions=()

while IFS=$'\t' read -r kind issue verdict reason extra; do
  [ -n "${issue:-}" ] || continue
  # The question the run asked is the escalation's reason, and the state records it only where the
  # pre-screen produced it (an exclusion keeps `reason`); an item's escalation keeps the label it wrote
  # and the item's own note. Where neither holds the text, the action names the place it does.
  question="$reason"
  [ -n "$question" ] || question="$extra"
  case "$kind" in
    item) line="decide #$issue — the run escalated it ($verdict)" ;;
    *) line="decide #$issue — the pre-screen refused it ($verdict)" ;;
  esac
  if [ -n "$question" ]; then
    line="$line: $question · the comment itself: gh issue view $issue"
  else
    line="$line: the run's state does not hold the question — gh issue view $issue"
  fi
  actions[${#actions[@]}]="$line"
done < <(decisions)

# The branches are read from git, and a branch is an action because of what it *carries*, not because it
# exists: `none` must never hide commits. A range git cannot answer is named rather than counted as zero.
while IFS= read -r branch; do
  [ -n "$branch" ] || continue
  if ahead=$(git -C "$LOOP_ROOT" rev-list --count "$base..$branch" 2>/dev/null); then :; else ahead=1; fi
  case "$ahead" in ''|*[!0-9]*) ahead=1 ;; esac
  [ "$ahead" -gt 0 ] || continue
  owner=$(printf '%s' "$branch" | sed -n 's#^fix/issue-\([0-9][0-9]*\)-.*#\1#p' | head -1)
  if [ -z "$owner" ]; then
    owner=$(jq -r --arg b "$branch" '[.items[] | select((.merged.branch // "") == $b) | .issue] | (.[0] // "")' "$state_file")
  fi
  if [ -n "$owner" ]; then
    actions[${#actions[@]}]="land $branch — $ahead commit(s) the base does not have — item #$owner"
  else
    actions[${#actions[@]}]="land $branch — $ahead commit(s) the base does not have — no item of this run owns it"
  fi
done <<< "$branches"

# An item that ended without a gate result is the one failure a caller cannot reconstruct from the table:
# the transcript beside its id says how far the item got.
while IFS=$'\t' read -r issue; do
  [ -n "${issue:-}" ] || continue
  actions[${#actions[@]}]="re-run the gate for #$issue — the run recorded no gate result — transcript $state_dir/items/$issue/worker-report.md"
done < <(jq -r '.items[] | select(.result != "done") | select((.metrics.gate // "") == "") | .issue | tostring' "$state_file")

# A citation the pre-screen still refuses is work a person can do and the run cannot: the repair path is
# the tooling's own, and it is named with the item.
stale_role=$(loop_role excludedStaleAgent)
if [ -n "$stale_role" ]; then
  while IFS=$'\t' read -r issue reason; do
    [ -n "${issue:-}" ] || continue
    actions[${#actions[@]}]="repair the citation in #$issue — the pre-screen refused it as $stale_role: $reason — tools/issues/resolve-stale.sh --id $issue"
  done < <(jq -r --arg s "$stale_role" '.excluded[] | select(.verdict == $s) | [(.issue | tostring), (.reason // "a citation no record accounts for")] | @tsv' "$state_file")
fi

if [ -d "$LOOP_WORKTREE" ]; then
  actions[${#actions[@]}]="keep or remove the worktree $LOOP_WORKTREE — the next run reuses its warm build; --clean-worktree removes it at the end of a run"
fi

printf 'actions the caller owes:\n'
if [ "${#actions[@]}" -eq 0 ]; then
  # Empty only when every one of the five sources was read and empty — not because a list was skipped.
  printf '  none — no decision, no branch carrying commits, no kept worktree, no gate left incomplete, no refused citation\n'
else
  n=1
  for action in "${actions[@]}"; do
    printf '  %s. %s\n' "$n" "$action"
    n=$((n + 1))
  done
fi
