#!/usr/bin/env bash
# report.sh — the run's report: what it was allowed to spend, what it spent it on, and what it left open.
#
# The report is the interface. Concept doc §6 asks a loop to justify itself with numbers, and the wrapper
# is the only component that can observe them without trusting a model, so every number below is read
# from the state file (or from git) rather than from a report a child wrote (D11).
#
# It answers the three questions a finished run has to answer on its own (#208): which items were handled
# and how each ended (the table, with the item's own reason when the outcome is not a closure), what each
# one cost (the table's `wall`, `tools`, `tokens`, `context` and `calls` columns — the middle three summed from the
# child's own event stream, the last a ratio of two of them, `-` where nothing was reported — and the run-level
# `spent`), and what the caller must now do — the named action list at the end, built from the state and from git,
# never from a count.
#
# Beside those three, and about the loop rather than about one run, it says whether the loop is getting better
# (#247): the review round-1 pass rate, the item wall median, minimum and maximum, and — since issue #757 — the
# run's tool calls per turn beside the items below the recorded floor, totalled from the same
# state, and the one figure that state does not hold — the landings that reddened `main` — named where it lives
# rather than fetched, because the report reads the state and git and never the tracker.
#
# Since issue #759 it also says where the spend went: the clock split by the phase that spent it (the model time,
# the project's gate, every other tool), the tokens split by the role that spent them (worker, reviewer, tail), and
# the share of the tokens spent after an item's first gate. All three are read from the state, which records them
# when the item's stream is read — a run that paid two thirds of its clock inside the gate and one that paid it
# reading printed the same shapes before these lines existed.
#
# Since issue #811 it counts the closes that were waived: an item may close over a gate that did not pass, and the
# state records the clause and the suite beside the tally (`state.sh done --gate-waived`). The `waived:` line prints
# how many of the run's closed items that was and which suite each waiver named — the number that decides whether a
# failing suite is flaky or broken. Prose is not a rate: before the field existed a waived red and a green gate both
# printed `result: done`, and nothing accumulated the evidence that would either fix the flake or name it.
#
# Since issue #841 it says which of its own figures are lower bounds: a stream a child outgrew was rotated, the
# adapter records what that rotation dropped, and the state carries the record beside the spend it is missing from
# (`metrics.streamRotation`). The `truncated:` line names the items whose figures are short, with the figure each
# dropped — the run's totals print the same whether one item was cut or nine, so the extent alone is not the answer.
# A **warning, never a stop**, like every figure beside it.
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
    --state) need_value "$@"; state_opt="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,/^set -uo pipefail/p' "$0"; exit 0 ;;
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
misdiagnosed=$(jq -r '[.items[] | select(.result == "falsified")] | length' "$state_file")
skipped=$(jq -r '[.items[] | select(.result == "skipped")] | length' "$state_file")
blocked=$(jq -r '[.items[] | select(.result == "blocked")] | length' "$state_file")
# The item a bound cut is counted apart from one that blocked (issue #785): it spent budget, so the budget left
# subtracts it, and it is no question — the next run resumes the phase the run's own clock ended.
cut=$(jq -r '[.items[] | select(.result == "cut")] | length' "$state_file")

printf '**agent-issue-loop — %s** (budget %s items / %s min · review %s · review rounds %s · may-close %s · max-decisions %s · base %s)\n\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$items" "$minutes" \
  "$(jq -r '.run.review' "$state_file")" "$(jq -r '.run.maxReviewRounds // "?"' "$state_file")" \
  "$(jq -r '.run.mayClose' "$state_file")" \
  "$(jq -r '.run.maxDecisions' "$state_file")" \
  "$(jq -r '.run.base | .[0:12]' "$state_file")"

# The bounds every figure below was measured against (issue #815): the character bound an item's tool results were
# cut at, and the two context bounds its `context` column is read against. They are printed beside the budget for the
# reason the budget is: a run at another bound is a different run, and a state whose record does not name it leaves a
# comparison of two runs' rates as a comparison of two numbers nobody wrote down. `?` is a state written before the
# record carried them — not `0`, which is a configuration that records no bound (the rule every unreported figure
# already follows).
printf 'bounds: result bound %s · context warn %s · context max %s\n\n' \
  "$(jq -r '.run.toolResultMaxChars // "?"' "$state_file")" \
  "$(jq -r '.run.itemContextWarn // "?"' "$state_file")" \
  "$(jq -r '.run.itemContextMax // "?"' "$state_file")"

# The budget left subtracts one run's spend from the same run's budget, so the spend has to be that run's:
# from its start to the stop it recorded when it ended, and from its start to the clock only inside the
# window it was given. Past the deadline with no recorded stop, the clock no longer belongs to this run —
# measured 2026-10-20, a later 30-minute run had re-initialised the state file and the report printed
# `budget left -421 min` about a 480-minute run it knew nothing about (issue #207).
is_num() { case "${1:-}" in ''|*[!0-9]*) return 1 ;; *) return 0 ;; esac; }

# Why the run ended is one of its numbers now: `next` names the condition (item cap, deadline, empty queue,
# two consecutive refusals, the remaining-time floor) and the run records it, so the report and the state
# file cannot disagree about the end (#184). A run that has not stopped says so, because the report can be
# printed while it is still going.
stop_reason=$(jq -r '.run.stop.reason // ""' "$state_file")

# A run that wrote this state and is not running any more is not a run that is going: the state names the
# process that wrote it, and a run that recorded no stop while its process is gone is a run that was killed
# (measured 2026-10-22: the run killed at 19:19 left a state that read "the run is still going" with 217
# minutes of budget left). The clock since its start is not its spend any more, so no budget left is printed
# for it either.
owner_pid=$(jq -r '.run.ownerPid // 0' "$state_file" 2>/dev/null || printf 0)
case "$owner_pid" in ''|*[!0-9]*) owner_pid=0 ;; esac
owner_gone=0
if [ "$owner_pid" -gt 0 ] && [ -z "$stop_reason" ] && ! kill -0 "$owner_pid" 2>/dev/null; then
  owner_gone=1
fi

run_epoch=$(jq -r '.run.startedEpoch // ""' "$state_file")
deadline_epoch=$(jq -r '.run.deadlineEpoch // ""' "$state_file")
deadline_at=$(jq -r '.run.deadlineAt // "an unrecorded time"' "$state_file")
stop_at=$(jq -r '.run.stop.at // ""' "$state_file")
# What the run spent is what it **worked**, and since the quota hold those are two different numbers: a wait for
# a condition outside the run moves the deadline with it (`state.sh hold`), so the pause is neither in the budget
# nor in the spend. Read as the clock alone, a run of three items that waited six hours reported 540 minutes
# against a 480-minute budget — a negative number nobody could act on. So the clock is read minus every recorded
# hold, and the waits are named beside it: the summary then says how long the run waited, which the items' own wall
# times cannot (they start and end inside a child and never span a hold).
paused_secs=$(jq -r '[(.run.holds // [])[].seconds] | add // 0' "$state_file" 2>/dev/null)
is_num "$paused_secs" || paused_secs=0
paused_min=$(( paused_secs / 60 ))
paused_note=""
if [ "$paused_min" -gt 0 ]; then
  paused_note=" · paused ${paused_min} min in $(jq -r '(.run.holds // []) | length' "$state_file") wait(s)"
fi
spent_min=""
if is_num "$run_epoch" && is_num "$deadline_epoch"; then
  if [ -n "$stop_at" ] && stop_epoch=$(date -u -d "$stop_at" +%s 2>/dev/null); then
    spent_min=$(( (stop_epoch - run_epoch) / 60 - paused_min ))
  elif [ "$(date +%s)" -le "$deadline_epoch" ]; then
    spent_min=$(( ( $(date +%s) - run_epoch ) / 60 - paused_min ))
  fi
  # A wait is recorded when it is **taken** — before the run sleeps in it — so a report read while the run is
  # still asleep can see more waiting than elapsed clock. Work spent is not a negative number, and the budget the
  # summary derives stays the budget: the figure floors at zero, with the wait named beside it either way.
  [ -z "$spent_min" ] || [ "$spent_min" -ge 0 ] || spent_min=0
fi
if [ "$owner_gone" -eq 1 ]; then
  printf 'closed %s · mis-diagnosed %s · skipped %s · blocked %s · cut %s · budget left unknown: the run that wrote this state is gone (pid %s) — it was killed and recorded no stop\n\n' \
    "$closed" "$misdiagnosed" "$skipped" "$blocked" "$cut" "$owner_pid"
elif [ -n "$spent_min" ]; then
  printf 'closed %s · mis-diagnosed %s · skipped %s · blocked %s · cut %s · budget left %s items / %s min · spent %s min%s\n\n' \
    "$closed" "$misdiagnosed" "$skipped" "$blocked" "$cut" \
    "$((items - closed - misdiagnosed - skipped - blocked - cut))" "$((minutes - spent_min))" "$spent_min" "$paused_note"
else
  printf 'closed %s · mis-diagnosed %s · skipped %s · blocked %s · cut %s · budget left unknown: these numbers belong to another run — its window closed at %s and it recorded no stop\n\n' \
    "$closed" "$misdiagnosed" "$skipped" "$blocked" "$cut" "$deadline_at"
fi

printf 'stop: %s\n\n' "$(if [ -n "$stop_reason" ]; then printf '%s' "$stop_reason";
  elif [ "$owner_gone" -eq 1 ]; then printf 'the run that wrote this state is gone — its process (pid %s) is not running and it recorded no reason' "$owner_pid";
  else printf 'not stopped — the run is still going, or it ended without recording a reason'; fi)"

# What each item spent, read against a recorded band (issue #662). The spend was printed beside nothing: the row
# carried the item's tokens, the table a column, and the run a total, so a run that cost twice what the one before
# it cost printed the same shapes as a healthy one. The bound is a recorded value (`LOOP_ITEM_TOKENS_WARN`,
# beside the two clocks in loop.conf) and this line names the items above it, because the column's ratio is read
# against it. It is a **warning, never a stop**: nothing below changes an item's outcome, so the line says what
# was measured and the caller decides. A configuration that records `0` — or no key at all, which is what a ported
# project's loop.conf is — flags nothing, and the line says the flag is off rather than `none`, which would read as
# a measurement nobody made (the rule the spend columns already follow for an unreported figure).
item_tokens_warn=$(loop_conf_value LOOP_ITEM_TOKENS_WARN)
case "$item_tokens_warn" in ''|*[!0-9]*) item_tokens_warn=0 ;; esac
if [ "$item_tokens_warn" -gt 0 ]; then
  printf 'over the item token bound (%s tokens): %s\n' "$item_tokens_warn" "$(jq -r --argjson b "$item_tokens_warn" '
    [ .items[]
      | ((.metrics.tokens // null) as $t
         | if $t == null then null
           else (($t.input // 0) + ($t.output // 0) + ($t.cacheRead // 0) + ($t.cacheWrite // 0)) end) as $tot
      | select($tot != null and $tot > $b)
      | "#\(.issue) \(((($tot / $b) * 10) | round) / 10)x" ]
    | if length == 0 then "none" else join(" · ") end' "$state_file")"
else
  printf 'over the item token bound: off — LOOP_ITEM_TOKENS_WARN records %s, so no item is flagged\n' "$item_tokens_warn"
fi

# The bound's rate is a figure of the run, not a property of one item (issue #809): the run of 2026-11-06 21:19Z
# ended 2 of its 11 items at `LOOP_ITEM_CONTEXT_MAX` — 18 % of what it attempted, 36 Mtok spent with nothing landed —
# and every line that described them read `cut`, while no line said how often it happens. A run that hits the bound
# five times as often as the archive therefore printed the same shapes as a healthy one. So the rate is printed
# beside the token band, the archive's beside it, and the items the bound ended are named with what each kept — the
# turns it spent and the files its leftover patch carries, which is the price of the phase the next run resumes
# (`state.sh done --leftover`).
#
# The class is the record's own mechanism (`stopReason: bound`), the word every arm that ends a child at a bound the
# run enforces already writes, so the line asks for no new vocabulary and a state written before it existed answers.
# Each side is counted over the records that *can* name a mechanism: on 2026-11-09, 160 of the archive's 407 item
# records predate the field (`state#aBlockAtTheRunsBoundIsNoRefusal`), and an item that could never be counted a hit
# inflates the denominator, so the rate reads lower than it is — the one direction this line exists to stop.
bound_share() { # bound_share <hits> <items> → "18.2 %", or "-" when there is nothing to divide by
  local hits="$1" all="$2" tenths
  case "$hits" in ''|*[!0-9]*) printf '-'; return 0 ;; esac
  case "$all" in ''|*[!0-9]*|0) printf '-'; return 0 ;; esac
  tenths=$(( (hits * 1000 + all / 2) / all ))
  printf '%s.%s %%' $(( tenths / 10 )) $(( tenths % 10 ))
}

cap_items=$(jq -r '.items | length' "$state_file")
# What the line counts is the question the requirement asks — how many of the items the run's own bound ended — and
# the record answers it in **two** fields, not one (measured 2026-11-10). The fifth result `cut` exists for exactly one
# reason: a bound the run enforces ended a phase of the item (`state.sh`, `run.sh`: a turn bound, a context bound, the
# child clock, the run's deadline or a vanished writer), so every item carrying it is a hit. `stopReason: bound` is the
# narrower field — a spec requirement keeps it off the rounds a review bound ended, because no item child was ended
# there — and a run whose cuts were all review rounds therefore printed `0 of N` while its items were being thrown
# away: on 2026-11-10 two of the day's three runs recorded three such cuts and the line named none of them. A record
# written before `cut` existed carries the same fact as `blocked` + `bound`, so that pair is counted too; counting
# `cut` alone would lower the archive's rate for the one direction it exists to expose.
#
# The two halves are named apart so the hit is visibly a disjunction and so the mutation set can drop one of them
# without a jq program quoted inside a sed expression.
bound_hit_result='(.result == "cut")'
bound_hit_mechanism='((.stopReason // "") == "bound")'
bound_hit_select="select($bound_hit_result or $bound_hit_mechanism)"
cap_hits=$(jq -r "[.items[] | $bound_hit_select] | length" "$state_file")
cap_named=$(jq -r "[ .items[]
                     | select($bound_hit_select)
                     | \"#\(.issue)\"
                       + (if (.metrics.turns // null) == null then \"\" else \" \(.metrics.turns) turns\" end)
                       + (if (.leftoverFiles // null) == null then \"\" else \", \(.leftoverFiles) files\" end) ]
                   | join(\" · \")" "$state_file")

# The archive is the finished runs `state.sh init` kept beside this one — the directory it derives, a project's
# `LOOP_RUNS_DIR` when it records one — read in one pass over every file it holds, with the run being reported left
# out so the line does not count it twice.
archive_runs=$(loop_conf_value LOOP_RUNS_DIR)
case "$archive_runs" in
  "") archive_runs="$(dirname "$state_file")/runs" ;;
  /*) ;;
  *) archive_runs="$LOOP_ROOT/$archive_runs" ;;
esac
archive_hits="" archive_items=""
if [ -d "$archive_runs" ]; then
  archived=()
  for f in "$archive_runs"/*.json; do
    [ -f "$f" ] || continue
    [ "$f" = "$state_file" ] && continue
    archived[${#archived[@]}]="$f"
  done
  if [ ${#archived[@]} -gt 0 ]; then
    # The archive is read with the run's own selector, over every record rather than over the ones that name a
    # mechanism: the field is the narrower half of the same fact, so an archive counted by it alone would print a rate
    # the run's own side cannot be compared with (`report#theBoundRateIsNamed`).
    archive_hits=$(jq -rs "[ .[] | .items[]? | $bound_hit_select ] | length" "${archived[@]}" 2>/dev/null)
    archive_items=$(jq -rs '[ .[] | .items[]? ] | length' "${archived[@]}" 2>/dev/null)
  fi
fi
case "$archive_hits" in ''|*[!0-9]*) archive_hits="" ;; esac
case "$archive_items" in ''|*[!0-9]*) archive_hits="" ;; esac
archive_clause="-"
if [ -n "$archive_hits" ]; then
  archive_clause="$archive_hits of $archive_items items a bound ended ($(bound_share "$archive_hits" "$archive_items"))"
fi
printf 'bound: %s of %s items (%s) · archive: %s' \
  "$cap_hits" "$cap_items" "$(bound_share "$cap_hits" "$cap_items")" "$archive_clause"
[ -z "$cap_named" ] || printf ' · %s' "$cap_named"
printf '\n'

# The run's own table is the same reader as `state.sh report`, pointed at the same file.
LOOP_STATE_FILE="$state_file" bash "$LOOP_DIR/state.sh" report

printf '\nskipped: %s\n' "$(jq -r '[.items[] | select(.result == "skipped") | "#\(.issue) \(.verdict)\(if .note == "" then "" else " (\(.note))" end)"] | if length == 0 then "none" else join(" · ") end' "$state_file")"
# A falsified item is closed, but not by a change: the run measured that the item's premise does not hold. It is
# named apart from the closures so a run that only falsified does not read as a run that delivered (openspec
# change `falsified-items-close-in-the-loop`).
printf 'mis-diagnosed: %s\n' "$(jq -r '[.items[] | select(.result == "falsified") | "#\(.issue) \(.metrics.rootCause // "")"] | if length == 0 then "none" else join(" · ") end' "$state_file")"
printf 'excluded before any work: %s\n' "$(jq -r '[.excluded[] | "#\(.issue) \(.verdict)\(if (.mechanism // "") == "" then "" else " (\(.mechanism))" end)"] | if length == 0 then "none" else join(" · ") end' "$state_file")"
# The items a bound cut are named apart from the blocks, in the reader's own language: a phase the run ended at
# its own bound asked nobody a question, so the line says which phase was cut and what the next run resumes
# (issues #785, #790). The row reads the item's own `result` and nothing else — the mechanism vocabulary is what
# a state carries for a reader who asks *which* bound it was, not a second predicate the report needs to tell work
# from a refusal. A state a tree wrote before `cut` carried a bound keeps its two `blocked` items in the block
# tally, exactly as that tree counted them. Empty for a run with none, exactly as the other named lists are.
printf 'cut short by the run%ss own bound: %s\n' "'" "$(jq -r '[.items[] | select(.result == "cut") | "#\(.issue) \(.metrics.reviewVerdict // "-")\(if .note == "" then "" else " (\(.note))" end)"] | if length == 0 then "none" else join(" · ") end' "$state_file")"
# The decisions the owner owes are read from what the run *wrote*, never from the verdict a worker chose:
# an item carries the label its own escalation recorded (`state.sh done --decision-label`), and an item the
# pre-screen excluded is escalated by that verdict's value — the one predicate run.sh repeats. Reading the
# verdict instead said `none` while `agent/needs-decision` had just been written (issue #177). An exclusion that
# carries the adoption's own mechanism is the run's own result, not the owner's question (issue #794): the
# mechanism is what `run.sh` writes when the branch is admissible and the merge's inputs refused it.
printf 'decisions the owner owes: %s\n' "$(jq -r --arg d "$(loop_role decisionAgent)" '
  [.items[] | select((.escalatedLabel // "") != "") | "#\(.issue)"]
  + [.excluded[] | select(.verdict == $d and (.mechanism // "") != "unmergeable") | "#\(.issue)"]
  | if length == 0 then "none" else join(" · ") end' "$state_file")"

# The branches are read from git, not from a child's report: the run's own script created them. What each
# of them carries is asked once, by patch identity — the loop lands work as `fix: … (#N)`, so a branch whose
# work is already on the base is not an ancestor of it, and ancestry named leftovers as landings for ever
# (issue #228). The two answers are different: a branch that carries work is an action the caller owes, a
# branch that carries nothing is a leftover the caller may remove — and a flat list of names says neither.
base=$(jq -r '.run.base // ""' "$state_file")
branches=$(git -C "$LOOP_ROOT" branch --list 'loop-*' 'fix/issue-*' --format='%(refname:short)')
branch_table=""
while IFS= read -r branch; do
  [ -n "$branch" ] || continue
  if unlanded=$(loop_unlanded_commits "$LOOP_ROOT" "$base" "$branch"); then
    ahead=$(printf '%s\n' "$unlanded" | grep -c . || true)
  else
    ahead=1
  fi
  case "$ahead" in ''|*[!0-9]*) ahead=1 ;; esac
  branch_table="$branch_table$branch"$'\t'"$ahead"$'\n'
done <<< "$branches"
printf 'branches: %s\n' "$(if [ -n "$branches" ]; then printf '%s' "$branches" | paste -sd' ' -; else printf 'none'; fi)"
landed=$(printf '%s' "$branch_table" | awk -F'\t' '$2 == 0 { printf "%s%s", (n++ ? " " : ""), $1 }')
printf 'landed, removable: %s\n' "$(if [ -n "$landed" ]; then printf '%s' "$landed"; else printf 'none'; fi)"
if [ -d "$LOOP_WORKTREE" ]; then
  printf 'worktree: %s (kept for the next run)\n' "$LOOP_WORKTREE"
else
  printf 'worktree: not present at run end (%s)\n' "$LOOP_WORKTREE"
fi
printf 'state: %s\n' "$state_file"

# ── whether the loop is getting better ───────────────────────────────────────────────────────────
# The report answers three questions about *one run* (#208) and none of them about the loop: a run that cost what
# the last one cost prints the same shapes as a run that is improving, so a person judged the loop's speed by hand
# from `run.json` and `items/*/reviewer-report-1.md` (issue #247). Two of the three figures are already in the
# state this report reads — `metrics.wallSeconds` and `metrics.reviewRounds`, recorded at `state.sh:494` and
# printed only per item — and this line is the run-level answer to them.
#
# The third, the landings that reddened `main`, is a number the state now holds (issue #709 — the change §D4 of
# `run-report-trend-figures` deferred it to): a landing records the commit its merge made, and `state.sh
# score-landings` reads the push run that commit caused once per landing and stores `landedReddenedMain`. This report
# still reads nothing but the state file and git: the network read happens at the landing rather than in every
# report, and a landing nobody has read yet prints as unscored rather than as a landing that did not break the base.
#
# A rate is not settled by one run, and the lines say the run they describe. A figure nobody measured prints `-`
# with its reason, never a zero — the rule the spend columns already keep.
#
# The pass rate is counted from the **verdict** a round ended on, never from `reviewRounds` alone (issue #247,
# review round 1): `reviewRounds` is the round the loop *attempted* — `run.sh:1680` sets it before the reviewer
# runs — so an item whose round 1 refused and then blocked records `1` exactly as one whose round 1 passed. The
# state records the verdict beside the count (`done --review-verdict`), and the three buckets below are the three
# things a reader has to tell apart: a round 1 that passed, a round that ended without a verdict (the reviewer's
# answer was cut, the run's clock ended it, it wrote, or the state predates the field), and an item no round ran
# for. A round that ended without a verdict is neither a refusal nor a pass, so it is named apart and is in no
# rate; a refused round 1 is a refusal even when it is also the last round the item got.
#
# The third figure the trend prints is the shape the spend was made in (issue #757): an item tool calls per
# turn. Every cost in this loop is turns times context, so a session that spent its budget in a chain of
# single-call turns is the defect no other line of the report can show — the archive reads 36 825 calls in
# 33 183 turns (1.11 per turn) at 78 k tokens a turn and 97 % cache read, and halving the calls per turn is worth
# about a third of it. The rate is derived from the two counts the state records (`metrics.calls`,
# `metrics.turns`), and an item below the floor `LOOP_ITEM_CALLS_PER_TURN_MIN` records is named beside the run
# own rate, the way the token line names an item above its band. It is a **warning, never a stop**: nothing in the
# run reads the key, and `0` — or a configuration that records no key at all — prints the floor as off rather than
# as `none`, which would read as a measurement nobody made.
# A value that is not a decimal number is no floor, and it is refused here rather than handed to jq: `--argjson` on a
# malformed value fails the whole program, and a report whose `calls:` line silently disappears is worse than one
# that says the floor is off. `1.` and `.` are refused; a leading zero (`05`) is read as 5, as jq reads it.
calls_floor=$(loop_conf_value LOOP_ITEM_CALLS_PER_TURN_MIN)
printf '%s' "$calls_floor" | grep -Eq '^[0-9]+(\.[0-9]+)?$' || calls_floor=0
trend=$(jq -r --argjson floor "$calls_floor" '
  def median:
    if length == 0 then null
    elif (length % 2) == 1 then .[(length / 2) | floor]
    else ((.[(length / 2) - 1] + .[length / 2]) / 2)
    end;
  def decided: . == "PASS" or . == "NOTES" or . == "FAIL" or . == "round2-ended";
  # Three figures about where the spend went (issue #759), each a count the state records: a duration in
  # milliseconds, printed in seconds below an hour and in hours above it, and a token count printed in Mtok to the
  # one decimal the per-item columns are read at.
  def dur: (. / 1000) | if . >= 3600 then ((. / 3600 * 10 | round) / 10 | "\(.) h")
                       else ((. * 10 | round) / 10 | "\(.) s") end;
  def mtok_n: ((. / 1000000 * 10 | round) / 10);
  def mtok: (. | mtok_n | "\(.) Mtok");
  def mib: ((. / 1048576 * 10 | round) / 10 | "\(.) MiB");
  def items_n: if . == 1 then "1 item" else "\(.) items" end;
  ([.items[] | {v: (.metrics.reviewVerdict // ""), r: (.metrics.reviewRounds // 0)}]) as $it
  | ([.items[] | .metrics.wallSeconds | select(. != null)] | sort) as $walls
  | [$it[] | select(.v | decided)] as $decided
  | [$it[] | select((.v == "PASS" or .v == "NOTES") and .r == 1)] as $first
  | [$it[] | select((.r >= 1) and ((.v | decided) | not))] as $undecided
  | [$it[] | select(.r == 0)] as $never
  | ([$it[] | select(.v != "")] | length) as $withVerdict
  | ( if $withVerdict == 0 and ([$it[] | select(.r >= 1)] | length) > 0
        then "- · this state records no review verdict — it was written before the report read one"
      elif ($decided | length) == 0
        then "- · no round of this run ended in a verdict · \($undecided | length) undecided · \($never | length) never reviewed"
      else "\($first | length)/\($decided | length) first rounds passed · \($undecided | length) undecided · \($never | length) never reviewed" end ) as $review
  | ( if ($walls | length) == 0 then "- · no item recorded a wall time"
      else "median \(($walls | median) | round) s (min \(($walls[0]) | round), max \(($walls[-1]) | round)) · \($walls | length) items" end ) as $wall
  | ([.items[]
      | select((.metrics.calls // null) != null and (.metrics.turns // 0) > 0)
      | {i: .issue, c: .metrics.calls, t: .metrics.turns,
         r: ((.metrics.calls / .metrics.turns) * 100 | round | . / 100)}]) as $calls
  | ( if ($calls | length) == 0 then
        # A state written before this figure existed records tokens for its items and no counts, and a dash with
        # the wrong reason would read as a run of items that answered without a tool — the distinction the review
        # rate dash already makes between "nobody measured" and "this state predates the field".
        if ([.items[] | select(.metrics.tokens != null)] | length) > 0
          then "- · this state records no calls or turns — it was written before the report read them"
          else "- · no item recorded a turn" end
      else ([$calls[].c] | add) as $tc
        | ([$calls[].t] | add) as $tt
        | (((($tc / $tt) * 100) | round | . / 100)) as $ratio
        | ( if $floor > 0
              then ([$calls[] | select(.r < $floor) | "#\(.i) \(.r)"]) as $below
                | "\($ratio) calls per turn (\($tc) calls / \($tt) turns) · "
                  + (if ($below | length) == 0
                       then "none below the recorded floor of \($floor) per turn"
                       else "below the recorded floor of \($floor) per turn: \($below | join(" · "))" end)
              else "\($ratio) calls per turn (\($tc) calls / \($tt) turns) · floor off — LOOP_ITEM_CALLS_PER_TURN_MIN records \($floor), so no item is flagged"
              end )
      end ) as $callline
  # What the shape above cannot show is the rule the worker prompt has stated since #246 and nothing counted (issue
  # #814): a path a child has already read is cited rather than opened again. `tools/loop-spend.py` measures it
  # offline (`LOOP-SPEND-READS calls= duplicate=`), and until the state recorded it the report could not say whether
  # an item followed the rule or ignored it. The line prints the run repeats with the file reads they are a share
  # of, the characters the repeats hold in their own results, how many of those the repeat rule withheld from the
  # the child (issue #831), and the item that repeated most — the same three-part shape the calls line prints. The two
  # halves are kept apart because since #685 they are different numbers: the rule replaces a repeat with one line, so the
  # characters a repeat result holds are either material the child never saw or material it did, and the single figure
  # called `re-injected` said the second about both. A **warning, never a stop**: no key gates it, no item outcome reads
  # it. The field absence is told apart from its null: a record written before the figure existed has no `dupReads` key
  # at all, while one of this era that met an item which opened no file has it as `null` — and the two dashes say which
  # of the two it is.
  | ([.items[]
      | select((.metrics.dupReads // null) != null and (.metrics.reads // 0) > 0)
      | {i: .issue, d: .metrics.dupReads, r: .metrics.reads, c: (.metrics.dupChars // 0),
         w: (.metrics.withheld.chars // null)}]) as $dups
  | ( if ($dups | length) == 0 then
        if ([.items[] | select((.metrics | has("dupReads")) | not)] | length) > 0
           and ([.items[] | select(.metrics.tokens != null or (.metrics.calls // null) != null)] | length) > 0
          then "- · this state records no repeat reads — it was written before the report counted them"
          else "- · no item recorded a file read" end
      else ([$dups[].d] | add) as $td
        | ([$dups[].r] | add) as $tr
        | ([$dups[].c] | add) as $tc
        | if $tr == 0 then "- · no item recorded a file read"
          elif $td == 0 then "\($td) of \($tr) file reads were a second read of a path the session already had · no item repeated a path"
          else ([$dups[] | select(.w != null) | .w]) as $w
            | ( if ($w | length) == 0
                  then "the withheld share of the repeat rule is unrecorded — this state predates the figure"
                  else "\($w | add) chars of them withheld by the repeat rule" end ) as $wline
            | "\($td) of \($tr) file reads were a second read of a path the session already had (\((($td / $tr) * 100) | round) %) · \($tc) chars in the repeat results · \($wline) · worst \(([$dups[] | select(.d > 0)] | max_by(.d)) | "#\(.i) \(.d) of \(.r)")"
          end
      end ) as $readsline
  # Where the clock and the tokens went (issue #759). Each figure is a sum over the items whose state holds it, so
  # the line names how many items it is over — a rate over 3 items and one over 227 are not the same claim. A bucket
  # nobody spent time in is left out of the phase line rather than printed as `0 s`, because a zero there reads as a
  # measurement that was made; the line below says when no gate ran at all.
  | ([.items[] | .metrics.phases | select(. != null)]) as $phs
  | ([.items[] | .metrics.roles | select(. != null)]) as $rol
  # The order the roles line prints is the trio it always printed followed by every other bucket the record holds
  # (issue #832): the bucket a turn no `session` record reaches is counted in is not one of the three, and a line over a
  # literal trio would drop it from a total the state already counts. Read off the records rather than declared here,
  # so a bucket a later reader adds prints without an edit to this file.
  | (["worker", "reviewer", "tail"]
     + ([$rol[] | keys[]] | unique
        | map(select(. as $k | (["worker", "reviewer", "tail"] | index($k)) == null)))) as $order
  # The clock the three buckets are a share of (issue #832): the wall is the sum of the items that recorded a phase
  # figure and a wall of their own, so the line prints the share the buckets are and the time they leave outside
  # both a turn and a tool — a denominator over the items that recorded a wall, never over the run window, which
  # also holds the landings and the holds. An item that recorded phases and no wall is in the numerator and not in
  # the denominator, so the two halves are over one set only where every item of it recorded a wall.
  | ([.items[] | select(.metrics.phases != null) | .metrics.wallSeconds | select(. != null)] | add) as $phsWall
  | ([.items[] | .metrics.afterGate | select(. != null)]) as $gt
  # How much of that gate a child paid outside `gate.sh` (issue #857). The bucket holds every call that ran a check the
  # map names — whoever started it — so the line names the part of it that no gate run paid: 674 s of the 1 497 s of
  # gate-adjacent time item #849 holds, in four calls a child ran by hand. The field is `null` where no child ran one,
  # so a run of none adds no clause rather than `0 s`.
  | ([.items[] | .metrics.gateRepeat | select(. != null)]) as $rep
  | ([$rep[].ms] | add // 0) as $repMs
  | ([$rep[].calls] | add // 0) as $repCalls
  | ( if $repMs > 0
        then " · \($repMs | dur) of that gate was \($repCalls) check\(if $repCalls == 1 then "" else "s" end) a child ran itself"
      else "" end ) as $repnote
  # What a state written before this figure existed holds instead: some spend and no split at all. That is a
  # different answer from a run whose items measured nothing, the distinction the calls line already makes.
  | ([.items[] | select(.metrics.tokens != null or (.metrics.roles // null) != null)] | length) as $hasSpend
  | ([.items[] | select((.metrics.phases // null) != null or (.metrics.roles // null) != null)] | length) as $splitAny
  | ( if ($phs | length) == 0 then
        (if $hasSpend > 0
          then "- · this state records no phase durations — it was written before the report read them"
          else "- · no item recorded a phase duration" end)
      else ([{n: "model", ms: ([$phs[].modelMs] | add)},
             {n: "gate", ms: ([$phs[].gateMs] | add)},
             {n: "other", ms: ([$phs[].otherMs] | add)}]
            | map(select(.ms > 0))) as $b
        | ([$b[].ms] | add) as $total
        | ( if $phsWall != null and $phsWall > 0 then
              ", \(((($total / 1000) / $phsWall) * 100 | round)) % of the \(($phsWall * 1000) | dur) of item wall — \(((($phsWall * 1000) - $total) | if . < 0 then 0 else . end) | dur) of it in neither a turn nor a tool"
            else " — no item wall recorded to read it against" end ) as $wallnote
        | if ($b | length) == 1 then "\($b[0].n) \($b[0].ms | dur) over \($phs | length | items_n)\($wallnote)\($repnote)"
          else "\($b | map("\(.n) \(.ms | dur)") | join(" · ")) · \($total | dur) over \($phs | length | items_n)\($wallnote)\($repnote)"
          end
      end ) as $phaseline
  | ( if ($rol | length) == 0 then
        (if $hasSpend > 0
          then "- · this state records no role split — it was written before the report read one"
          else "- · no item recorded a session role" end)
      # An item that recorded the model a role answered with prints one row per (role, model) tally (issue #823). The
      # tokens of a role no model explained — the default command, an older harness — keep a row of the role alone,
      # because the proposal of the item says such a role needs no label and the rows the line prints must still add
      # up to the total it is over. A tally of zero is no row: a turn that named a model and spent nothing is not a
      # measurement.
      elif ([.items[] | .metrics.roleModels | select(. != null)] | length) > 0 then
        ([.items[] | .metrics.roleModels | select(. != null)]) as $rm
        | ($order
           | map(. as $r
                 | ([$rm[] | .[$r] // {}]
                    | reduce .[] as $o ({}; reduce ($o | to_entries[]) as $e (.;
                        .[$e.key] = ((.[$e.key] // 0) + $e.value)))) as $sum
                 | ($sum | to_entries | map({role: $r, model: .key, tok: .value}))
                   + [{role: $r, model: null,
                       tok: (([$rol[] | (.[$r] // 0)] | add // 0) - ([$sum[]] | add // 0))}]
                 | .[])
           | map(select(.tok > 0))) as $rows
        | if ($rows | length) == 0 then "- · no item recorded a session that reported tokens"
          else "\($rows | map(if .model == null then "\(.role) \(.tok | mtok)"
                              else "\(.role) (\(.model)) \(.tok | mtok)" end) | join(" · ")) · \(([$rows[].tok] | add) | mtok) over \($rol | length | items_n)"
          end
      else ($order
            | map(. as $r | {role: $r, tok: ([$rol[] | .[$r] | select(. != null)] | add)})
            | map(select(.tok != null))) as $by
        | if ($by | length) == 0 then "- · no item recorded a session that reported tokens"
          elif ($by | length) == 1 then "\($by[0].role) \($by[0].tok | mtok) over \($rol | length | items_n)"
          else "\($by | map("\(.role) \(.tok | mtok)") | join(" · ")) · \(([$by[].tok] | add) | mtok) over \($rol | length | items_n)"
          end
      end ) as $roleline
  # The share after the first gate has both halves in the state (`tokens` and `total`, over the sessions that ran a
  # gate), so the line prints the percentage with the two numbers it is a percentage of — the rule the review rate
  # keeps for its own figures.
  | ( if ($gt | length) == 0 then
        (if $splitAny == 0 and $hasSpend > 0
          then "- · this state records no gate share — it was written before the report read one"
          else "- · no item of this run ran a gate in its stream" end)
      else ([$gt[].tokens] | add // 0) as $a
        | ([$gt[].total] | add // 0) as $tot
        | ([$gt[].sessions] | add // 0) as $n
        | if $tot == 0 then "- · the items that ran a gate reported no tokens"
          else "\((($a / $tot) * 100) | round) % — \($a | mtok_n) of \($tot | mtok_n) Mtok, from the first gate of \($n) session\(if $n == 1 then "" else "s" end)"
          end
      end ) as $gateline
  # What the figures above cannot show (issue #841): a stream the child outgrew was rotated, and the record the
  # adapter wrote first states what went — its `turn_end` records, and the bytes that rotation removed. Every figure
  # summed from that stream is a lower bound, and the state now carries the record beside them, so the line names
  # the items whose figures are short, with the figure each dropped: the totals would print the same whether
  # one item was cut or nine (measured on the run of 2026-11-09T21:42:41Z: nine of fifteen, 1 697 dropped turns
  # against the 1 372 the state recorded). The two figures are read from **two** selections and the line names both,
  # because a rotation that removed only deltas leaves a stream the bound cut and no figure of it short: the turns
  # and the item list are the items that lost a turn, while the bytes are summed over every item whose stream
  # carries a rotation record — that figure is what the bound was about, and summing it over the truncated items
  # alone under-reported the run and read as a total (the second reading of issue #841). The absence of the field is told
  # apart from its null, as every other line here does: a record written before this figure existed has no
  # `streamRotation` key at all, while one of this era that met a run whose streams stayed inside their bound has
  # it as `null`. A **warning, never a stop**: nothing reads it to end a child.
  | ([.items[] | select(.metrics.streamRotation != null)
      | {i: .issue, b: (.metrics.streamRotation.droppedBytes // 0)}]) as $rot
  | ([.items[] | select((.metrics.streamRotation.droppedTurnEnds // 0) > 0)
      | {i: .issue, t: .metrics.streamRotation.droppedTurnEnds}] | sort_by(-.t)) as $trunc
  | ([.items[] | select((.metrics | has("streamRotation")) | not)] | length) as $rotless
  | ( if ($rot | length) == 0 then
        if $rotless > 0
          then "- · this state records no stream rotation — it was written before the report counted one"
          else "- · no item had its stream rotated" end
      elif ($trunc | length) == 0 then
        "no figure of this run is a lower bound — \([$rot[].b] | add | mib) removed by the \($rot | length) rotated stream\(if ($rot | length) == 1 then "" else "s" end), no turn dropped · "
          + "\([$rot[] | "#\(.i)"] | join(" · "))"
      else "\($trunc | length) of \(.items | length) items \(if ($trunc | length) == 1 then "is" else "are" end)"
           + " a lower bound — \([$trunc[].t] | add) turns dropped and \([$rot[].b] | add | mib) removed by the \($rot | length) rotated stream\(if ($rot | length) == 1 then "" else "s" end) · "
           + "\([$trunc[] | "#\(.i) \(.t)"] | join(" · "))"
      end ) as $truncline
  # What the run closed over (issue #811). A close over a red gate carries the clause it was waived with, and this
  # is the count the issue asks for: the waived closes out of the closed items, with each suite a waiver named,
  # because the per-suite total is what tells a flaky suite from a broken one. The absence of the field is told
  # apart from its null, as every other line here does: a record written before the field existed has no
  # `gateWaived` key at all, while one of this era that closed over a gate that passed has it as `null`.
  | ([.items[] | select(.result == "done")] | length) as $closedItems
  | ([.items[] | select(.result == "done" and (.metrics.gateWaived // "") != "")
      | (if (.metrics.gateWaivedSuite // "") == "" then "-" else .metrics.gateWaivedSuite end)]) as $waived
  | ( if ($waived | length) > 0 then
        "\($waived | length) of \($closedItems) closed items (\($waived | group_by(.) | map("\(.[0]) ×\(length)") | join(", "))) · the gate did not pass"
      elif $closedItems == 0 then "- · no item of this run closed"
      elif ([.items[] | select(.result == "done" and ((.metrics | has("gateWaived")) | not))] | length) > 0
        then "- · this state records no gate waiver — it was written before the report counted one"
      else "0 of \($closedItems) closed items · no close was over a gate that did not pass"
      end ) as $waivedline
  | "  review: \($review)\n  waived: \($waivedline)\n  wall:   \($wall)\n  calls:  \($callline)\n  reads:  \($readsline)\n  phases: \($phaseline)\n  roles:  \($roleline)\n  after the first gate: \($gateline)\n  truncated: \($truncline)"
' "$state_file")
printf '\n'
printf "trend — this run's own items; no rate is green or red in one run:\n"
printf '%s\n' "$trend"
landings=$(jq -r '
  [ .items[] | select((.merged.branch // "") != "") ] as $l
  | ([ $l[] | select(.landedReddenedMain != null) ]) as $scored
  | ([ $l[] | select(.landedReddenedMain == true) ] | length) as $red
  | ([ $l[] | select((.merged.sha // "") != "" and .landedReddenedMain == null) ] | length) as $unscored
  | ([ $l[] | select((.merged.sha // "") == "" and .landedReddenedMain == null) ] | length) as $noCommit
  | ( if $unscored > 0 then " · \($unscored) unscored" else "" end ) as $u
  | ( if $noCommit > 0 then " · \($noCommit) records no commit" else "" end ) as $n
  | if ($l | length) == 0 then "- · no item of this run landed"
    elif ($scored | length) == 0 then "- · no landing is scored yet\($u)\($n)"
    else "\($red) of \($scored | length) reddened main\($u)\($n)" end
' "$state_file")
printf '  landings: %s\n' "$landings"

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
    (.excluded[] | select($d != "" and .verdict == $d and (.mechanism // "") != "unmergeable")
      | ["excluded", (.issue | tostring), .verdict, (.reason // ""), (.evidence // "")])
    | @tsv' "$state_file"
}

# The item root is the loop root's `items` directory, and which directory that is depends on the shape of the state
# the caller named (issue #855). The state in place sits in the loop root (`lib.sh:154`), so its own directory holds
# `items`; the state a finished run is reported on is the copy `state.sh init` kept under `runs/` (`state.sh:183-199`,
# whose `RUNS_DIR` defaults to `<state dir>/runs`, `state.sh:109`), and the item directories never move with that copy.
# Both shapes are answered by the tree itself: the `items` directory beside the state, and where the state sits one
# level down, the one beside its parent. A tree holding neither keeps this file's own answer, `$state_dir/items`.
state_dir=$(dirname "$state_file")
item_root="$state_dir/items"
[ -d "$item_root" ] || item_root="$(dirname "$state_dir")/items"
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
# exists: `none` must never hide commits. What it carries was asked above, by patch identity — the loop
# lands work as `fix: … (#N)`, so a branch whose work is already on the base is not an ancestor of it, and
# ancestry named leftovers as landings for ever (issue #228). A range git cannot answer is named rather than
# counted as zero.
while IFS=$'\t' read -r branch ahead; do
  [ -n "$branch" ] || continue
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
done <<< "$branch_table"

# A label write the tracker refused is an obligation no other source can discharge: the run recorded it, and the
# item's state on the tracker is stale in a way a reader who trusts only the tracker cannot see — an item with no
# decision at all where the run recorded one, or an in-progress mark on an item nothing is working (issue #279).
while IFS=$'\t' read -r issue failed; do
  [ -n "${issue:-}" ] || continue
  [ -n "${failed:-}" ] || continue
  actions[${#actions[@]}]="set #$issue's tracker state by hand — the run could not write $failed, so the item's state there is stale — gh issue view $issue"
done < <(jq -r '.items[] | select(((.labelFailures // []) | length) > 0)
  | [(.issue | tostring), ((.labelFailures // []) | join(" "))] | @tsv' "$state_file")

# An item that ended without a gate result is the one failure a caller cannot reconstruct from the table:
# the transcript beside its id says how far the item got. A `cut` is not that (issue #788): a gate of its own was
# never owed, because the run ended its child first, and the block below names it as the item to take up again.
while IFS=$'\t' read -r issue; do
  [ -n "${issue:-}" ] || continue
  actions[${#actions[@]}]="re-run the gate for #$issue — the run recorded no gate result — transcript $item_root/$issue/worker-report.md"
done < <(jq -r '.items[] | select(.result != "done") | select(.result != "falsified") | select(.result != "cut") | select((.metrics.gate // "") == "") | .issue | tostring' "$state_file")

# The item the run's own bound ended is work and not a question (issue #788): it carries no decision label, so this is
# where a caller learns it is waiting — with the phase that was cut and the branch the attempt left, which the next
# run takes up. One item is one line, and the line is the resume line (issue #850): a `cut` is the result every bound
# the run enforces leaves on the item — the child clock, the turn bound, the token bound and the run's own deadline
# alike (`state.sh done --stop-reason`, and `run#childIsEndedAtTheRunsDeadline` reads the deadline's own record as
# `bound`) — so the patch a cut child left is named here, on the line that already names the item, and the recovery
# arm below is left to the record that carries kept work and no bound cut. Measured 2026-11-11T11:37:08Z: #847 and
# #839 were `cut` with `bound` and a leftover, and the report printed each of them twice — 2 of the 12 actions it
# listed — the second line saying the run's clock had ended them with 7 h 51 m of the run's budget still to go.
while IFS=$'\t' read -r issue note patch; do
  [ -n "${issue:-}" ] || continue
  # The note the run wrote already ends with the patch's own sentence (`run.sh:3433`, `:3499`), so the line appends it
  # only for a record whose note does not carry the path — a state written by hand, or by a tree older than #674. The
  # transcript the line names is the item's own directory under the loop root the state belongs to (`item_root`, issue
  # #855): the next run is handed the item and reads several files of it, so the directory is what its reader opens.
  kept=""
  if [ -n "$patch" ] && [ "${note#*"$patch"}" = "$note" ]; then
    kept=" · its work is kept at $patch"
  fi
  actions[${#actions[@]}]="resume #$issue — the run ended a child of it at a bound, so no decision is owed — ${note:-the phase the bound cut is resumable}${kept} · transcript $item_root/$issue"
done < <(jq -r '.items[] | select(.result == "cut") | [(.issue | tostring), (.note // ""), (.leftover // "")] | @tsv' "$state_file")

# The work a child the run ended left is kept as a patch, and the item's own record names it (issue #674). An item the
# run's clock ended carries no decision label, so this line is the only place the report tells a caller that the run,
# not the item, ended it — and where the work it left is. Its guard is the record's result and not its patch (issue
# #850): the guard that read the patch named a cause the record does not carry, because a cut child's leftover is a
# consequence of the cut and not its cause, so the same item was listed twice. What is left here is the record that
# carries kept work and no bound cut — the shape a tree wrote before `cut` existed, whose end was that run's clock.
while IFS=$'\t' read -r issue patch; do
  [ -n "${issue:-}" ] || continue
  [ -n "${patch:-}" ] || continue
  actions[${#actions[@]}]="recover #$issue — the run's clock ended it and its work is kept at $patch"
done < <(jq -r '.items[] | select(.result != "cut") | select((.leftover // "") != "") | [(.issue | tostring), .leftover] | @tsv' "$state_file")

if [ -d "$LOOP_WORKTREE" ]; then
  actions[${#actions[@]}]="keep or remove the worktree $LOOP_WORKTREE — the next run reuses its warm build; --clean-worktree removes it at the end of a run"
fi

printf 'actions the caller owes:\n'
if [ "${#actions[@]}" -eq 0 ]; then
  # Empty only when every one of the seven sources was read and empty — not because a list was skipped.
  printf '  none — no decision, no branch carrying commits, no kept worktree, no gate left incomplete, no item ended at a bound, no leftover patch, no label write the tracker refused\n'
else
  n=1
  for action in "${actions[@]}"; do
    printf '  %s. %s\n' "$n" "$action"
    n=$((n + 1))
  done
fi
