#!/usr/bin/env bash
# run.sh — one command starts a budgeted, unattended run. No dialogue, no session, no question.
#
# This script is the run's only writer and its only git actor: it takes the lock, owns the state, creates
# the worktree and the branches, commits each step and writes every label. The agent processes it starts
# get a prepared work package and a working directory, and nothing else — not a budget, not a queue, not
# a tracker credential, and never a decision about whether an item closed (design D2, D3, D7, D12).
#
# A script cannot ask, so every budget is a flag whose default is recorded in `loop.conf`, and the run stops on
# its own authority: the item cap, the deadline, an empty queue, the recorded streak of consecutive refusals
# (`LOOP_REFUSAL_STREAK_STOP`, issue #804), or a remaining time the next item cannot be paid for. The stop
# conditions are `state.sh next`'s, so the run never does its own budget arithmetic (invariant 3). A block the
# run's own bound caused is not a refusal and holds no pair (issue #786).
#
# One condition is neither a stop nor a refusal: the provider's quota, read by the project's own reader
# (`LOOP_QUOTA_CMD`, `tools/loop-quota.sh` here). A reader that holds makes `state.sh next` exit 6, and this run
# **pauses** — prints the reader's reason, records the wait, moves its deadline with it (a pause is not work) and
# asks again. `--no-quota` turns that reader off for this run, because the reader belongs to one provider and which
# provider a run spends is a value only the operator has (invariant: the engine names no provider, D1); the header
# names the reader it armed, or `quota=off`.
#
# Exit: 0 the run ended (including "nothing eligible"), 2 usage/malformed input, 3 stop (unauthorised
#       identity, or nothing eligible), 5 refuse (another run holds the lock, the decision cap is
#       reached, the base has moved, the kept worktree carries a previous run's work).
#
# The run records the one condition that ended it (`state.sh stop`), so the printed report and the state
# file state the same end (#184).
#
# Usage: run.sh [--items N] [--minutes M] [--review on|off] [--may-close yes|no] [--push yes|no]
#               [--max-decisions N] [--max-review-rounds N] [--review-read-only on|off]
#               [--pre-gate-review on|off] [--strict-outside-write yes|no] [--dry-run] [--no-quota]
#               [--clean-worktree] [--reset-worktree] [--base <sha>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"
# shellcheck source=tools/loop/evidence.sh
source "$here/evidence.sh"
# shellcheck source=tools/loop/leftovers.sh
source "$here/leftovers.sh"
# shellcheck source=tools/loop/child.sh
source "$here/child.sh"

items=""
minutes=""
review=""
pre_gate=""
may_close=""
max_decisions=""
max_review_rounds=""
push="no"
read_only="on"
strict_outside_write=""
dry_run=0
clean_worktree=0
reset_worktree=0
base=""
no_quota=0
while [ $# -gt 0 ]; do
  case "$1" in
    --items) need_value "$@"; items="${2:-}"; shift 2 ;;
    --minutes) need_value "$@"; minutes="${2:-}"; shift 2 ;;
    --review) need_value "$@"; review="${2:-}"; shift 2 ;;
    --may-close) need_value "$@"; may_close="${2:-}"; shift 2 ;;
    --push) need_value "$@"; push="${2:-}"; shift 2 ;;
    --max-decisions) need_value "$@"; max_decisions="${2:-}"; shift 2 ;;
    --max-review-rounds) need_value "$@"; max_review_rounds="${2:-}"; shift 2 ;;
    --review-read-only) need_value "$@"; read_only="${2:-}"; shift 2 ;;
    --pre-gate-review) need_value "$@"; pre_gate="${2:-}"; shift 2 ;;
    --strict-outside-write) need_value "$@"; strict_outside_write="${2:-}"; shift 2 ;;
    --base) need_value "$@"; base="${2:-}"; shift 2 ;;
    --dry-run) dry_run=1; shift ;;
    --no-quota) no_quota=1; shift ;;
    --clean-worktree) clean_worktree=1; shift ;;
    --reset-worktree) reset_worktree=1; shift ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) loop_die "run: unknown argument $1" ;;
  esac
done

loop_resolve
loop_roles_load
# The resolved paths go to every child, so all of them agree on which run and which project they belong to.
export LOOP_STATE_FILE="$LOOP_STATE_FILE" LOOP_WORKTREE="$LOOP_WORKTREE"
# The profile stays with the process that read it, because the child works in another tree and reads the profile
# beside that tree's engine. Measured 2026-10-23 finishing #273 (issue #277): the export below was the run's own
# default, so every gate of a change whose delta edits `loop.conf` measured the profile of the project the run was
# started in — the rule the change was replacing — and the log could not tell; the repair was a hand-pinned re-run.
# `LOOP_CONF` is the self-test's seam and is read here (`loop_resolve` records it as `LOOP_CONF_PATH`); `export -n`
# keeps the value this shell already read and takes it out of every child's environment.
export -n LOOP_CONF

# The quota check is the one condition a run may be told to skip without editing the configuration: `--no-quota`
# empties the key **in this process**, and every child that reads the profile reads it as set-but-empty — the key is
# written as a fill-if-unset expansion, so a set-but-empty value is the switch — which means `state.sh next` asks
# nobody. The reader belongs to one provider and this engine cannot tell which provider a given run spends: that is
# a project value (D1), and the operator is the one who holds it.
if [ "$no_quota" -eq 1 ]; then
  export LOOP_QUOTA_CMD=""
fi

# The measuring command the worker is handed (issue #246). A project that configures none still renders a task,
# so the placeholder carries a sentence instead of an empty span, and no render site reads the key directly.
measure_cmd="${LOOP_MEASURE_CMD:-}"
[ -n "$measure_cmd" ] || measure_cmd="(this project configures no measuring command)"

: "${items:=$(loop_conf_value LOOP_ITEMS_DEFAULT)}"
: "${minutes:=$(loop_conf_value LOOP_MINUTES_DEFAULT)}"
: "${review:=$(loop_conf_value LOOP_REVIEW_DEFAULT)}"
: "${pre_gate:=$(loop_conf_value LOOP_PRE_GATE_REVIEW_DEFAULT)}"
: "${may_close:=$(loop_conf_value LOOP_MAY_CLOSE_DEFAULT)}"
: "${max_decisions:=$(loop_conf_value LOOP_MAX_DECISIONS_DEFAULT)}"
: "${max_review_rounds:=$(loop_conf_value LOOP_MAX_REVIEW_ROUNDS_DEFAULT)}"
# Whether a difference in the project's own tree decides an item (#274, #276, #285). Recorded, because it is a
# fact about a project: a tree no peer session and no concurrent run writes can carry the verdict, and one that a
# landing fast-forwards cannot (`worktree.sh merge-item` merges the base in the project tree).
: "${strict_outside_write:=$(loop_conf_value LOOP_STRICT_OUTSIDE_WRITE_DEFAULT)}"
: "${strict_outside_write:=no}"

for pair in "items=$items" "minutes=$minutes" "max-decisions=$max_decisions"; do
  case "${pair#*=}" in ''|*[!0-9]*) loop_die "run: ${pair%%=*} must be a non-negative integer" ;; esac
done
# Zero rounds is not a cheap review, it is a review that never runs: the item would be blocked on a round that
# never happened, and the branch would carry a `review-failed-round-0` that refuses it for ever.
case "$max_review_rounds" in ''|*[!0-9]*|0) loop_die "run: --max-review-rounds must be a positive integer" ;; esac
case "$review" in on|off) ;; *) loop_die "run: --review must be on or off" ;; esac
case "$pre_gate" in on|off) ;; *) loop_die "run: --pre-gate-review must be on or off" ;; esac
# A run with no reviewer has nothing to read the diff before the gate, and the split exists for that reading
# (issue #758): the diff is reviewed, its findings are applied by a fresh child, and the item's one gate is run on a
# change a reader has already agreed with. So the shape is folded here, once, and the header, the prompt the change
# child is handed and the round below all read the one value.
[ "$review" = on ] || pre_gate=off
case "$may_close" in yes|no) ;; *) loop_die "run: --may-close must be yes or no" ;; esac
case "$push" in yes|no) ;; *) loop_die "run: --push must be yes or no" ;; esac
case "$read_only" in on|off) ;; *) loop_die "run: --review-read-only must be on or off" ;; esac
[ "$strict_outside_write" = on ] || [ "$strict_outside_write" = off ] || [ "$strict_outside_write" = yes ] ||
  [ "$strict_outside_write" = no ] || loop_die "run: --strict-outside-write must be yes or no"
case "$strict_outside_write" in yes|on) strict_outside_write=yes ;; *) strict_outside_write=no ;; esac

# The one indirection through which every child is started (D3, D15). An empty value would start nothing
# and report success for it, so it stops the run instead.
if [ -z "$(printf '%s' "${LOOP_AGENT_CMD:-}" | tr -d '[:space:]')" ]; then
  loop_die "run: LOOP_AGENT_CMD is empty in $LOOP_CONF_PATH — the run has no way to start an agent process"
fi

# The placement reading asks the kernel for the child's own process tree with `pgrep` (issue #288), which is the
# engine's only procps dependency since `pid_in_child_tree`'s `ps` call went with the wide rule. Both calls in
# `loop_tree_pids` swallow their stderr, so a host without procps answers nothing and the walk names only the
# child; `outside_write_seen`'s filters drop that pid and every difference is then recorded and none placed, with
# no line saying so — the deciding half of the rule never fires while `--strict-outside-write no`, the default,
# still lets the item land. The suite refuses such a host, but a run started by hand never consults the suite
# (issue #703), so the run refuses here, beside the agent-command check and before a worktree, a pre-screen, a
# branch or a child exists.
if ! command -v pgrep >/dev/null 2>&1; then
  loop_die "run: pgrep is required — the placement reading asks the kernel for the child's own process tree; a host without procps would record every difference and place none"
fi


# The run header names every effective value, so a run can be repeated and audited (D2). The child's
# wall-clock is a recorded value too (`loop.conf`), so it is named here with the rest of the budget — as the
# bound the child is given, so the header and the `LOOP-TIMEOUT` line that ends it cannot disagree.
child_max=$(child_cap)

# What the header names, so a run can be repeated and audited (D2) and the header and the `LOOP-TIMEOUT` line that
# ends a child cannot disagree about the bound they apply: every role `agent_roles` below can name, in that order,
# the rework round included — a bound the header does not print is one a run cannot be audited for (issue #842).
child_turn_bounds=""
for role in worker worker-gate worker-tail worker-rework reviewer reviewer-pre-gate; do
  child_turn_bounds="${child_turn_bounds:+$child_turn_bounds,}$role:$(child_turn_bound "$role")"
done

# How many of the gate's own checks one child may start by hand (issue #882). `LOOP_GATE_RUNS_MAX` bounds the gate's
# own full runs, but the same work a child starts outside `gate.sh` met no bound at all: it was counted after the
# child had ended, as `metrics.gateRepeat` (issue #857), and no arm acted on it. Measured on run
# 2026-11-13T05:47:11Z — 226 hand-run checks holding 1.3 h of its 3.1 h gate bucket, and the walls of #853, #865 and
# #864 80 %, 60.4 % and 58.8 % gate — so the count is read while the child runs, from the stream it appends to,
# exactly as its turns are. Unrecorded or `0` is no bound, the rule every other bound in the profile follows (D16).
child_checks_bound=$(loop_conf_value LOOP_CHILD_CHECKS_MAX)
case "$child_checks_bound" in ''|*[!0-9]*) child_checks_bound=0 ;; esac

# A bound priced in what a turn costs, not in how many turns there are (issue #760). The turn bound above counts
# turns, and a turn is a unit of shape rather than of cost: every turn re-reads the whole conversation, so the same
# turn count costs 40 k or 240 k depending on how far the context has grown — over this project's archive (227 items,
# 2026-11-03, `tools/loop-spend.py --json`) tokens per item are p50 10.4 M and p90 23.6 M against turns per item p50
# 140 and p90 273, a 5x spend over a 2x range of turns. The reading that varies already exists
# (`tools/loop/state.sh`'s `child_usage_json`, the largest `input + cacheRead` a turn reported), but only the report
# reads it, after the item is over. So the run reads it too, where the wall clock and the turn count are already
# enforced, and takes two decisions from it: a checkpoint at `LOOP_ITEM_CONTEXT_WARN`, at which the item's first worker
# is split and continued (issue #856), and an end at `LOOP_ITEM_CONTEXT_MAX`. Measured over the 300 item records of
# `.pi/issue-loop/run.json` and `runs/*.json` (2026-11-05): the largest context per item is p50 107 669, p75 143 124,
# p90 174 984, p95 191 815, maximum 244 764. The checkpoint was drawn at that p75 while it only printed a line; since
# it ends the child, the reading is the split's own (issue #1003, the table in `tools/loop/loop.conf`): 90 000, where
# the baseline a split pays again is outweighed by the turns it saves. The maximum is 200 000 — above the p95 and at
# the window every provider
# this project runs sits at, where a turn costs 1.86x the archive's median turn (200 000 / 107 669) — and 10 of the
# 300 recorded items (3.3 %) reached it. Both are `0` or absent = no bound, the shape `LOOP_MIN_FREE_MIB` and
# `LOOP_ITEM_TOKENS_WARN` have, so a project that records neither owes nothing by them (D16).
item_context_warn="${LOOP_ITEM_CONTEXT_WARN:-0}"
case "$item_context_warn" in ''|*[!0-9]*) item_context_warn=0 ;; esac
item_context_max="${LOOP_ITEM_CONTEXT_MAX:-0}"
case "$item_context_max" in ''|*[!0-9]*) item_context_max=0 ;; esac

# What a child the ceiling ended listed (issue #956). Such a child writes no report block — the run ended it, so
# there is no `LOOP-DECISION` and no `LOOP-RED-TEST` — and what it did leave is the prose it had written when the
# bound's own line was appended: measured 2026-11-17, #894's continuation report states the shape in that prose
# ("sections 2.2-2.6, 3.x and 4.x do not fit one child's context bound") and asks for the phases to become items of
# their own, which is the list a person reshapes the item from. The run's own lines are appended after that prose, so
# they are excluded here: the line read is the child's, from the report the run already holds.
continuation_listing() { # <report> → the child's own last line, or nothing when it wrote none
  sed -n '/^LOOP-[A-Z][A-Z-]*: /!p' "$1" 2>/dev/null | sed -e 's/[[:space:]]*$//' -e '/^$/d' | tail -1
}
# What the header names, for the reason the per-role turn bounds are named there: a bound a run enforces and does not
# print is a bound nobody can audit (issue #842).
child_context_bounds=""
for role in worker worker-gate worker-tail worker-rework reviewer reviewer-pre-gate; do
  child_context_bounds="${child_context_bounds:+$child_context_bounds,}$role:$(child_context_bound "$role")"
done


# The floor an item may not start below, in MiB. Absent or malformed keeps the recorded default, exactly as the
# child's wall-clock does, and `0` disables the check for a project that has nothing to compare against.
min_free_mib="${LOOP_MIN_FREE_MIB:-1024}"
case "$min_free_mib" in ''|*[!0-9]*) min_free_mib=1024 ;; esac
free_mib=$(host_free_mib)

# The two numbers of a quota wait: how long the run sleeps when the reader names no reset instant, and the longest
# wait it will take before it ends the run instead (`0` = no cap). Read here beside the host floor, because both
# are the run's own numbers about a condition outside it — how long a pause may be is not something the reader of
# a balance knows, and how long to poll is not something the provider's answer carries.
quota_poll="${LOOP_QUOTA_POLL_SECONDS:-900}"
case "$quota_poll" in ''|*[!0-9]*|0) quota_poll=900 ;; esac
quota_hold_max="${LOOP_QUOTA_HOLD_MAX_SECONDS:-0}"
case "$quota_hold_max" in ''|*[!0-9]*) quota_hold_max=0 ;; esac


# A role that does not run on the project's command is named in the header, because a run that spends a different
# model on part of its work is not the same run as one that does not, and nothing else in the run's own line would say
# so (`LOOP_AGENT_CMD_<ROLE>` in loop.conf). The reading is the child's own resolution and not the role's recorded
# value (issue #821): a role that inherits — the round before the gate on the reviewer's key — is named here too. A
# class-qualified command is per item while the header is written before any item is read, so it is named by the
# child's transcript instead. `-` is the ordinary case: every child through `LOOP_AGENT_CMD`.
agent_roles="-"
for role in worker worker-gate worker-tail worker-rework reviewer reviewer-pre-gate; do
  [ "$(child_agent_cmd_key "$role" "")" != LOOP_AGENT_CMD ] || continue
  if [ "$agent_roles" = "-" ]; then agent_roles="$role";
  else agent_roles="$agent_roles,$role"; fi
done
# …and which reader answers the quota question, as one token: the pair is the run's own record of a condition it
# waited on, and `off` is a fact a reader must be able to see (a run that paused nothing because nobody asked looks
# exactly like a run with quota to spare). The command string carries spaces, so the token is its file name — the
# header is a line of `key=value` tokens a reader greps, and every value already there is one word.
quota_reader=off
if [ -n "${LOOP_QUOTA_CMD:-}" ]; then
  quota_reader="${LOOP_QUOTA_CMD##* }"
  quota_reader="${quota_reader##*/}"
fi
# The bound the record keeps itself inside, named beside the path so that a reader who finds `<file>.1` knows what
# made it (D6). It is the recorded value and the writer's own fallback is the same number, so this is what the writer
# used and not a second reading of the configuration.
record_max=$(loop_conf_value LOOP_RUN_EVENTS_MAX_BYTES)
record_max="${record_max:-$LOOP_EVENT_DEFAULT_MAX_BYTES}"
# The run's start is one fact, and the header line is that fact rendered: the values a reader greps out of this line
# and the values the report's cross-check counts come from one call, so the two cannot drift apart (issue #894, D3).
# The state file stays the authority for the arithmetic — this is the run's own statement of the flags it started
# with, and the record is where a later reader finds it without the transcript.
loop_emit run-start items="$items" minutes="$minutes" review="$review" pre_gate="$pre_gate" \
  may_close="$may_close" push="$push" max_decisions="$max_decisions" \
  max_review_rounds="$max_review_rounds" read_only="$read_only" \
  strict_outside_write="$strict_outside_write" child_max="$child_max" \
  child_turns="$child_turn_bounds" child_checks="$child_checks_bound" \
  item_context_warn="$item_context_warn" item_context_max="$item_context_max" \
  item_context_bounds="$child_context_bounds" min_free_mib="$min_free_mib" \
  free_mib="${free_mib:-unknown}" dry_run="$dry_run" reset_worktree="$reset_worktree" \
  agent_roles="$agent_roles" quota="$quota_reader" record="$LOOP_RUN_EVENTS" record_max="$record_max"
# What follows the header, and where to watch it happen (issue #191). The pre-screen is the next step and it is
# not instant — measured 2026-10-20: 53 s for 84 records over 188 tracker items — and a header that named only its
# arguments left the operator to decide from silence whether the run was alive, which is how two runs of that day
# were interrupted by hand inside that window. The two views that exist while a run works are named here, so the
# answer does not depend on the operator remembering them.
loop_emit run-live-view state_dir="$(dirname "$LOOP_STATE_FILE")"

# The state is written below, after the identity and the lock: the lock is what keeps one run per
# repository, and the state it would replace belongs to the run that is working.
separator=$(loop_compat_profile_value namespaceSeparator)
separator="${separator:-/}"
# The label an item awaiting a decision carries, and the verdict that means it: both are values of the
# profile's vocabulary, read from the profile's `roles` section (D9, D16).
decision_agent=$(loop_role decisionAgent)
decision_label="agent${separator}${decision_agent}"

# The pre-screen writes the run's state and never the tracker — a dry run must write no label — so the
# verdicts of its own that mean "a person owes this decision" are escalated here, under the item, before
# anything is picked. An item this run has already recorded was escalated or blocked by the item loop (the
# pre-screen runs again when the queue empties mid-run), so it is not escalated a second time.
escalate_prescreen_decisions() {
  [ "$dry_run" -eq 0 ] || return 0
  [ -n "$decision_agent" ] || return 0
  jq -r --arg d "$decision_agent" '
    ([.items[].issue] | unique) as $handled
    | .excluded[] | select(.verdict == $d)
    # An adoption the run itself decided — a branch whose own evidence is admissible and whose merge the tree, the
    # base or the replay refused — is recorded with the mechanism `unmergeable` beside the exclusion and is not a
    # question the owner owes (#794): the verdict stays the one the pre-screen read, and the mechanism is what
    # makes the difference between it and a decision.
    | select((.mechanism // "") != "unmergeable")
    | select((.alreadyEscalated // false) != true)
    | select(.issue as $i | ($handled | index($i)) | not)
    | [(.issue | tostring), .reason, (.evidence // "")] | @tsv
  ' "$LOOP_STATE_FILE" 2>/dev/null |
    while IFS=$'\t' read -r decision_issue decision_reason decision_evidence; do
      [ -n "$decision_issue" ] || continue
      loop_labels escalate "$decision_issue" \
        --reason "${decision_reason:-the item awaits a decision}" \
        --evidence "${decision_evidence:-the run exclusion record}" >&2 || true
    done
}


# An item whose earlier attempt *finished* but was never merged is an item no later path could take: its
# branch carries commits the base does not, `start-item` refuses it, and two such refusals end a run — the
# measured 2026-10-18 outcome, where two branches each held a finished change, a green gate and an archived
# change on the same base and both were left for a person (issue #176). The run therefore decides the branch
# by what it **carries** and what it can **check**: one archived change of its own and a green verification
# record inside it. `worktree.sh adopt-item` merges it onto the base, and the item is closed through the same
# closure path a worker's item goes through, with the branch, its commits and the evidence it was checked for
# recorded in the run state and named in the report. A branch that does not carry that evidence is not
# touched: its refusal and its escalation stay exactly as they were.
# The refusal `adopt-item` printed, kept for the caller that records it (issue #794) — empty between attempts, so a
# caller reads the refusal of the attempt it just made and never an older one.
adoption_refusal=""

# The branch the success path just committed on is the fix's only home until it is merged: `end-item` commits on
# `fix/issue-<n>-<slug>`, and the loop's only merge, `adopt-item`, is reached for a *previous* attempt's branch —
# which needs the item to be visited again, and a closed item is never picked (#229). Measured 2026-10-20 on
# item #201: the run closed it with its two commits still on the branch and the base at the run's base, and a
# person fast-forwarded `main` 35 minutes later. So the run merges what it committed, before it closes, and a
# refused merge blocks the item instead: a closure the tree does not back is what `status/fixed-by` must never
# mean. The line the merge prints is the run's record of it; the refusal is left in `merge_refusal` for the
# block's reason.
merge_refusal=""
# What a refusal named is kept beside its reason, because the record is what the report prints (issue #883): the
# branch this merge was asked for, and the paths the refusal read as `git status --porcelain` printed them. The
# note keeps the refusal's prose; the report reads these two instead of parsing the branch and the paths back out
# of it.
merge_refused_branch=""
merge_refused_paths=()



# The run's end: the report, and the worktree the run asked not to keep (D4). The flag is the run's, so it
# holds wherever the run ends — and the pre-screen can end one earlier than the item loop does, because a
# decision it records leaves nothing eligible (issue #175).
#
# A kept worktree is kept for its *sources*: the next run inherits the checkout and the git state, while what
# the next build regenerates is removed here rather than carried into the next run (issue #210, `worktree.sh
# strip-scratch`, the paths from `LOOP_WORKTREE_SCRATCH_PATHS`). Nothing is removed when the tree is removed
# whole, and nothing is removed from a tree the caller named as the thing to repair with `--reset-worktree`:
# that flag is the caller's own statement about what the previous run left there, and this step makes no
# further decision about the tree's contents.
end_run() {
  bash "$LOOP_DIR/report.sh"
  if [ "$clean_worktree" -eq 1 ]; then
    bash "$LOOP_DIR/worktree.sh" remove || true
  elif [ -n "${worktree:-}" ] && [ -d "$worktree" ]; then
    if [ "$reset_worktree" -eq 0 ]; then
      bash "$LOOP_DIR/worktree.sh" strip-scratch || true
    fi
    loop_emit worktree-kept worktree="$worktree"
  fi
  # The branches the loop made are the run's leftovers too, and the next reader should not have to run a command
  # to learn which are dead refs (issue #239): the run sweeps the ones whose patches the base already holds. It
  # runs after the worktree step, so `--clean-worktree` also releases the branch that tree held, and it is skipped
  # for a dry run and for a run that ended before the pre-screen (a budget of zero items writes nothing, so it
  # sweeps nothing either). A branch that still carries work, a branch a worktree holds, and a branch whose change
  # a reviewer refused are the sweep's refusals, not its work.
  if [ "$dry_run" -eq 0 ] && [ -n "${stage_dir:-}" ]; then
    bash "$LOOP_DIR/worktree.sh" prune-landed --apply || true
  fi
}


# ── the refusals that must happen before anything exists ────────────────────────────────────────

# 1. the identity: an unauthorised run stops before creating anything (spec, "an unauthorised identity
# stops the run before any work; no branch, label or state is written" — the state it just wrote is the
# run's own record, and no branch, worktree or label is created past this point).
preflight_out=$(loop_issue_tool policy.sh preflight 2>&1)
preflight_status=$?
if [ "$preflight_status" -eq 3 ]; then
  loop_emit stop-unauthorised detail="$preflight_out"
  exit "$LOOP_STOP"
fi
if [ "$preflight_status" -ne 0 ]; then
  relay --stderr "$preflight_out"
  loop_die "run: the preflight could not be evaluated (exit $preflight_status)"
fi

# 2. the lock: one run per repository, and it is taken **before** this run writes anything. The state it
# would replace, the queue the pre-screen writes into it and the branch the adoption path merges are the
# live run's, and a run that is refused after any of them has already done the damage (measured 2026-10-22:
# the run that started at 19:34 replaced the state of the run that started at 18:46, paid for a pre-screen,
# merged that run's unfinished item into main and closed it, and was refused at the lock two minutes into a
# 240-minute budget). A lock whose owner is not running any more is taken over and the takeover is
# announced, so a run that was killed leaves no tombstone that refuses every run after it.
loop_state lock --owner "$$" || exit $?

in_progress=""
interrupted=0
# The signal the run took, named by the trap and written into the run's own stop (issue #755). Empty until one arrives.
signal_taken=""
# The process group of the child that is running right now, and nothing else: `run_child` sets it under `set -m`
# and clears it once it has waited, so the traps below can end the child (and everything the child started) when
# the run takes a signal — the path a person's `kill`, or an operator's Ctrl-C, takes — while it is waiting for
# that child (issue #213).
active_child=""
# The heartbeat subshell the running `run_child` started, and the one it must end when the run takes a signal
# (issue #308): the `$stop` file the heartbeat loops on is only created on the path where the wait returns, so a
# signalled run used to leave it polling its tree for ever — measured 2026-10-25, an orphan still printing
# `running 56m00s` long after its run had exited.
active_hb=""
# The patch the leftovers of an ended child were written to, set by `keep_leftovers` and named in the transcript a
# reader of the blocked item is handed.
leftover_patch=""
# The gate that ran on the work a cut keeps, and the log its result was read from (issue #885): set by
# `gate_the_kept_work` and recorded beside the item's `cut`, so the next run resumes onto a measured tree instead of
# re-deriving one. Empty on every cut that had no work to gate, which is what the record's own null says.
cut_gate_result=""
cut_gate_log=""
# Whether the run's own deadline has ended a child (issue #674). It is the run's budget that ran out rather than a
# finding about the item, so the run takes no further pass over the queue: a second pre-screen after it excluded the
# item whose branch the cut run had just made, and the report named a decision the clock — not the item — had asked.
deadline_cut=0
# …and whether the run's own bound ended a **review round** of an item it worked (issue #785), which has the same
# consequence for the same reason: the round was cut rather than refused, so the item is left for the next run, and
# the pass the emptied queue would buy would read the branch the cut child left as a decision about that item. The
# measured shape is #780, whose tail wrote `LOOP-VERDICT: closed`, whose gate was `tests=460 failures=0` and whose
# round 1 ended at the turn bound: the run then excluded the item and its report asked its owner a question nobody
# had put to it.
bound_cut=0
# The one ignored directory `keep_leftovers` copies into that patch instead of only naming it (issue #308). Empty
# means no ignored path is copied; the profile's key is the project fact, the engine keeps none of its own.
record_dir="${LOOP_UNTRACKED_RECORD_DIR:-}"
record_dir="${record_dir%/}"



on_exit() {
  local rc=$?
  trap - EXIT
  # The child first: a run ended by a signal must not leave the item's agent working in the tree while the run
  # writes the item's outcome. `loop_end_group` sends `TERM`, so the child's own cleanup runs, and waits for it.
  loop_end_group "$active_child"
  active_child=""
  # …and the heartbeat beside it (issue #308). It is a job of this shell and not a member of the child's group, so
  # the group signal above never reached it, and the `$stop` file it loops on is only created on the path where the
  # wait returns — which a signal never takes.
  loop_end_pid "$active_hb"
  active_hb=""
  if [ "$interrupted" -eq 1 ]; then
    # The run's own end, written before the lock comes off (issue #755): the item's block below says what became of the
    # item, and `run.stop` is what a report read after this process is gone prints instead of "it recorded no reason".
    # It is outside the item test because the write does not depend on an item being in flight, and the reason names
    # the signal rather than its status, which is `128+N` and a table a reader would have to decode.
    loop_state stop "interrupted by SIG${signal_taken}" >&2 || true
    if [ -n "$in_progress" ]; then
      # A killed run must leave no item looking in progress: the label comes off and the item is recorded
      # blocked, so the next run's pre-screen can take it up again (D7).
      loop_labels unpick "$in_progress" >&2 || true
      loop_state done "$in_progress" --result blocked --note "interrupted by a signal" >&2 || true
    fi
  fi
  loop_state unlock --owner "$$" >&2 || true
  exit "$rc"
}

# The trap keeps the signal's own name, so the record above can name it (issue #755). `HUP` is trapped beside `INT` and
# `TERM`: a terminal or a harness that goes away sends it, and a signal the run does not trap kills the shell before any
# trap runs — the item kept its in-progress label, the state recorded no stop, and the next pre-screen excluded the item.
# A signal that arrives while `on_exit` is running returns instead of re-entering it: the trap writes the record last, and
# a second `exit` would abort it before the stop, the unpick and the unlock (a terminal teardown can send `HUP`, then
# `TERM`). `QUIT`, `USR1`, `USR2` and `ALRM` stay untrapped — each is either a run a person ends on purpose or a clock the
# host sends, and the gap this change closes is the one a vanished terminal leaves.
on_signal() { [ "$interrupted" -eq 1 ] && return 0; interrupted=1; signal_taken="$2"; exit "$1"; }
trap on_exit EXIT
trap 'on_signal 129 HUP' HUP
trap 'on_signal 130 INT' INT
trap 'on_signal 143 TERM' TERM

# 3. the state: this run's own record, written now that this run is the only one that can write it. The
# process that writes it is named in it, so a reader can tell a run that is going from one that was killed.
init_args=(--items "$items" --minutes "$minutes" --review "$review" --may-close "$may_close" \
  --max-decisions "$max_decisions" --max-review-rounds "$max_review_rounds")
if [ -n "$base" ]; then init_args+=(--base "$base"); fi
init_args+=(--owner-pid "$$")
loop_state init "${init_args[@]}" || exit $?
base=$(loop_state get run.base)

# Why a run ended is the run's own number, not a line on a console that scrolls away: every path that leaves
# the run records its reason, so `report.sh` prints the end the state file holds (#184). Defined before the first
# refusal that ends a run *before* it works an item — the checkout guard below — because a function is a command
# only once its definition has executed.
stop_run() { # <reason> — printed and recorded, then the caller breaks or exits
  loop_emit run-end reason="$1"
  loop_state stop "$1" >/dev/null 2>&1 || true
}



# 4. the checkout. The landing step merges into the project's **own tree**, where the base branch is checked out:
#    `worktree.sh`'s `merge-item` and `adopt-item` both `git merge` there and neither verifies it, and `adopt-item`
#    runs from the pre-screen, before the run's worktree exists. So a tree on any other branch — or on a detached
#    HEAD — cannot land what the run spends items on, and the run is stopped here: before the pre-screen, before
#    any adoption, before every item. The alternative is what was measured on the state #301 was filed on — a
#    refusal at the merge, after an item's whole budget — or, when the checkout merely fast-forwards to the item's
#    branch, a closure claiming a fix that never reached the base (issue #301's review, 2026-10-24).
checkout_ref=$(git -C "$LOOP_ROOT" symbolic-ref --quiet --short HEAD 2>/dev/null) || checkout_ref=""
if [ "$checkout_ref" != "$(loop_base_ref)" ]; then
  loop_emit refuse-tree-on-branch ref="${checkout_ref:-a detached HEAD}"
  loop_emit refuse-switch-to-base base="$(loop_base_ref)"
  loop_emit refuse-switch-command root="$LOOP_ROOT" base="$(loop_base_ref)"
  stop_run "the project tree is checked out on ${checkout_ref:-a detached HEAD}, not the base branch $(loop_base_ref) — the landing step merges into this tree, so switch to it and re-run"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
fi

# 4b. the base-red measurement is the gate's, not the run's (issue #944). The run used to take one pre-flight here, over
#     the profile's whole step set, before it worked anything (issue #921): measured 2026-11-16 on `gate.sh --base-red`
#     that cost 1 772 s of step time, of which 1 540 s were five steps and 1 251 s were steps no item of the night would
#     consult — 29 minutes of a 600-minute budget spent before the first item, and invisible in every item's wall
#     because it was no item's step. A step's base state is now measured by the first gate that needs it, one step at a
#     time, inside the item whose gate went red at it, and cached in `run.baseRed` for the rest of the run
#     (`gate.sh`'s `measure_step_base`). Nothing is measured here, and this block only says which mode the run is in.
case "$(loop_conf_value LOOP_BASE_RED)" in
  off|no|0)
    loop_emit base-red-off
    ;;
  *)
    loop_emit base-red-measured
    ;;
esac

# 5. the kept worktree. Keeping it is deliberate (the next run inherits its warm checkout, D4) and refusing a
#    previous run's leftovers is the right rule — but the refusal may not end a run with an exit code and no
#    item: it names the item whose branch the kept tree carries, records the refusal under that item,
#    escalates it and prints the report, so the "a run that ends with nothing processed and no reason"
#    outcome stays impossible on the path where it is most likely — a killed or crashed run (issue #173).
#    The check is one `git status` in a directory the run already knows: it needs neither the state nor the
#    lock, so it happens here, before the decision cap is counted and long before the pre-screen is paid for.
#    A worktree a *live* run is working in is not leftovers: a live run holds the lock, and this run was
#    refused at that lock before it got here — so a tree that is not clean now is a previous run's.
worktree="$LOOP_WORKTREE"
# The items this run ended *itself*, whose work `keep_leftovers` wrote to the item's own patch (issue #308). It is
# the one distinction the mid-run start refusal cannot read from the tree: dirt this run ended and recorded may be
# discarded to start the next item, because what the refusal protects — a record beside the tree — already exists
# there (issue #664). The patch's path is derived, never stored: `lib.sh` owns that one derivation, because the
# writer, the reader and the start that applies it must agree about the file or one of them lies (issues #664, #754).
own_leftovers=" "
if [ -d "$worktree" ] && worktree_is_dirty; then
  leftovers=$(git -C "$worktree" status --porcelain)
  # The item the leftovers belong to: `start-item` writes the item's branch into the worktree's own branch
  # marker, so a tree a killed run left behind still names the item it was working on.
  marker=$(cat "$worktree/.pi/loop-branch" 2>/dev/null || true)
  owner_item=$(owner_of_marker "$marker")
  if [ "$reset_worktree" -eq 1 ] && [ "$dry_run" -eq 0 ]; then
    loop_emit reset-discarding worktree="$worktree"
    if ! discard_kept_worktree reset-worktree "$leftovers"; then
      # The repair's refusal ends the run too, and a run whose end is not recorded is the shape of a crash once
      # this process is gone: the report reads `run.stop`, so the refusal is written as the run's own (#787).
      # The reason names the paths the repair printed — the status it left behind, not the one the attempt started
      # from: a file `reset --hard` did discard is not work a reader still owes, and naming it would put back the
      # false claim this change exists to end. The console scrolls away, and the reason is what a later report
      # prints (`Backlog.recipes.md` §7d).
      remaining=$(git -C "$worktree" status --porcelain)
      remaining_reason=""
      [ -z "$remaining" ] ||
        remaining_reason=" — $(printf '%s' "$remaining" | tr '\n' ';' | sed 's/;$//; s/;/; /g')"
      stop_run "the kept worktree is still not clean after --reset-worktree${remaining_reason} — nothing was started; the paths it printed are removed by hand"
      bash "$LOOP_DIR/report.sh"
      exit "$LOOP_REFUSE"
    fi
  elif [ "$dry_run" -eq 0 ] && submodule_only_dirt "$leftovers" && place_submodule_pin; then
    # The module on another pin is not work, so the repair above is not owed to it and no flag is needed to take
    # it: the pin the base records is placed and the run goes on, instead of ending under an item nobody owns.
    loop_emit kept-worktree-no-work
  else
    loop_emit kept-worktree-dirty worktree="$worktree"
    loop_indent "$leftovers"
    if [ -n "$owner_item" ]; then
      loop_emit leftovers-owner issue="$owner_item" branch="$marker"
    else
      loop_emit leftovers-owner-unknown marker="${marker:-an unknown branch}"
    fi
    loop_emit leftovers-repair
    if [ "$dry_run" -eq 0 ] && [ -n "$owner_item" ]; then
      # The label is recorded with the item only if the escalation actually wrote it, so the report and the
      # tracker cannot disagree (issue #177).
      escalated_label=""
      if loop_labels escalate "$owner_item" \
        --reason "a previous run left work behind in the kept worktree" \
        --evidence "branch ${marker:-unknown}, worktree $worktree" >&2; then
        escalated_label="$decision_label"
      fi
      loop_state done "$owner_item" --result blocked --verdict blocked \
        --note "the kept worktree was not clean — a previous run left work behind" \
        --decision-label "$escalated_label" >/dev/null
    fi
    # The refusal is the run's end as well as the item's: the report reads `run.stop`, and an empty one prints a
    # killed process rather than this refusal once the run that wrote it is gone (#787). The reason names the files
    # the refusal printed, as the cap arm names its list — the console scrolls away, and the reason is what a later
    # report prints (`Backlog.recipes.md` §7d).
    leftover_reason=""
    [ -z "$leftovers" ] ||
      leftover_reason=" — $(printf '%s' "$leftovers" | tr '\n' ';' | sed 's/;$//; s/;/; /g')"
    stop_run "the kept worktree is not clean — a previous run left work behind${leftover_reason}${owner_item:+, and the leftovers belong to #$owner_item} (repair: --reset-worktree discards them, and names them before it does)"
    bash "$LOOP_DIR/report.sh"
    exit "$LOOP_REFUSE"
  fi
fi

# 5b. the module's pin, before the pre-screen is paid for. A base whose gitlink names a commit no clone this host
#     can read holds makes every start on it refuse as dirt nothing can place — measured 2026-10-28 as a whole run:
#     the pin was two unpublished commits ahead of the module's remote, and 69 seconds in, with 600 minutes left,
#     the run stopped under no item. `worktree.sh` owns the rule and the repair it names; this step only pays it at
#     the start rather than after the queue is built.
pin_out=$(bash "$LOOP_DIR/worktree.sh" check-module-pin "$base" 2>&1) || {
  relay "$pin_out"
  stop_run "the base records a submodule pin no clone this host holds: $(printf '%s' "$pin_out" | head -1)"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
}
[ -z "$pin_out" ] || relay "$pin_out"

# 5c. the project tree's uncommitted work, read once the arm above has had its say: a base whose gitlink names a
#     commit no clone holds puts the module's path in this tree's status, and that is the pin arm's refusal — with the
#     repair it names — not dirt the run may only report. The landing step merges into this tree and both of its
#     merges refuse dirt there (`worktree.sh`'s `merge-item` and `adopt-item`), so that state had exactly one reader
#     and it ran after the item's whole budget and its gate: measured over this loop's archive (issue #798), four items
#     were recorded `unmergeable` for it (#668, #675, #769, #782), and #782's two files cost one run and two items
#     before any refusal was read. It is read here, before the decision cap is counted — and therefore before the
#     pre-screen is paid for and before any adoption the pre-screen would attempt. Nothing is repaired: the kept
#     worktree's dirt has `--reset-worktree` because the run wrote it, while this tree's dirt may be an edit its owner
#     made while no run was live, and no reading available here tells the two apart — the run's own leftovers are its
#     items' patches, which git ignores. So the refusal names the paths and discards nothing.
project_dirt=$(git -C "$LOOP_ROOT" status --porcelain 2>/dev/null)
# …less the run's own state directory, which this process wrote seconds ago: it locked and initialised the state above,
# so a project that does not ignore `.pi/` would have every run refuse itself. The directory is the run's, not the
# owner's, and the two are told apart without a reading: this process created it (issue #798).
state_rel="${LOOP_STATE_FILE#"$LOOP_ROOT"/}"
state_dir_rel=$(dirname "$state_rel")
project_dirt=$(printf '%s\n' "$project_dirt" | awk -v sd="$state_dir_rel" 'NF {
  p = $0; sub(/^.../, "", p); sub(/\/$/, "", p)
  if (p == sd || index(p, sd "/") == 1 || index(sd, p "/") == 1) next
  print }')
if [ -n "$project_dirt" ]; then
  loop_emit project-dirty
  loop_indent "$project_dirt"
  loop_emit project-dirty-cannot-tell
  loop_emit project-dirty-list
  loop_emit project-dirty-command root="$LOOP_ROOT"
  project_dirt_paths=$(printf '%s' "$project_dirt" | sed 's/^...//' | tr '\n' ' ')
  stop_run "the project tree carries uncommitted work the landing step would merge over: ${project_dirt_paths% } — the run cannot tell its own leftover from an edit made while it was not running, so nothing is discarded; commit or discard them and re-run"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
fi

# 6. the decision cap: decisions pile up across runs, so a per-run count cannot see them (D9).
#    The cap counts the *candidate* decisions — the items that carry both the decision label and the loop's
#    opt-in — because that is the population the pre-screen offers and the only one a person can answer: an item
#    whose owner removed the authorisation label is parked, not a question anyone may settle, so it must not spend a
#    slot of the cap that stops work on the items they can (#745). Measured 2026-11-02 on the live tracker: 13
#    labelled, 9 of them candidates, against a cap of 10 — the run refused with a free slot in the count it can
#    answer.
open_decisions=$(loop_issue_tool list.sh --json --limit 400 --eligible-only 2>/dev/null |
  jq -r --arg label "$decision_label" '[.[] | select([.labels[].name] | index($label))] | length')
if [ "${open_decisions:-0}" -ge "$max_decisions" ]; then
  # The refusal names both counts, so a reader can tell the 9 questions the cap compared from the 13 items that
  # merely carry the label. Read here rather than above: on the path a run normally takes it costs no read.
  labelled_decisions=$(loop_issue_tool list.sh --json --limit 400 2>/dev/null |
    jq -r --arg label "$decision_label" '[.[] | select([.labels[].name] | index($label))] | length')
  # The list is read once and used twice: printed for the console the caller watches, and carried in the reason,
  # because a refusal is the run's own record and the console scrolls away (issue #787). Both counts and the list
  # come from reads this arm already paid for.
  decision_list=$(loop_issue_tool list.sh --json --limit 400 --eligible-only 2>/dev/null |
    jq -r --arg label "$decision_label" \
      '.[] | select([.labels[].name] | index($label)) | "#\(.number) \(.title)"')
  loop_emit decisions-cap open="$open_decisions" labelled="${labelled_decisions:-$open_decisions}" max="$max_decisions"
  [ -z "$decision_list" ] || loop_indent "$decision_list"
  loop_emit decisions-accumulate
  # A refusal that leaves without recording itself is the shape of a crash once this process is gone: `report.sh`
  # reads `run.stop`, and an empty one prints "it was killed and recorded no stop" — the arm #227 repaired on the
  # pre-screen's path (#787).
  decision_reason=""
  [ -z "$decision_list" ] ||
    decision_reason=" — $(printf '%s' "$decision_list" | tr '\n' ';' | sed 's/;$//; s/;/; /g')"
  stop_run "the decision cap refuses the start: $open_decisions of the ${labelled_decisions:-$open_decisions} item(s) that await a decision are candidates, and the cap is $max_decisions$decision_reason"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
fi

# 7. the queue. Nothing eligible is an explicit non-error: a stopped clock is not a failed loop (D5).
if [ "$items" -eq 0 ]; then
  loop_emit nothing-eligible-empty-budget
  end_run
  exit "$LOOP_OK"
fi

# A dry run writes no label (D6): the pre-screen's refusal labels are skipped on it, so "what would
# happen" stays a statement about the run and not about the tracker.
prescreen_args=()
[ "$dry_run" -eq 0 ] || prescreen_args+=(--dry-run)
stage_dir=$(dirname "$LOOP_STATE_FILE")
prescreen_log="$stage_dir/prescreen.log"
progress pre-screen "started — its own output follows as it is written, its text is kept in $prescreen_log"
# Streamed, not captured and replayed (issue #191): the text reaches the terminal while the call runs, and the
# same bytes are kept for a path that has to read them afterwards instead of watching. `pipefail` inside the
# subshell makes the pipeline's status the pre-screen's own, so the verdict below reads what it always read.
prescreen_status=0
(
  set -o pipefail
  bash "$LOOP_DIR/prescreen.sh" "${prescreen_args[@]}" 2>&1 | tee "$prescreen_log"
) || prescreen_status=$?
progress pre-screen "finished (exit $prescreen_status) — the run decides on it next"
# A previous attempt's branch is decided by what it carries *before* it is escalated as a decision: a finished
# attempt is merged and its item closed, and only what is left is the owner's.
adopt_finished_exclusions
# The adoption ran in the loop above, so the base it recorded in the state file is the *new* one: the run's own
# base variable is read here again, because a merge that landed before the worktree exists moves the base every
# later item descends from — a stale base would put the merged change into the next item's diff.
base=$(loop_state get run.base)
# The pre-screen's own decision verdicts (a previous attempt's branch) reach the tracker here, so the
# decision is visible before any item is picked — including when the run then finds nothing else to work.
escalate_prescreen_decisions
case "$prescreen_status" in
  0) ;;
  3)
    loop_emit nothing-eligible-queue
    end_run
    exit "$LOOP_OK"
    ;;
  *)
    # A pre-screen that could not reach a verdict ends the run, and the run says why. Without this arm's reason the
    # exit was bare: the lock came off through the EXIT trap while `run.stop` stayed empty, so the next reader was
    # told the process "is gone — it recorded no reason", which is the shape of a crash rather than of a refusal
    # (measured 2026-10-27: a run died 30 s in at exit 2 and the state blamed nobody).
    stop_run "the pre-screen ended with exit $prescreen_status before an item was picked — no work was started"
    exit "$prescreen_status"
    ;;
esac

if [ "$dry_run" -eq 1 ]; then
  loop_emit dry-run-head
  jq -r '.queue[] | "  rank \(.rank)\(if .pinned then " pinned" else "" end) · #\(.issue) [\(.class)/\(.category)] \(.title)"' "$LOOP_STATE_FILE"
  jq -r '.excluded[] | "  excluded #\(.issue) \(.verdict): \(.reason)"' "$LOOP_STATE_FILE"
  bash "$LOOP_DIR/report.sh"
  loop_emit dry-run-end
  exit "$LOOP_OK"
fi

# ── the working tree, once per run ──────────────────────────────────────────────────────────────

worktree="$LOOP_WORKTREE"
branch="loop-$(date -u +%Y%m%d-%H%M%S)"
bash "$LOOP_DIR/worktree.sh" create --branch "$branch" || exit $?
if [ -f "$worktree/.pi/loop-env.sh" ]; then
  # shellcheck source=/dev/null
  source "$worktree/.pi/loop-env.sh"
  export VCPKG_ROOT
fi
loop_emit worktree-started worktree="$worktree" branch="$branch" base="${base:0:12}"
progress worktree "ready — $(basename "$worktree") on $branch from ${base:0:12}"















# …and the base it recorded, so a child that commits on the project's own branch without touching a file is
# still a change of the tree the run promised to build on. `project_state` is read before and after every child,
# and what the two readings differ by is recorded (below) — a difference is evidence, not a verdict about the one
# child the run knows nothing about (#274, #276, #285).














# ── the round's input, and the bound on it (issue #924) ───────────────────────────────────────────────
#
# The reviewer's input set had no ceiling, and the round paid for it by paging: measured over the eight rounds of
# the run the item records, the reviewer spent 46.1 M tokens — 22.5 % of the run's 204.7 M — 5.8 M a round for the
# six that reported and 8.9 M for #883, at about 92 k tokens a turn, and two rounds ended with no verdict at all,
# one at `LOOP_ITEM_CONTEXT_MAX` (#883, 250 418) and one at `LOOP_CHILD_TURNS_MAX_REVIEWER` (#898). A turn bound
# decides when to stop reading an unbounded input, so what is bounded here is the input itself:
# `LOOP_REVIEW_INPUT_MAX_BYTES` caps each of the three parts the round is handed, and the turn bound is not touched.
#
#   the diff                  the file `{{DIFF_PATH}}` names — what a round writes with `write_review_diff`
#   the paths before the gate  the round before the gate's report, the file a finding's path is named in
#   the change's own files    an inventory of them, path beside size — what paging an evidence tree is reading
#
# A part **under** the cap is handed exactly as it was before this bound existed, and a round whose three parts are
# all under it calls none of this: no file is written and its prompt carries no line about a bound, so such a round
# is byte for byte the one this key found. A part **over** it is handed as its head, at the cap, cut at a line
# boundary and marked `[LOOP-CUT: …]`; everything the cut left out is named — by path and line range — in
# `<round>.cut.md`, which the round's prompt names (`prompts/reviewer.md`, `{{REVIEW_INPUTS}}`) so a finding can
# still point at a line the round was not handed, and the scale of the cut travels into the prompt and into the
# item's state (`state.sh done --review-input`), where `report.sh` prints it.
review_input_cut_scale=""






# ── the round a bound ended is restarted in this run (issue #918) ───────────────────────────────────────────────
#
# A round the run's own bound ended is `cut`, and the item is left for the next run — right about the item, and wrong
# about the round. Measured on the run item #918 cites: #861's round ended at `LOOP_CHILD_TURNS_MAX_REVIEWER=110`
# turns with **no refusal in any of the 110**, so what the bound ended was an unfinished reading and not an
# objection, and the round had taken 21.08 M of that run's 65.21 M cache-read (32.3 %) and 70 m 26 s of its 209 item
# minutes (33.7 %) on a diff its own worker had spent 329 turns and 461 calls making. `review-1.diff` is there
# afterwards and no report is.
#
# So the run restarts the **reading**: one restart per round (`LOOP_REVIEW_RESTART_MAX`), in a fresh session on the
# input `LOOP_REVIEW_INPUT_MAX_BYTES` already bounded, with what the cut round had written handed to it. That is the
# half #851 already buys for the item's own worker at the context bound — a new session re-reads a bounded input
# instead of the conversation the ended one had paged — and it is why the restart is not simply "the same round,
# longer": a turn bound raised to cover a reading that never converged pays for the same pages twice.
#
# The item's other arm — deriving the bound from the diff the round must read — is recorded in `loop.conf` beside the
# key and not taken here: a derivation needs a measured reading rate per turn, which this archive does not carry,
# and the input cap above already fixes what there is to read.

# How many restarts one round may take, read once for the run: `0` or an unrecorded key is the pre-#918 shape.
review_restart_max=$(loop_conf_value LOOP_REVIEW_RESTART_MAX)
case "$review_restart_max" in ''|*[!0-9]*) review_restart_max=0 ;; esac



# What the restart did, in one line for the item%ss record (`state.sh done --review-restart`): the round, the bound
# that ended it and the file it had written, because a round that answered after a restart must not read in the
# record as a round that answered first time.
review_restart_line=""





# A child's own stream is silent by construction: the adapter the configured agent command names keeps the
# child's event stream for the report (`LOOP_CHILD_EVENTS`) and prints its answer once it has exited (measured
# 2026-10-19: on a pipe nothing appears until then), so a run used to print `pick #N` and then nothing for the
# whole item — a working child and a hung one looked the same. The wait therefore reports itself: one line when
# the child starts, one every `LOOP_PROGRESS_SECONDS` (default 30), and one when it ends. What those lines carry
# is read from the tree the child writes — its log's size, the worktree's changed files, its commits since the
# base — never from the child's own claim, which only exists once it is over. `fmt_elapsed` and the `progress`
# line the run's own phases use live in `lib.sh`, so a phase line and a child's line agree on what an elapsed
# time looks like (issue #191).










# ── the retry budget (issue #752) ───────────────────────────────────────────────────────────────
#
# A failure whose cause lies outside the item — an adapter that refuses the child's argv, a provider that refuses
# every request, a lock, a transient refusal — is not a verdict about the item, and the run used to read it as one
# about the window: it ended where it stood. The budget is a recorded value beside the clocks (`LOOP_RETRY_MAX`,
# `LOOP_RETRY_DELAYS`), one per transient **class** per run, and it is spent only by a class the run recognises in
# the item's own evidence. The cap is the point and not a concession: a deterministic refusal must not be re-asked
# forever, so a class that fails the cap is an answer about the run. `state.sh`'s own stops stay final — they are
# verdicts about the run's budget and not failures — and this budget never spends itself on them.
#
# The class this tree spends the budget on is the child that never started — a child that wrote no verdict line and
# left the item's tree at the run's base commit, whether its status was 1 (a refused argv) or 0 (a refused provider).
# A retry re-runs the item from the queue, because what failed is the item's whole phase pipeline: the prompt is
# rendered again and the branch is reused, and the item's record is untouched by the failed attempt (nothing was
# read), so the retry has the same work package the first attempt had. The delay before each retry is read from
# `LOOP_RETRY_DELAYS` by position, and the run names each attempt and the keys it read in the line it prints and in
# the stop reason it leaves behind.
retry_max=$(loop_conf_value LOOP_RETRY_MAX)
case "$retry_max" in ''|*[!0-9]*) retry_max=3 ;; esac
retry_delays=$(loop_conf_value LOOP_RETRY_DELAYS)
[ -n "$(printf '%s' "$retry_delays" | tr -d '[:space:]')" ] || retry_delays="30 120 300"
# One budget per class, this run's. The class `child-start` is the only transient class the run spends today; a
# second class adds a counter beside this one and reads it in its own stop arm, never a shared count that would let
# one class spend another's budget.
retry_spent_child_start=0
retry_log_child_start=""
# The second class is the answer that is not an answer (issue #882): a final report of 0 bytes over a tree the item
# already moved. It spends the **same** recorded budget — one entry of `LOOP_RETRY_MAX`, the same delays read by
# position — and its own flag beside the counter bounds the class to one retry, so a second empty answer is the item's
# stop instead of a third child. It re-runs the *same* child with the *same* prompt in place, not the item from the
# queue: the branch carries the work the attempt kept, so a second pick would read that work as an unfinished attempt
# instead of asking it the question again. Measured 2026-11-14: a continuation child exited 0 with a 0-byte report,
# nothing spent the budget, the item was recorded `blocked` and 14.5 Mtok were lost.
retry_spent_answer_empty=0
retry_log_answer_empty=""

# ── the item loop ───────────────────────────────────────────────────────────────────────────────

queue_passes=1
while true; do
  if [ "$(jq -r '.queue | length' "$LOOP_STATE_FILE")" -eq 0 ] && [ "$queue_passes" -lt 2 ] &&
    [ "$deadline_cut" -eq 0 ] && [ "$bound_cut" -eq 0 ]; then
    # A queue that emptied mid-run gets one more pass, never a third (SKILL.md, "Pre-screen the backlog") — but not
    # after the run's own deadline ended a child, nor after its own bound ended a review round of the item it last
    # worked (issue #785): the pass can admit nothing the run could work either way, and its verdict on the item whose
    # branch the cut child had just made reads as a decision about that item.
    queue_passes=$((queue_passes + 1))
    bash "$LOOP_DIR/prescreen.sh" >&2 || true
    escalate_prescreen_decisions
  fi
  next_status=0
  next_out=$(loop_state next) || next_status=$?
  if [ "$next_status" -eq "$LOOP_HOLD" ]; then
    # `next` exits 6 when the reason is outside the run and a **wait** clears it — the provider's own quota, read
    # through `LOOP_QUOTA_CMD`. The run pauses rather than ends, because ending would spend the budget the run
    # still holds on a decision that says nothing about the next item — the shape of loss the refusal-streak
    # measurement already named (eight of twelve runs stopped on the pair, 55–90 % of their budget unused, measured
    # 2026-11-05/06; `state.sh`'s `LOOP_REFUSAL_STREAK_STOP`). The reader's own lines are kept verbatim: the reason is every line but the last, and the last names the
    # instant the wait clears (`resume-at <epoch>`, `0` when the reader knows no instant and the run polls).
    hold_reason=$(printf '%s\n' "$next_out" | grep -v '^resume-at ' | head -1)
    hold_at=$(printf '%s\n' "$next_out" | sed -n 's/^resume-at \([0-9][0-9]*\)$/\1/p' | tail -1)
    case "$hold_at" in
      ''|0) hold_seconds="$quota_poll" ;;
      *) hold_seconds=$(( hold_at - $(date +%s) ))
         [ "$hold_seconds" -gt 0 ] || hold_seconds=1 ;;
    esac
    if [ "$quota_hold_max" -gt 0 ] && [ "$hold_seconds" -gt "$quota_hold_max" ]; then
      stop_run "$hold_reason — the wait of ${hold_seconds}s is longer than the ${quota_hold_max}s a run takes (LOOP_QUOTA_HOLD_MAX_SECONDS), so the run ends instead of waiting for it"
      break
    fi
    # The pause is written **before** the sleep and it moves the deadline with it (`state.sh hold`): the budget is
    # what the run may spend on work, and sleeping is not work — without that a provider resetting in six hours
    # would be waited for past the run's own deadline and never resumed. Written first, so `state.sh show` reads
    # the truth for the whole wait instead of counting the pause as spent budget for its length.
    loop_state hold --seconds "$hold_seconds" --reason "$hold_reason" >/dev/null 2>&1 ||
      loop_note "run: the pause could not be recorded in the state — the deadline is not extended by it"
    loop_emit pause reason="$hold_reason" seconds="$hold_seconds"
    sleep "$hold_seconds"
    continue
  fi
  if [ "$next_status" -ne 0 ]; then
    # `next` names the stop condition on stdout and exits 3: it is the run's own verdict on why it may not
    # start another item, and the only account of the end besides the counters. Captured and dropped, the
    # printed report could not say why a run stopped (#184).
    stop_run "$next_out"
    break
  fi
  issue="$next_out"
  # A landing refusal is the record of the item it refused (issue #883), so an item starts holding none: the arm that
  # carries one clears it after its own `done`, and this is the other half of that promise — no later item can record
  # a landing the run never asked of it.
  merge_refused_branch=""
  merge_refused_paths=()
  # The host is read again for every item, because memory is what the previous item's own gate may have spent,
  # and it is read *before* the item's first child exists: below the recorded floor the item is recorded
  # `blocked` with the measurement and the number it was compared against, and the run stops. A run on a starved
  # host cannot become productive by taking the next item either — the gate that would have run there measures
  # the desktop session instead of the change (issue #209). Nothing was started, so nothing is escalated: this
  # is not a decision the item owes, it is a condition a person clears by freeing memory and re-running.
  free_mib=$(host_free_mib)
  if [ -n "$free_mib" ] && [ "$free_mib" -lt "$min_free_mib" ]; then
    shortfall="the host has ${free_mib} MiB available, below the recorded floor of ${min_free_mib} MiB (LOOP_MIN_FREE_MIB) — the item was not started"
    loop_state done "$issue" --result blocked --verdict blocked --note "$shortfall" >/dev/null
    loop_emit item-blocked issue="$issue" result=blocked reason="$shortfall"
    stop_run "$shortfall; free memory and re-run — a starved host cannot become productive by taking the next item"
    break
  fi
  # One item the tracker will not answer for is *unknown*: not a verdict about that item and not the end of the run.
  # The pre-screen has read that rule since #231 (a candidate it cannot read is recorded and the screen continues), and
  # this read — the one that happens once per item, asking for the comments a recorded decision arrives in — was the
  # last place that turned the same unknown into a `stop_run`: one unanswered read cost a run the budget it had left
  # (measured 2026-11-06, the 480-minute run that ended at 03:31Z with 108 minutes and 69 item slots unspent — the
  # record `.pi/issue-loop/runs/2026-11-06T21:19:06Z.json`: 480 min − 372 min elapsed = 108 min, 80 item slots − 11
  # recorded = 69). The read is the seam's own (`tools/issues/lib.sh`), so what reaches here is the failure the seam
  # handed back: a refusal it passes through without retrying, or a transport failure it retried and reported as
  # `tracker-unreachable`. The item is recorded with the word the screen writes for the same unknown and the queue
  # moves on: a tracker that answers nothing still ends the run, on the refusal streak `state.sh next` reads — the
  # count the engine is given (`LOOP_REFUSAL_STREAK_STOP`, `tools/loop/loop.conf:83`; 4 in this tree), not a pair
  # (issue #804).
  read_err=$(mktemp) || read_err=""
  if ! issue_json=$(loop_issue_json "$issue" --comments 2>"${read_err:-/dev/stderr}"); then
    read_error=$(tr '\n' ' ' < "${read_err:-/dev/null}" | sed -e 's/  */ /g' -e 's/ *$//')
    [ -z "$read_err" ] || rm -f "$read_err"
    loop_emit tracker-unreadable issue="$issue" error="${read_error:-the CLI gave no message}" --stderr
    loop_state done "$issue" --result blocked --verdict unreadable \
      --note "the tracker could not be read for this item, so nothing is decided about it — ${read_error:-the CLI gave no message}" >&2 || true
    continue
  fi
  [ -z "$read_err" ] || rm -f "$read_err"
  title=$(printf '%s' "$issue_json" | jq -r '.title // ""')
  # The item's class is a work input, not a label the worker guesses: a feature takes the propose-first path
  # while a bug and an improvement take the fix path (`tools/loop/prompts/worker.md`).
  class=$(printf '%s' "$issue_json" | jq -r '[.labels[]? | if type == "string" then . else .name end |
    select(startswith("class/")) | sub("^class/"; "")] | .[0] // ""')
  # The class that is planned before it is applied is the profile's value, not a word in the skeleton (D16).
  propose_class=$(loop_role proposeClass)
  slug=$(slugify "$title")
  [ -n "$slug" ] || slug="issue-$issue"
  # The change the item's body records (`finding-shape-at-filing`) is the plan a person wrote and gated: the run copies
  # it into the worktree and hands it over, so a tier-B item is never planned a second time. Empty is a tier-A item,
  # whose plan is the worker's own; nothing below reads the body for any other purpose.
  item_body=$(printf '%s' "$issue_json" | jq -r '.body // ""')
  item_change=$(issues_change_of "$item_body")
  changes_family=$(loop_conf_value LOOP_CHANGE_SOURCE_DIR)
  changes_family="${changes_family:-openspec/changes}"

  if ! start_out=$(bash "$LOOP_DIR/worktree.sh" start-item "$issue" "$slug" --change "${item_change:-}" 2>&1); then
    relay "$start_out"
    case "$start_out" in
      # The base moved: the base branch is not what this run recorded, so nothing may be branched off it. That is a
      # stop with a reason, not a silent exit — a run that ends without recording why makes its own report read
      # "ended without recording a reason" and its numbers unattributable (#184, measured 2026-10-20). The reason
      # repeats what `check_base` observed — both commits — and adds nothing it cannot know: it used to claim the
      # base had "advanced while this run was open", which was false for a run opened from a feature branch whose
      # `init` had recorded that branch's commit instead of the base branch's (issue #301).
      *"base moved"*) stop_run "the base moved: $(printf '%s' "$start_out" | head -1 | sed 's/^base moved: //')"; break ;;
      *"carries commits the base does not"*)
        # A previous attempt left a *finished* change on the item's branch — `start-item` has already set a
        # blocked attempt aside, so what reaches here carries one. What it carries decides: a finished change
        # with a green verification is merged and the item closed, and only a branch without that evidence is the
        # owner's — recorded and escalated rather than dropped, because an unrecorded refusal is how a run ends
        # with "nothing processed" and no reason (D7, D9, issue #232). The run then takes the next candidate.
        if adopt_finished_attempt "$issue" "fix/issue-$issue-$slug"; then
          in_progress=""
          continue
        fi
        escalated_label=""
        if loop_labels escalate "$issue" --reason "a previous attempt's branch carries work the base does not" \
          --evidence "branch fix/issue-$issue-$slug" >&2; then
          escalated_label="$decision_label"
        fi
        loop_state done "$issue" --result blocked --verdict blocked --note "$start_out" \
          --decision-label "$escalated_label" >/dev/null
        in_progress=""
        continue
        ;;
      *"not clean"*)
        # The dirt is the *previous* item's unfinished work, not this candidate's failure (#284). The run used to
        # record only the first line of the refusal and end, so the item whose work the tree held — already
        # recorded for another reason — was named nowhere, the leftovers were attributed nowhere, and the item
        # that could not start went back into the queue untouched (measured 2026-10-23: 166 minutes into an 8 h
        # budget, 7 eligible items unworked). The tree's own branch marker names the item, so the leftovers are
        # recorded there and escalated exactly as the start-time pre-flight records them (#173), and the stop
        # reason names the files instead of the refusal's first line (issue #284, item (b) and (c)).
        # The candidate is not recorded and not escalated: nothing was done to it, and it stands in the queue as
        # its owner set it. Continuing down the queue is not an alternative and is not taken: the tree stays
        # dirty, nothing is discarded without `--reset-worktree`, so every later candidate would be refused the
        # same way and one cause would leave a blocked item per candidate.
        # …unless the dirt is the run's *own* (issue #664). A child this run ended at its cap left it, and
        # `keep_leftovers` has already written it to that item's patch — so the record the refusal protects is in
        # place, and the tree is the one this run has to start its next item on. It is discarded and the start
        # retried once; that is the whole difference between the dirt of *another* item, which still stops the run,
        # and the dirt of the item the run itself just ended. Measured 2026-10-26: #310 was ended at its cap at
        # 08:08:45Z and its patch written at 08:08:38Z, the start of #309 was refused at 08:09:04Z, and the run
        # stopped having spent 166 of its 400 minutes — 234 minutes and the one queued item unspent.
        marker=$(cat "$worktree/.pi/loop-branch" 2>/dev/null || true)
        owner_item=$(owner_of_marker "$marker")
        leftovers=$(git -C "$worktree" status --porcelain 2>/dev/null)
        leftover_files=$(printf '%s\n' "$leftovers" | sed '/^$/d' | paste -sd, - | sed 's/,/, /g')
        start_after_discard=0
        retry_refusal=""
        if recorded_leftovers_of "$owner_item" && discard_kept_worktree discard "$leftovers"; then
          loop_emit leftover-discarded issue="$owner_item" patch="$(loop_leftover_patch_of "$owner_item")" starts="$issue"
          # The console scrolls away, so the fact is written where a later reader looks for it: the run's own steps. The
          # item it belongs to already carries its record and its label, which is why this is a step and not a second
          # `done` for the same item — one record per item, and the record was the old arm's too.
          loop_state step A --name "leftovers-discarded-$owner_item" --status ok \
            --note "the work #$owner_item left after this run recorded it is kept at $(loop_leftover_patch_of "$owner_item") and was discarded so #$issue could start" >/dev/null 2>&1 || true
          retry_start_item
        elif submodule_only_dirt "$leftovers" && place_submodule_pin; then
          # The module on another pin is not work: nothing inside it was written, so the pin the base records is placed
          # and the start is retried — the case a person met before as "the worktree is not clean" under no item.
          loop_emit gitlink-pin-placed issue="$issue"
          retry_start_item
        fi
        if [ "$start_after_discard" -eq 0 ]; then
          # The retry is a start of its own and can be refused for a reason that has nothing to do with the dirt it
          # was retried over: the base moved while the child ran, or the branch carries work the base does not. That
          # reason is the refusal a reader needs, so it is the one recorded — read from the retry's own output, and
          # only the dirt keeps the attribution below (issue #664, round 2: without this, a retried start refused for
          # another reason was reported as "the worktree is not clean" with an empty list of files, on a tree the run
          # had just cleaned itself).
          case "$start_out" in
            *"not clean"*) ;;
            *) stop_run "the item could not be started: ${retry_refusal:-$(printf '%s' "$start_out" | head -1)}"; break ;;
          esac
          # Read again now and not before the retry: a discarded tree's files are gone, and a stop reason that named
          # them would name work nobody can find (issue #664, round 2).
          leftovers=$(git -C "$worktree" status --porcelain 2>/dev/null)
          leftover_files=$(printf '%s\n' "$leftovers" | sed '/^$/d' | paste -sd, - | sed 's/,/, /g')
          if [ -n "$owner_item" ]; then
            leftover_owner="the leftovers are #$owner_item's (branch $marker)"
          else
            leftover_owner="the worktree is on ${marker:-an unknown branch}, which names no item"
          fi
          # …and the leftovers it found are written down before the run ends (issue #308): they are this item's, the
          # item is named by the branch marker, and the stop reason below names the file — so `--reset-worktree` is no
          # longer the only repair a person has, and the work is readable after it. An item whose work this run already
          # wrote is **not** written again (issue #664, round 2): the tree it would read is the one the repair above
          # just discarded or half-discarded, so the second write would replace a record of real work with a header
          # and a residual, and the record is the whole reason the dirt is discardable in the first place.
          own_leftovers_of "$owner_item" || keep_leftovers "$owner_item"
          if [ "$dry_run" -eq 0 ] && [ -n "$owner_item" ]; then
            escalated_label=""
            if loop_labels escalate "$owner_item" \
              --reason "a start was refused mid-run because the worktree is not clean — the leftovers are this item's" \
              --evidence "branch ${marker:-unknown}, worktree $worktree: $leftover_files" >&2; then
              escalated_label="$decision_label"
            fi
            loop_state done "$owner_item" --result blocked --verdict blocked \
              --note "the worktree was not clean when #$issue was started: the leftovers are #$owner_item's, and no item can start until they are dealt with — $leftover_files" \
              --decision-label "$escalated_label" >/dev/null
          fi
          stop_run "the item could not be started: the worktree is not clean — $leftover_owner, so #$issue and no item after it can start until they are dealt with: $leftover_files${leftover_patch:+ (the work is kept in $leftover_patch)} (repair: --reset-worktree discards them, and names them before it does)"
          break
        fi
        ;;
      *) stop_run "the item could not be started: $(printf '%s' "$start_out" | head -1)"; break ;;
    esac
  fi
  # A blocked attempt's branch is kept under a name of its own instead of being overwritten (issue #232), and
  # the run prints exactly that line: the rename is the only record of where those commits went, and the start's
  # captured output also holds git's own chatter, which the run does not repeat.
  case "$start_out" in
    *set-aside:*) printf '%s\n' "$start_out" | grep '^set-aside:' || true ;;
  esac
  loop_labels pick "$issue" || {
    loop_emit progress-mark-failed issue="$issue" --stderr
    stop_run "the in-progress label could not be written on #$issue"
    break
  }
  in_progress="$issue"
  # The state names the item from here on and not only a phase: `.current` is what `state.sh show` prints and
  # what `done` clears, so a person who opens the state of a killed run reads which item was in flight instead
  # of resolving the worktree's branch marker by hand (issue #263). Its `change` is the name the run asks the
  # worker for — the same slug the branch carries — because the change the worker files is not known until it
  # reports, and `done` records that one beside the item.
  change_hint="${item_change:-$(if [ "$class" = "$propose_class" ]; then printf add; else printf fix; fi)-$slug}"
  loop_state start "$issue" --change "$change_hint" >/dev/null || {
    loop_emit progress-record-failed issue="$issue" --stderr
    stop_run "the state could not name #$issue as the item in progress"
    break
  }

  item_dir="$stage_dir/items/$issue"
  mkdir -p "$item_dir"
  # The one place beside its change a child may write (issue #800): `run_child` starts every child in the project's
  # own tree, so a write with a project-relative path lands in the tree the landing step needs clean, and writing
  # beside the change was the only path a child had. It is created before the prompt that names it is rendered, so
  # the directory a child reads out of its prompt is one that exists.
  scratch_dir="$item_dir/scratch"
  mkdir -p "$scratch_dir"
  # What the run copied into the worktree, beside the item's other evidence: the source directory, the file count and
  # the bytes are the run's own record of the preparation, and `loop-clean.sh` reports the source from the state.
  if [ -n "$item_change" ] && [ -f "$LOOP_STATE_FILE" ]; then
    jq --arg i "$issue" '(.changeCopies // {})[$i] // empty' "$LOOP_STATE_FILE" > "$item_dir/change-copy.json" 2>/dev/null || true
  fi
  # The report a person can tail while the item is worked, named where the item starts: the child's own stream
  # arrives only when it exits, so that file is the view that exists in between (issue #191).
  progress item "picked #$issue — its report is written to $item_dir/worker-report.md"
  # The event file of this item's children. Its contents decide what the report can say an item cost, so the
  # path is the run's to name, inside the item's own directory, and it is removed here for the same reason the
  # per-child progress marker is: a run that starts the item again must not inherit a previous attempt's spend.
  # Nothing else about it is the run's — a child that writes no stream leaves no file, and the report says `-`.
  events_file="$item_dir/events.jsonl"
  rm -f "$events_file"
  export LOOP_CHILD_EVENTS="$events_file"
  # The ledger of what this item's children were handed, named beside the stream it is written with (the adapter puts it
  # there and hands every child the same path as `LOOP_TOOL_RESULT_LEDGER`): a continuation is a **new** session on the same
  # tree, so not one path its first process read is in its context, and this file is the only record of them the second
  # process can be pointed at — the prompt names it, so that child asks for a passage instead of re-reading what the item
  # already holds (issue #1084).
  item_ledger="$item_dir/tool-result-ledger.tsv"
  package="$item_dir/work-package.md"
  fingerprint=$(printf '%s' "$issue_json" | jq -r '.body // ""' | { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
  # The item's text reaches the child inside a delimited data field, never in an instruction position
  # (D10): the work package is a file, and the prompt names its path instead of inlining it.
  # The prepared change reaches the child as the third delimited block of its package, with the readiness verdict
  # read again for the copy in the worktree — the copy is what the child applies, so the copy is what is judged.
  change_path=""
  change_readiness=""
  if [ -n "$item_change" ]; then
    change_path="$worktree/$changes_family/$item_change"
    change_readiness=$(change_readiness_of "$change_path")
  fi
  printf '%s' "$issue_json" |
    issues_core WorkPackage --profile "$(loop_profile)" --issue - \
      --fingerprint "$fingerprint" --provenance "issue #$issue" \
      --change "${item_change:-}" --change-path "$change_path" --change-readiness "$change_readiness" > "$package" ||
    loop_die "run: could not render the work package for #$issue"

  # What the prompt does not carry, and why it is one file (issue #1082): the long form of the three blocks a worker
  # consults — the rule routing, what its shell refuses, and how a measurement is taken and calls batched — is
  # rendered as its own file beside the prompt, which names it, so that the prompt a turn re-reads is not the whole of
  # what the item must be told. It is rendered once per item, with the same pairs as the prompt it belongs to, so a
  # placeholder added there is filled here too; the round-2 prompt below names this same copy.
  render_prompt "$LOOP_DIR/prompts/worker-reference.md" "$item_dir/worker-reference.md" \
    "GATE_STEP=$(gate_step_text "$pre_gate")" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
    "SCRATCH_DIR=$scratch_dir" \
    "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
    "CLASS=$class" "PROPOSE_CLASS=$propose_class"
  render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt.md" \
    "GATE_STEP=$(gate_step_text "$pre_gate")" \
    "WORKER_REFERENCE=$item_dir/worker-reference.md" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
    "SCRATCH_DIR=$scratch_dir" \
    "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
    "CLASS=$class" "PROPOSE_CLASS=$propose_class"
  worker_prompt_bound_check "$item_dir/worker-prompt.md" worker

  started=$(date +%s%3N)
  # The report the item is read from: the worker's, or the continuation's when this run ended that worker at its context
  # bound and continued the item itself (issue #851). Everything after this line reads `$worker_report`/`$worker_exit`,
  # never the path, so the continuation needs no arm of its own below.
  worker_report="$item_dir/worker-report.md"
  worker_prompt="$item_dir/worker-prompt.md"
  # Whether the continuation this run started was ended at the same ceiling the worker it continued met, and what that
  # child listed when it was (issue #956): read where the continuation ends and decided by the outcome arms below.
  continuation_too_wide=0
  continuation_phases=""
  run_child "$item_dir/worker-prompt.md" "$worker_report"
  worker_exit=$?
  loop_state step A --name worker --status "$(if [ "$worker_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
    --note "exit $worker_exit" >/dev/null

  # ── the bound ended this worker: this run continues the item, it does not hand it forward (issue #851) ─────────────
  #
  # The bound is not the defect. `LOOP_ITEM_CONTEXT_MAX` stays 200 000 and the checkpoint sits at 90 000 (#1003), and a
  # child the run ended is never committed (#754). What cost the run of 2026-11-11T11:37:08Z 33.08 of its 75.45 Mtok
  # (44 %) was **when** the work was resumed: it cut #847 at 200 174 tokens after 142 turns and #839 at 200 869 after
  # 125, kept 1 010 460 B and 1 323 610 B of their work in their own patches, closed neither, and then stopped `queue
  # empty` 435 min before its own deadline with both patches in hand — and the next run applied neither (2 of 2, `run.resumes`
  # records `outcome: no-apply`), because the base had moved under them between the runs. A patch applied in the run that
  # cut it is a continuation, in the one tree it was taken from; the same patch one run later is a coin toss.
  #
  # The item's **session chain** is continued while the run's own deadline has not passed and the chain is below the
  # recorded cap (issue #1022). A continuation reaching the same crossing is the same item spending the same per-turn
  # cost: exactly one continuation bounded the first session of the chain and left every item's context in the band it
  # reached before the checkpoint existed. The cap is what keeps this from being a loop that never ends an item — a chain
  # at its cap is the item's `cut` — and a continuation started
  # after the deadline would be ended on its first second, replacing the bound's own reason with the clock's, which is the
  # distinction #829 and #785 paid for.
  continuations_done=0
  continuations_max=$(loop_conf_value LOOP_ITEM_CONTINUATIONS_MAX 2>/dev/null | head -1)
  case "$continuations_max" in ''|*[!0-9]*) continuations_max=3 ;; esac
  while [ "$worker_exit" -eq "$LOOP_CHILD_TIMEOUT" ] && child_ended_by_context_bound "$worker_report"; do
    [ "$continuations_done" -lt "$continuations_max" ] || break
    deadline_epoch=$(jq -r '.run.deadlineEpoch // 0' "$LOOP_STATE_FILE" 2>/dev/null)
    case "$deadline_epoch" in ''|*[!0-9]*) deadline_epoch=0 ;; esac
    if [ "$deadline_epoch" -gt 0 ] && [ "$(date +%s)" -ge "$deadline_epoch" ]; then
      progress note "#${issue} is not continued: the run's own deadline has passed, so a continuation would be ended on its first second and would replace the bound's reason with the clock's"
      break
    else
      # The patch this child left, read before `worktree.sh` runs: the step applies exactly that file, and the item's
      # note names it (the run's own record of the work, kept beside the tree as #754 leaves it — nothing is committed).
      continuation_patch="$leftover_patch"
      continuation_resume=0
      # Which reading ended the child decides the step a reader sees (issue #856): the stop's continuation is
      # `worker-continuation` (issue #851) and the checkpoint's is `worker-checkpoint`, so the two splits are told apart
      # in the ledger and the applier's sentence names the one that fired. The line read here is the one `run_child`
      # wrote, not a second reading of the stream.
      continuation_step=worker-continuation
      continuation_reason=bound
      case "$(report_field "$worker_report" LOOP-TIMEOUT 2>/dev/null)" in
        *"the recorded context checkpoint of"*) continuation_step=worker-checkpoint; continuation_reason=checkpoint ;;
      esac
      bash "$LOOP_DIR/worktree.sh" continue-item "$issue" "$continuation_reason" > "$item_dir/continuation-resume.log" 2>&1 || continuation_resume=$?
      case "$continuation_resume" in
        0)
          # What the patch holds, in the child's own terms: the files and the hunks are counted from the record, so a
          # continuation reads how much work stands in the tree instead of `git diff`-ing a tree a reviewer would then
          # have to trust.
          continuation_files=$(grep -c '^+++ ' "$continuation_patch" 2>/dev/null || printf 0)
          continuation_hunks=$(grep -c '^@@' "$continuation_patch" 2>/dev/null || printf 0)
          continuation_paths=$(sed -n 's#^+++ b/#  - #p' "$continuation_patch" 2>/dev/null | head -20)
          render_prompt "$LOOP_DIR/prompts/worker-continuation.md" "$item_dir/worker-prompt-continuation.md" \
            "GATE_STEP=$(gate_step_text "$pre_gate")" \
            "ISSUE=$issue" "CLASS=$class" "PROPOSE_CLASS=$propose_class" \
            "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
            "LEDGER=$item_ledger" "SCRATCH_DIR=$scratch_dir" "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" \
            "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
            "PATCH=$continuation_patch" "PATCH_FILES=$continuation_files" \
            "PATCH_HUNKS=$continuation_hunks" "PATCH_PATHS=$continuation_paths" \
            "BOUND_LINE=$(report_field "$worker_report" LOOP-TIMEOUT)"
          progress item "continuing #$issue from the work its ended worker left — $continuation_patch ($continuation_files file(s), $continuation_hunks hunk(s)) — continuation $(( continuations_done + 1 )) of $continuations_max, and its report is written to $item_dir/worker-continuation-report.md"
          run_child "$item_dir/worker-prompt-continuation.md" "$item_dir/worker-continuation-report.md"
          worker_exit=$?
          worker_report="$item_dir/worker-continuation-report.md"
          worker_prompt="$item_dir/worker-prompt-continuation.md"
          # …and a ceiling this child met is the item's shape (issue #956): the run has now measured the item in two
          # children — the worker it continued and this continuation — so what the item does
          # not fit is its own size and not the phase's progress: the outcome arms below record the shaping verdict
          # `too-wide` — the hold a person shapes the item from — instead of a `cut` the next run re-enters at full size,
          # and the reason names the ceiling and what this child listed. The test is the one the first cut is read from,
          # so no new vocabulary is introduced to name a context split.
          if [ "$worker_exit" -eq "$LOOP_CHILD_TIMEOUT" ] && child_ended_by_context_bound "$worker_report"; then
            continuation_too_wide=1
            continuation_phases=$(continuation_listing "$worker_report")
          fi
          loop_state step A --name "$continuation_step" \
            --status "$(if [ "$worker_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
            --note "exit $worker_exit · from $continuation_patch, applied onto $(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null | cut -c1-12)" >/dev/null
          # …and the chain stops on that reading (issue #956): the shape has been measured in two children, so a third
          # would spend the item's per-turn cost to learn what the first two already said. The session chain's own cap
          # (#1022) bounds a chain of *progress*; a ceiling is its own end and not a step of one.
          [ "$continuation_too_wide" -eq 1 ] && break
          ;;
        2)
          # The patch does not apply to the branch's HEAD: nothing is continued and nothing is discarded silently —
          # `continue-item` records the non-apply with the item, and the outcome below leaves it `cut` with that note.
          progress note "#${issue} is not continued: the work its ended worker left does not apply onto the branch's HEAD, so the item keeps the cut for the next run — see $item_dir/continuation-resume.log"
          break
          ;;
        *)
          progress note "#${issue} is not continued: no kept patch was found to apply — see $item_dir/continuation-resume.log"
          break
          ;;
      esac
    fi
    continuations_done=$(( continuations_done + 1 ))
  done

  # The gate ends the child that ran it, and a second fresh child carries the evidence (issue #744,
  # `tail_after_gate` above). Its archive is not that child's: the child **before** the gate completes the change and
  # archives it (issue #837), so the gate names a tree the round after it reads unchanged. A child the run ended, or
  # one that already answered for itself (a skip, a falsification), has no tail: there is nothing to record and nothing
  # to carry, and the run reads the item's report where it has always read it.
  #
  # The gate child's own difference in the project's tree is read here, before the tail overwrites the reading: it
  # is a fact about that child, and a tail child that then writes nothing would otherwise erase it (#285's note).
  worker_write_note="$outside_write_note"
  worker_write_attributed="$outside_write_attributed"
  worker_write_decides=0
  outside_write_decides && worker_write_decides=1
  # A write the run can place decides the item **where it is read** (issue #800): recorded here, beside the reading
  # that took it and before the pre-gate round or a gate child is started, because an item whose change stands in a
  # tree the run cannot vouch for is not one a gate result would be evidence about. The reading is the worker's own;
  # the gate child's and the tail child's are recorded under names of their own further down.
  if [ -n "$worker_write_note" ]; then
    if [ "$worker_write_decides" -eq 1 ]; then
      write_step_note="the item ends at this reading, before any gate child: $worker_write_note"
    else
      write_step_note="$worker_write_note"
    fi
    # The step carries the attribution the outcome block's reason already carries, because the requirement this
    # reading owes names the child the process was placed in and the step is where a reader looks it up: the pids
    # are the reading's, taken while the child ran (issue #800, review round 1).
    if [ -n "$worker_write_attributed" ]; then
      write_step_note="$write_step_note (attributed to this child: $worker_write_attributed)"
    fi
    loop_state step A --name outside-write --status ok --note "$write_step_note" >/dev/null 2>&1 || true
    progress note "$write_step_note"
  fi

  # ── a change child that exited 0 and wrote no answer is asked once more (issue #970) ──────────────────
  #
  # `change_answered` is right that an empty report is not a verdict (#827), and the run's answer to it was to end
  # the item where it stood: measured 2026-11-18, item #892 spent 2 166 s and 6.29 Mtok, its `worker-report.md` was 0 B
  # with status 0 while a finished diagnosis stood beside it as `evidence-diagnosis.md`, and the item blocked
  # `child-failed` with no gate verdict at all (`child-failed`/`child-blocked` is the archive's largest stop class:
  # 5 of 18 items). Every other bound the run enforces already has a second chance — a checkpoint continues a cut
  # item, a leftover patch is applied where it fits, a cut item is resumed by the next run — and a child that exits 0
  # and writes twelve bytes too few is the cheapest of them to recover from: the tree is where the child left it and
  # the phase's own prompt is on disk.
  #
  # The relaunch composes **the checkpoint prompt** (`prompts/worker-continuation.md`), which already names the tree,
  # the leftover and what is still owed, so a retry is not a second definition of what a phase is asked — nothing new
  # is composed here, and the values are the ones the continuation renders with. It is asked **once**, and two silent
  # children in a row are a finding about the phase: the item then blocks with the attempt count in its reason.
  #
  # Three guards, each of them a sentence the item's own record already keeps:
  #   * `worker_exit -eq 0` — a child the **run** ended at a bound returns `LOOP_CHILD_TIMEOUT` (#224) and a report
  #     carrying `LOOP-TIMEOUT`/`LOOP-DEADLINE` is an answer to `change_answered`, so neither is retried: asking again
  #     would double a spend the run had already decided to stop.
  #   * `worker_write_decides -eq 0` — a change standing in a tree the run cannot vouch for decides the item where it
  #     is read (#800), and retrying it would read past that reading.
  #   * `retry_max -gt 0` and one entry of the same budget the other transient classes spend (`LOOP_RETRY_MAX`,
  #     `loop.conf`): a project that records `0` retries is a project that wants this shape left as it was, which is
  #     what `run#anEmptyAnswerOverACommitIsTheChangePhasesFailure` reads.
  #
  # …and one fact about the tree, which is what separates this silence from the one #752 retries: a tree that carries
  # nothing — HEAD at the run's base **and** a clean status, the shape a provider that refused every call leaves (#742)
  # — keeps the item-level retry it already has, which re-renders the **first** prompt. Both halves are read, because a
  # child's work is uncommitted until the run commits it below: a dirty tree at the base is work, and a clean tree past
  # it is work the child committed itself. The checkpoint composition below tells the child the tree it is handed already
  # holds that work, so it is only the honest prompt for a tree the phase moved.
  worker_head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
  worker_tree_work=""
  if [ "$worker_head_now" != "$base" ]; then
    worker_tree_work=1
  elif [ -n "$(git -C "$worktree" status --porcelain 2>/dev/null | head -1)" ]; then
    worker_tree_work=1
  fi
  if [ "$worker_exit" -eq 0 ] && [ "$worker_write_decides" -eq 0 ] && [ "$retry_max" -gt 0 ] && [ "$retry_spent_child_start" -lt "$retry_max" ] && [ -n "$worker_tree_work" ] && ! change_answered "$worker_report"; then
    answer_bytes=$(wc -c < "$worker_report" 2>/dev/null || printf 0)
    retry_spent_child_start=$((retry_spent_child_start + 1))
    change_answer_retried=1
    # The prompt names the tree and nothing it does not have: there is no patch, because the child was not ended and
    # nothing was kept — its work is the change root as it stands, uncommitted, which is exactly what `worktree.sh
    # end-item` would commit later.
    render_prompt "$LOOP_DIR/prompts/worker-continuation.md" "$item_dir/worker-prompt-retry.md" \
      "GATE_STEP=$(gate_step_text "$pre_gate")" \
      "ISSUE=$issue" "CLASS=$class" "PROPOSE_CLASS=$propose_class" \
      "BOUND_LINE=it exited 0 and wrote none of the lines the run reads ($answer_bytes B) — the phase answered, but its answer never reached its report" \
      "PATCH=none — the work that process left is the tree itself, uncommitted" \
      "PATCH_FILES=0" "PATCH_HUNKS=0" \
      "PATCH_PATHS=  - the change root as the first process left it" \
      "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
      "LEDGER=$item_ledger" "SCRATCH_DIR=$scratch_dir" "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" \
      "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS"
    loop_emit change-not-answer-retry bytes="$answer_bytes" --stderr
    progress item "#${issue} the change child answered nothing — the phase is asked once more from the tree it stands in (retry $retry_spent_child_start of $retry_max), and its report is written to $item_dir/worker-report-retry.md"
    run_child "$item_dir/worker-prompt-retry.md" "$item_dir/worker-report-retry.md"
    worker_exit=$?
    worker_report="$item_dir/worker-report-retry.md"
    worker_prompt="$item_dir/worker-prompt-retry.md"
    # The reading of the project tree `run_child` takes belongs to the child that just ended, so it is the retry's now:
    # a write this one made is read where the arms below read one, and the first child's reading stays in the step the
    # block above already recorded.
    worker_write_note="$outside_write_note"
    worker_write_attributed="$outside_write_attributed"
    worker_write_decides=0
    outside_write_decides && worker_write_decides=1
    loop_state step A --name worker-answer-retry --status ok \
      --note "exit 0 and no line the run reads (${answer_bytes} B) — the phase was asked once more, and the second asking (exit $worker_exit) wrote $(wc -c < "$worker_report" 2>/dev/null || printf 0) B" >/dev/null 2>&1 || true
  fi

  # The child that ran the gate, and the report its result is read from: the item's change child, unless the run
  # deferred the gate to a child of its own below (issue #758).
  gate_report="$worker_report"
  gate_prompt="$worker_prompt"
  gate_child="worker"
  gate_exit="$worker_exit"
  gate_write_note="$worker_write_note"
  gate_write_decides="$worker_write_decides"

  # ── the diff is reviewed before the gate, and a fresh child runs the one gate (issue #758) ────────────────────
  #
  # The change ends the child that made it: with `--pre-gate-review on` (`worker.md`'s `{{GATE_STEP}}`) the change
  # child stops at the diff and reports `LOOP-DIFF-READY` instead of a gate line, a fresh reviewer reads that diff,
  # and the gate child applies its findings in one pass and then runs the item's gate **once**. Measured on this
  # loop's archive (`python3 tools/loop-spend.py`, 2026-11-03, 227 items): 1 581 of 2 591 Mtok — 61 % — are spent
  # after the earlier of the item's first gate and its first focused suite run (the predicate that reading took
  # before #1008 split the two), 117 child sessions ran the gate three times or more, and a finding a reader could
  # have made from the diff alone was paid for twice: the gate it did not need, and the second full pass the rework
  # that answered it then made. The reviewer itself is cheap — 7 522 turns at 53.6 k tokens against the worker's
  # 84.5 k at 25 654 turns — so a round before the gate does not reduce how many rounds an item takes, it reduces
  # what a round costs.
  #
  # What stays where it was: the review round after the gate, because a change is judged after its verification as
  # well. The tail records the gate's result and the evidence; the archive is the child **before** the gate's (issue
  # #837). The round below is **not** a round of the review budget: it refuses
  # nothing, the item is never blocked by what it finds, and the round `--max-review-rounds` bounds is the one after
  # the gate. Its report stays in the item's directory and is named in the item's step, which is where a reader finds
  # what the change was asked to fix before its gate ran.
  #
  # A change child that ran the gate itself — one that ignored the prompt, or an item worked under the one-child
  # contract — leaves a gate line and no `LOOP-DIFF-READY`, and the item is read exactly as it was: a gate the run did
  # not run is not re-run, and a round before a gate that has already run has nothing to read.
  pre_gate_refusal=""
  pre_gate_refusal_verdict=""
  # Whether the round before the gate answered nothing at all — cut on its way to the file twice (issue #953). The
  # round is advisory by its own contract (`prompts/pre-gate-review.md`): what it may do is hand findings to the child
  # that runs the gate, and it is not a round of the review budget, so an answer that never arrived is **not** a
  # refusal. Measured 2026-11-17 (run 10:58Z→16:25Z, items #916 and #917): each reviewer was handed the item's prompt,
  # answered the run with a question (`Want me to execute review against diff?`), the byte-identical retry asked the
  # same question, and the second cut refused an item whose gate had never been started — 20.4 Mtok and 3 commits
  # handed to a person. The round is recorded `unanswered` and the gate child decides the item, which is what it was
  # going to do anyway.
  pre_gate_unanswered=0
  # Whether the round was skipped because its own input was halved (issue #969), and the sentence the item's record
  # keeps for it. `pre_gate_report` stays empty on a skip, so no arm below reads a report that was never written.
  pre_gate_skipped=0
  pre_gate_skip_reason=""
  pre_gate_report=""
  # What the round%ss input bound cut, in the words `bound_review_inputs` composed (issue #924): empty when every
  # part of this item%ss rounds was under the cap, and the figures the pre-gate round read when it was the one over it.
  review_input_cut_scale=""
  # How many restarts of a bound-ended round have been spent, against `LOOP_REVIEW_RESTART_MAX` (issue #918), and
  # the line the item%ss record keeps of one that happened. The count is **per round** and is reset where a round
  # starts: what the key bounds is what a run pays to finish one round it cannot answer, so a round-2 cut after a
  # round-1 restart still gets its one, and a round that takes two restarts is the item%ss cut.
  review_restarts_used=0
  # …and the line a restart puts in the record, reset with it: the run may work several items, and an item that took
  # no restart must not inherit the one before it.
  review_restart_line=""
  # …and the file the two readings of a round the run cut **twice** leave (issue #987), reset with them for the same
  # reason: the run may work several items, and an item whose round was not cut twice must not inherit the evidence of
  # one that was.
  review_findings_file=""
  # The reading the round's last attempt stands in place of — the round before the gate's and the reviewed round's —
  # is the report the run had in hand a line before it named the next attempt, and only the caller holds it:
  # `<round>-restart.md` is the same name whichever attempt was restarted, and a round whose answer was cut on its way
  # to the file is asked again before it is ever restarted, so a reading taken off the final report's name reaches
  # past the one it replaced (issue #1045). Empty until an attempt replaces another, so a round the run cut once keeps
  # the shape it had.
  pre_gate_replaced_report=""
  review_replaced_report=""
  # What the round answered, when it answered and refused nothing (issue #813). The refusal verdicts above are what
  # the run's outcome reads; this is what the item's record keeps, and it is the round's own answer — `PASS` or `FAIL`,
  # the two the prompt asks for — so that an item which read a round and then ended on its gate does not record
  # `reviewVerdict: "off"` / `reviewRounds: 0` beside that round's spend.
  pre_gate_verdict=""
  # Whether the round that never finished was ended by a **bound** — the run's own deadline or the reviewer's role's
  # turn bound — rather than by the transport (issue #785). A bound's round is a cut, and a cut is not the item's: it
  # is recorded `cut`, escalated to nobody and resumed by the next run, where a report the transport cut twice is a
  # block the owner is asked about.
  pre_gate_cut=0
  # Whether the change child claimed a change and left the tree at the run's base — a reading taken before the child
  # that would run the gate, so that nothing is paid for afterwards (issue #797). Empty for every other shape.
  empty_tree_reading=""
  # The bound a **review round** the run ended hit: the round's own fact — its number, its key and the figure — and,
  # beside it, the item's stop class (issues #923, #898). #923 recorded the round here and left the class off the item,
  # which is what the spec then asked for, and the two runs it was filed from read `cut` with an empty `stop` cell: #898
  # was ended by the reviewer's turn bound at 110 turns and #883 by the item's context bound in the reviewer phase, so a
  # reader could not tell which bound fired, or that a bound fired at all, without the child's transcript. A bound the
  # run enforces is the run's whichever phase it ended, so the class and its key reach the item's record through the
  # same path the item's own child uses (`stop_reason`, `bound_key`), and the round keeps its own field beside them.
  # The key and the figure are read from the round's own `LOOP-TIMEOUT` line, by the same reader the item's class uses,
  # so a round ended by the run's deadline (no key: the deadline is a clock, not a value in `loop.conf`) records the
  # class and no figure and keeps the verdict it already carried.
  round_bound_round=""
  round_bound_key=""
  round_bound_value=""
  # Whether the phase that ran the gate answered nothing at all — measured in the phase itself, because a child that
  # exits 0 and writes no report is that phase's failure and no reading about the item's tree (issue #810). Empty for
  # every other shape, and kept beside `empty_tree_reading` for the same reason: it is decided before the outcome block
  # and it names a phase, not a verdict.
  phase_failure=""
  # Whether this item's change child was asked once more because its answer carried no line the run reads (issue #970).
  # It is the item's own fact and not the run's retry counter, which counts every transient class the run spent: a run
  # that retried another item's child before this one must not read its own figure as this item's attempts.
  change_answer_retried=0
  # The change child's own silence is read here, before the phase that would follow it (issue #827): a report that
  # carries no diff marker, no gate line and no verdict is nothing any arm below can read, so the round before the
  # gate is not entered on it, no gate child is started, and the tail has nothing to archive — the item is that
  # phase's failure instead. The branch the child may have left behind stays kept and the run still commits its tree
  # (`end-item`, below), which is why the outcome must not fall through to the arm that names the item's diff. A tree
  # still at the base is the run's own stop (`could not start`, further down) and is read there: both readings are
  # taken, and the one that speaks for the run speaks first because it is the stronger statement about the child.
  # Since issue #970 the silence is asked once more first (the arm above), so a report this block reads a silence from
  # is the **second** asking's: the reason names the attempt count, which is strictly more than the run recorded
  # before, and a run whose retry budget is off keeps the sentence it always wrote.
  if [ "$worker_exit" -eq 0 ] && [ "$worker_write_decides" -eq 0 ] && ! change_answered "$gate_report"; then
    if [ "$change_answer_retried" -eq 1 ]; then
      phase_failure="the change worker exited 0 and wrote no answer twice ($(wc -c < "$gate_report" 2>/dev/null || printf 0) B, no line the run reads, after being asked once more) — the phase that would have made the change produced nothing the run could read, so the item is stopped by that phase and not by its own tree"
    else
      phase_failure="the change worker exited 0 and wrote no answer ($(wc -c < "$gate_report" 2>/dev/null || printf 0) B, no line the run reads) — the phase that would have made the change produced nothing the run could read, so the item is stopped by that phase and not by its own tree"
    fi
    loop_state step A --name worker-answer --status fail --note "exit 0 and no answer" >/dev/null 2>&1 || true
    progress note "the change child's answer is not an answer — the round before the gate and the gate child are not started on it"
  fi
  if [ "$pre_gate" = on ] && [ "$worker_exit" -eq 0 ] && [ "$worker_write_decides" -eq 0 ]; then
    change_diff_ready=$(report_field "$gate_report" LOOP-DIFF-READY)
    change_gate_claim=$(report_field "$gate_report" LOOP-GATE)
    change_verdict=$(report_field "$gate_report" LOOP-VERDICT)
    if [ -n "$change_diff_ready" ] && [ -z "$change_gate_claim" ] && [ -z "$change_verdict" ] &&
      [ -z "$(report_field "$gate_report" LOOP-TIMEOUT)" ] &&
      [ -z "$(report_field "$gate_report" LOOP-DEADLINE)" ]; then
      # The change is committed before anything reads it: the round's diff is `git diff <base>..HEAD`, and work the
      # run has not committed is not that.
      bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" >/dev/null 2>&1 || true
      head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
      progress commit "the change child stopped at the diff — HEAD ${head_now:0:12} (base ${base:0:12})"
      # …and a child that stopped at the diff and left the tree at the run's base committed nothing (issue #797). The
      # outcome block's own arm names that reading and keeps its place as the invariant; what must not happen is
      # everything that arm would be paid for first — the reviewer, the gate child and the tail — on a tree there is
      # nothing to read, run a gate on or archive. Measured 2026-11-06 (`runs/2026-11-06T11:44:21Z.json`): a gate phase
      # is 20–22 min of a 40–50 min item, and eight items over the archive learned their tree was at the base only after
      # one was paid. A child that answered the diff marker did read the item, so what this leaves is the item's own
      # block and not the run's "could not start" stop that the same empty tree beside an answer with nothing in it is.
      if [ "$head_now" = "$base" ]; then
        empty_tree_reading="the item ends at this reading, before any gate child: the change child stopped at the diff and the tree is still at the base commit, so nothing was committed$(item_outside_write_clause)"
        loop_state step A --name empty-tree --status ok --note "$empty_tree_reading" >/dev/null 2>&1 || true
        progress note "$empty_tree_reading"
      fi
      if [ "$head_now" != "$base" ]; then
        # The round's diff, its file and the pathspec are the post-gate round's own: one reviewer contract, one diff.
        review_exclude_spec=$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)
        diff_base="$base"
        diff_file="$item_dir/review-pre-gate.diff"
        write_review_diff "$diff_base" "$diff_file"
        # No round ran before this one, so the bound covers the two parts there are: the diff and the change's files.
        review_inputs=$(bound_review_inputs "$diff_file" "")
        review_input_cut_scale=$(review_input_scale_of "$diff_file")
        # ── the round is skipped when its own input is halved (issue #969) ────────────────────────────────────────
        #
        # A diff cut past half is one whose middle the round cannot see. The record does not make such a cut the cause
        # of the rounds that ended: of the three pre-gate rounds the run 2026-11-18T22:18Z ended at
        # `LOOP_CHILD_TURNS_MAX_REVIEWER_PRE_GATE=110`, none had its pre-gate input cut past half — while the cap cut
        # 5 of that run's 8 review inputs and `reviewer-pre-gate` spent 60.3 Mtok, 25 % of its 240.6 Mtok. A larger
        # cap does not answer it: #901's part is 98 662 B and #902's 149 838 B, so the cap moves the cut
        # rather than removing it. What is read instead is the share the cut already measured, which is why the
        # decision and the item's record are the same string (`metrics.reviewInput`): past half, the round is skipped
        # and the reason is recorded. The gate child decides the item on the diff itself — the round is advisory by its
        # own contract — and the item's first round reads the same diff whether this one ran or not.
        if review_input_cut_halved "$review_input_cut_scale"; then
          pre_gate_skipped=1
          pre_gate_skip_reason="the round before the gate was skipped: its input is halved — $review_input_cut_scale — so it cannot read the diff whole and the gate child decides the item on it"
          progress note "#${issue} $pre_gate_skip_reason"
        fi
        # The arm below keeps the indentation it had before the guard: it is one block, and moving eighty lines whose
        # text did not change is a diff the reader pays for and learns nothing from.
        if [ "$pre_gate_skipped" -eq 0 ]; then
        before=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
          { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
        pre_gate_report="$item_dir/pre-gate-review.md"
        render_reviewer_prompt "$item_dir/reviewer-pre-gate-prompt.md" "" \
          "$(cat "$LOOP_DIR/prompts/pre-gate-review.md")" "$review_inputs"
        run_child "$item_dir/reviewer-pre-gate-prompt.md" "$pre_gate_report"
        # An answer cut on its way to the file decides nothing, and it is the run's own transport rather than a
        # reviewer that stayed quiet, so the round is asked once more — the rule the rounds after the gate follow
        # (#224). A round a clock ended says so in its own report and is read as it stands.
        pre_gate_deadline=$(report_field "$pre_gate_report" LOOP-DEADLINE 2>/dev/null || true)
        if [ -z "$pre_gate_deadline" ] && [ -z "$(report_field "$pre_gate_report" LOOP-TIMEOUT)" ] &&
          ! answer_complete "$pre_gate_report" '^VERDICT: (PASS|FAIL)'; then
          loop_emit pre-gate-cut-retry last="$(last_line "$pre_gate_report")" --stderr
          pre_gate_replaced_report="$pre_gate_report"
          pre_gate_report="$item_dir/pre-gate-review-retry.md"
          render_reviewer_prompt "$item_dir/reviewer-pre-gate-prompt-retry.md" "" \
            "$(cat "$LOOP_DIR/prompts/pre-gate-review.md")" "$review_inputs"
          run_child "$item_dir/reviewer-pre-gate-prompt-retry.md" "$pre_gate_report"
          pre_gate_deadline=$(report_field "$pre_gate_report" LOOP-DEADLINE 2>/dev/null || true)
        fi
        # ── a bound ended this round: the run restarts it rather than handing the item forward (issue #918) ────────
        #
        # The restart reads the same bounded input in a fresh session and is handed the findings the cut round had
        # written; `LOOP_REVIEW_RESTART_MAX` bounds how many times one round may take it, and a restart begun past the
        # run's deadline is not started at all. The **final** report is the one the guards and the arms below read, so
        # only a second bound is the item's cut — the pre-#918 shape, kept for the round that cannot be answered.
        pre_gate_timeout_line=$(report_field "$pre_gate_report" LOOP-TIMEOUT)
        if [ -n "$pre_gate_timeout_line" ] && [ -z "$pre_gate_deadline" ] &&
          [ "$review_restarts_used" -lt "$review_restart_max" ] && review_restart_before_deadline; then
          review_restarts_used=$((review_restarts_used + 1))
          review_restart_note "the round before the gate" "$pre_gate_timeout_line" "$pre_gate_report"
          render_reviewer_prompt "$item_dir/reviewer-pre-gate-prompt-restart.md" "" \
            "$(cat "$LOOP_DIR/prompts/pre-gate-review.md")" "$review_inputs" \
            "$(review_restart_scope "$pre_gate_report" "$pre_gate_timeout_line" "round before the gate" "")"
          pre_gate_replaced_report="$pre_gate_report"
          pre_gate_report="$item_dir/pre-gate-review-restart.md"
          progress note "#${issue} restarts the round before the gate in a fresh session: the run ended it at $pre_gate_timeout_line, and the findings it had written are handed to the restart"
          run_child "$item_dir/reviewer-pre-gate-prompt-restart.md" "$pre_gate_report"
          pre_gate_deadline=$(report_field "$pre_gate_report" LOOP-DEADLINE 2>/dev/null || true)
        fi
        after=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
          { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
        # A reviewer that wrote cannot have been the reviewer (D3), and the two guards come first for the reason the
        # post-gate round states: the round is discarded whatever ended the child.
        if [ "$read_only" = on ] && [ "$before" != "$after" ]; then
          pre_gate_refusal_verdict="reviewer-wrote"
          pre_gate_refusal="the pre-gate review round was discarded: the working tree changed while the reviewer ran"
        elif outside_write_decides; then
          pre_gate_refusal_verdict="reviewer-wrote"
          pre_gate_refusal="the pre-gate review round was discarded: the project's own tree changed while the reviewer ran — $outside_write_note${outside_write_attributed:+ (attributed to this round: $outside_write_attributed)}"
        else
          if [ -n "$outside_write_note" ]; then
            loop_state step A --name outside-write-pre-gate --status ok --note "$outside_write_note" >/dev/null 2>&1 || true
            progress note "$outside_write_note"
          fi
          pre_gate_timeout_line=$(report_field "$pre_gate_report" LOOP-TIMEOUT)
          if [ -n "$pre_gate_deadline" ]; then
            deadline_claim="$pre_gate_deadline"
            pre_gate_cut=1
            pre_gate_refusal_verdict="deadline"
            pre_gate_refusal="the run's deadline passed while the pre-gate reviewer was running — the round was cut, not refused, so the change is left for the next run"
            # The restart the run paid for was ended at a bound too, so this reading is the second of two and what both
            # wrote is kept for whoever resumes the item (issue #987). The call prints nothing unless the report's own
            # name says it is a restart, so a round the deadline ended once keeps the shape it had. The reading this
            # restart stands in place of is the one the run had in hand before it named the restart — the retry, when
            # the round's first answer was cut on its way to the file — and not the name of its first attempt (#1045).
            review_findings_file=$(review_findings_for_double_cut "$item_dir" "$pre_gate_report" "$pre_gate_replaced_report" || true)
          elif [ -n "$pre_gate_timeout_line" ]; then
            pre_gate_cut=1
            pre_gate_refusal_verdict="cut-short"
            # The ended child's own transcript names the bound and the figure it reached, and the reason on the item
            # quotes that line: a reader of the blocked item must not have to choose between the two bounds, or
            # re-derive from a transcript nobody handed them which number ended the round (issue #829).
            pre_gate_refusal="the run ended the pre-gate reviewer at one of its spend bounds — $pre_gate_timeout_line — the round was cut, not refused, so the change is left for the next run"
            # …and the round's ending is recorded beside the refusals it did not write (issue #923): the item keeps no
            # class for it, so without this the bound that ended the round survives only in the reason's prose.
            # …and, when this is the second bound this round took, what the two readings wrote (issue #987). A
            # restart handed the first reading's findings in its prompt and wrote none of them down, so without this
            # the run's account of the change dies with the sessions that produced it.
            review_findings_file=$(review_findings_for_double_cut "$item_dir" "$pre_gate_report" "$pre_gate_replaced_report" || true)
            round_bound_round="pre-gate"
            round_bound_line=$(bound_key_value "$pre_gate_timeout_line")
            round_bound_key="${round_bound_line%% *}"
            round_bound_value="${round_bound_line##* }"
          elif ! answer_complete "$pre_gate_report" '^VERDICT: (PASS|FAIL)'; then
            # The round's answer never arrived, twice. It refuses nothing — a round that answered nothing is not a
            # reviewer that refused the change (issue #953) — so the round is recorded `unanswered` beside the answers
            # it did not write, and the run carries on to the gate child below: the gate is the item's one verdict.
            pre_gate_unanswered=1
          fi
        fi
        fi
      fi
      if [ -z "$pre_gate_refusal" ] && [ "$head_now" != "$base" ]; then
        # What the round found, in the shape the post-gate round is read in: only an explicit refusal with a count
        # hands anything to the child that follows. A `VERDICT: FAIL` with zero blocking findings is a pass whose
        # notes are recorded, and a report with no count line at all refuses nothing.
        blocking=$(blocking_count "$pre_gate_report")
        pre_gate_findings=""
        pre_gate_verdict=$(report_verdict "$pre_gate_report")
        case "$pre_gate_verdict" in PASS|FAIL|NOTES) ;; *) pre_gate_verdict="" ;; esac
        # A round that answered nothing is the fourth answer the item's record can carry (issue #953): neither a pass
        # nor a refusal, and not the `cut-short` a bound leaves either — no bound ended it, the transport did.
        if [ "$pre_gate_unanswered" -eq 1 ] && [ -z "$pre_gate_verdict" ]; then
          pre_gate_verdict="unanswered"
        fi
        if [ "$pre_gate_verdict" = FAIL ] && [ -n "$blocking" ] && [ "$blocking" -gt 0 ]; then
          pre_gate_findings="$pre_gate_report"
        fi
        # The step the item keeps for this round: the diff read before the gate, or the skip its own input bought
        # (issue #969) — the status the report counts as its own class on the `review:` line.
        if [ "$pre_gate_skipped" -eq 1 ]; then
          loop_state step A --name pre-gate-review --status skip --note "$pre_gate_skip_reason" >/dev/null
        else
          # The note's own words first, and the step second: a value handed to `--note` is data, and the run's lines
          # are the record's (`events.tsv`), so this branch composes no line of its own.
          if [ "$pre_gate_verdict" = unanswered ]; then
            pre_gate_note='the round before the gate answered nothing, so it records `unanswered` and the gate child decides the item'
          elif [ -n "$pre_gate_findings" ]; then
            pre_gate_note="$blocking blocking finding(s) handed to the gate child"
          else
            pre_gate_note='no blocking finding'
          fi
          loop_state step A --name pre-gate-review --status ok \
            --note "the diff was read before the gate: $pre_gate_note" >/dev/null
        fi
        if [ "$pre_gate_verdict" = unanswered ]; then
          progress note "#${issue} the round before the gate answered nothing twice — the round is recorded unanswered and the gate child decides the item"
        fi
        # The gate child is a phase of its own: it is handed what the change child established, and the findings when
        # there are any, and it decides nothing the change child already decided.
        gate_prompt_values=(
          "ISSUE=$issue" "CHANGE_ROOT=$worktree" "EVIDENCE_DIR=$item_dir" "BASE=$base"
          "SCRATCH_DIR=$scratch_dir"
          "GATE_CMD=$LOOP_GATE_CMD" "WORKER_SKILLS=$LOOP_WORKER_SKILLS"
          "CHANGE_NAME=${change_diff_ready:--}" "CHANGE_ANSWER=$(report_field "$gate_report" LOOP-CHANGE)"
          "ROOT_CAUSE=$(report_field "$gate_report" LOOP-ROOT-CAUSE)"
          "RED_TEST=$(report_field "$gate_report" LOOP-RED-TEST)"
          "FINDINGS_PATH=${pre_gate_findings:-$(if [ "$pre_gate_verdict" = unanswered ]; then printf 'none — the round before the gate never answered, so nothing was handed forward'; elif [ "$pre_gate_skipped" -eq 1 ]; then printf 'none — the round before the gate was skipped: its input is halved, so this child decides the item on the diff itself'; else printf 'none — the round read before the gate found no blocking finding'; fi)}"
        )
        render_prompt "$LOOP_DIR/prompts/worker-gate.md" "$item_dir/worker-gate-prompt.md" \
          "${gate_prompt_values[@]}"
        # The findings are appended rather than substituted, for the reason the post-gate rework appends them: a
        # report is arbitrary text, and a value substituted into a skeleton is a value the skeleton's own
        # placeholders would then rewrite.
        if [ -n "$pre_gate_findings" ]; then
          printf '\n## The round that read this diff before the gate refused it; apply exactly these findings\n\n' \
            >> "$item_dir/worker-gate-prompt.md"
          cat "$pre_gate_findings" >> "$item_dir/worker-gate-prompt.md"
        fi
        run_child "$item_dir/worker-gate-prompt.md" "$item_dir/worker-gate-report.md"
        gate_exit=$?
        gate_child="gate worker"
        gate_prompt="$item_dir/worker-gate-prompt.md"
        gate_report="$item_dir/worker-gate-report.md"
        # A phase's report is the phase's **answer**, not optional text beside it (issue #810). Measured 2026-11-06 on
        # item #755: the gate child exited 0 and left a 0-byte report, `tail_after_gate` found no gate line and started
        # no tail, the item was decided from that empty file — whose change claim is empty — and the arm that names the
        # item's own tree wrote "reported success but no archived change appears in the run's diff" over a branch that
        # carries a commit. The rule the rounds already follow (#224) is asked of the phase here: an answer that is not
        # an answer is asked once more, the same prompt contract written to a name of its own, and the second silence is
        # this phase's failure — carried to the item's reason by `phase_failure`, never read as the item's tree.
        gate_note="exit $gate_exit"
        if [ "$gate_exit" -eq 0 ] && ! gate_answered "$gate_report"; then
          loop_emit gate-not-answer-retry last="$(last_line "$gate_report")" --stderr
          gate_report="$item_dir/worker-gate-report-retry.md"
          gate_prompt="$item_dir/worker-gate-prompt-retry.md"
          render_prompt "$LOOP_DIR/prompts/worker-gate.md" "$gate_prompt" \
            "${gate_prompt_values[@]}"
          run_child "$gate_prompt" "$gate_report"
          gate_exit=$?
          gate_note="exit $gate_exit"
          if [ "$gate_exit" -eq 0 ] && ! gate_answered "$gate_report"; then
            phase_failure="the gate worker exited 0 and wrote no report, twice — the phase that would have run the gate produced no answer the run could read, so the item is stopped by that phase and not by its own tree"
            gate_note="exit 0 and no report"
          fi
        fi
        gate_write_note="$outside_write_note"
        gate_write_decides=0
        outside_write_decides && gate_write_decides=1
        loop_state step A --name worker-gate \
          --status "$(if [ "$gate_exit" -eq 0 ] && [ -z "$phase_failure" ]; then printf ok; else printf fail; fi)" \
          --note "$gate_note" >/dev/null
      fi
    fi
  fi

  tail_started=0
  tail_exit=0
  tail_report="$item_dir/worker-tail-report.md"
  # A child that answered for the item itself — a skip, a falsification, a block — has nothing to archive, so it
  # starts no tail whatever its gate line says: a falsified report carries `LOOP-GATE: not run` as its own record of
  # the gate it did **not** run, and reading that as "the gate ran" would start a child with an archive to write for a
  # tree the item never changed. `closed` is the one verdict that does not settle it here, because it is the shape a
  # report written against the older one-child contract carries; the tail then verifies the archive the same way.
  first_verdict=$(report_field "$gate_report" LOOP-VERDICT)
  if [ -z "$pre_gate_refusal" ] && [ -z "$empty_tree_reading" ] && [ -z "$phase_failure" ] && [ "$gate_exit" -eq 0 ] && [ "$gate_write_decides" -eq 0 ] &&
    { [ -z "$first_verdict" ] || [ "$first_verdict" = closed ]; } &&
    [ -z "$(report_field "$gate_report" LOOP-TIMEOUT)" ] &&
    [ -z "$(report_field "$gate_report" LOOP-DEADLINE)" ]; then
    tail_after_gate "$gate_report" "$item_dir/worker-tail-prompt.md" "$tail_report" || true
  fi
  if [ "$tail_started" -eq 1 ]; then
    loop_state step A --name worker-tail \
      --status "$(if [ "$tail_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
      --note "exit $tail_exit" >/dev/null
  fi
  # The report the item is decided from, and the child whose ending decides the commit: the tail's when one ran,
  # the gate child's otherwise. Both are recorded, so a reader of the item reads the gate's numbers in the first
  # transcript and the archive's in the second.
  final_report="$gate_report"
  final_prompt="$gate_prompt"
  last_child="$gate_child"
  last_exit="$gate_exit"
  if [ "$tail_started" -eq 1 ]; then
    final_report="$tail_report"
    final_prompt="$item_dir/worker-tail-prompt.md"
    last_child="tail worker"
    last_exit="$tail_exit"
  fi

  verdict_claim=$(report_field "$final_report" LOOP-VERDICT)
  skip_verdict=$(report_field "$final_report" LOOP-SKIP-VERDICT)
  change_claim=$(report_field "$final_report" LOOP-CHANGE)
  root_cause=$(report_field "$final_report" LOOP-ROOT-CAUSE)
  worker_decision=$(report_field "$final_report" LOOP-DECISION 2>/dev/null || true)
  red_test=$(report_field "$final_report" LOOP-RED-TEST)
  gate_claim=$(report_field "$final_report" LOOP-GATE)
  # A close over a red gate is a judgement the closing child records, not prose its transcript keeps (issue #811):
  # the clause it was waived with, and the suite the clause names where it names one, travel to the record beside
  # the tally so a report can count them.
  gate_waived=$(report_field "$final_report" LOOP-GATE-WAIVED)
  gate_waived_suite=$(report_field "$final_report" LOOP-GATE-WAIVED-SUITE)
  outside_write=$(report_field "$final_report" LOOP-OUTSIDE-WRITE)
  timeout_claim=$(report_field "$final_report" LOOP-TIMEOUT)
  # The run's own deadline is a different clock from the item's wall-clock cap (issue #227): the transcript says
  # which one ended the child, and only the deadline's cut leaves the item for the next run instead of asking a
  # question about it (issue #674).
  deadline_claim=$(report_field "$final_report" LOOP-DEADLINE 2>/dev/null || true)
  # A falsification is the one skip whose finding is not a question: the worker measured that the mechanism the
  # item blames does not hold. Both halves are required before the run treats it as one — what was blamed, and
  # what was measured instead with how it was taken — because a claim nobody can check is a question again
  # (openspec change `falsified-items-close-in-the-loop`).
  falsified_mechanism=$(report_field "$final_report" LOOP-FALSIFIED 2>/dev/null || true)
  falsified_measurement=$(report_field "$final_report" LOOP-MEASURED 2>/dev/null || true)

  # An answer of 0 bytes is no answer wherever the tree stands (issue #882). The arm below — the child that never
  # started — asks that question only while HEAD is still the base, and measured 2026-11-14 showed what that leaves: a
  # continuation child exited 0 with a 0-byte report over a tree that already carried work, no arm read the empty
  # answer, the item was recorded `blocked` and 14.5 Mtok were lost.
  #
  # The empty answer spends one retry of the same budget — the same counter, the same delays read by position — and it
  # is spent **in place**: the same child, the same prompt, the same report path, read again. The item is not re-picked
  # from the queue, because the branch carries what the attempt kept and a second pick would read that work as an
  # unfinished attempt instead of asking the question again. The class is named `answer-empty` in the line the run
  # prints and in the step the item's record carries. A second empty answer is the run's stop, with both attempts and
  # their byte counts in the note. A report that carries a verdict of its own is never re-run here, and neither is one
  # the child's timeout or the run's deadline ended, nor a phase the gate arm above already asked twice and named as
  # the item's failure (#810): that reading is the phase's verdict, not this class's. With nothing left to spend the
  # arm stays out of the way, so a run whose budget is off keeps the outcome the item's own evidence earns.
  head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
  if [ -e "$final_report" ] && [ ! -s "$final_report" ] && [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] &&
    [ -z "$deadline_claim" ] && [ -z "$phase_failure" ] && [ "$head_now" != "$base" ]; then
    if [ "$retry_spent_answer_empty" -eq 0 ] && [ "$retry_spent_child_start" -lt "$retry_max" ]; then
      retry_spent_answer_empty=1
      retry_spent_child_start=$((retry_spent_child_start + 1))
      retry_delay=$(retry_delay_at "$retry_spent_child_start" "$retry_delays")
      loop_state step A --name answer-empty --status ok \
        --note "the $last_child wrote a 0-byte report over HEAD ${head_now:0:12} (base ${base:0:12}) — asked the same question again, retry $retry_spent_child_start of $retry_max after ${retry_delay} s" >/dev/null 2>&1 || true
      loop_emit answer-empty-retry child="$last_child" retry="$retry_spent_child_start" of="$retry_max" \
        delay="$retry_delay" retry_max="$retry_max" retry_delays="$retry_delays" --stderr
      [ "$retry_delay" -eq 0 ] 2>/dev/null || sleep "$retry_delay"
      run_child "$final_prompt" "$final_report"
      last_exit=$?
      answer_bytes=$(wc -c < "$final_report" 2>/dev/null || printf 0)
      retry_log_answer_empty="attempt 1 wrote 0 B, attempt 2 (exit $last_exit) wrote $answer_bytes B"
      loop_emit answer-empty-retry-ended child="$last_child" exit="$last_exit" bytes="$answer_bytes" --stderr
      verdict_claim=$(report_field "$final_report" LOOP-VERDICT)
      skip_verdict=$(report_field "$final_report" LOOP-SKIP-VERDICT)
      change_claim=$(report_field "$final_report" LOOP-CHANGE)
      root_cause=$(report_field "$final_report" LOOP-ROOT-CAUSE)
      worker_decision=$(report_field "$final_report" LOOP-DECISION 2>/dev/null || true)
      red_test=$(report_field "$final_report" LOOP-RED-TEST)
      gate_claim=$(report_field "$final_report" LOOP-GATE)
      gate_waived=$(report_field "$final_report" LOOP-GATE-WAIVED)
      gate_waived_suite=$(report_field "$final_report" LOOP-GATE-WAIVED-SUITE)
      outside_write=$(report_field "$final_report" LOOP-OUTSIDE-WRITE)
      timeout_claim=$(report_field "$final_report" LOOP-TIMEOUT)
      deadline_claim=$(report_field "$final_report" LOOP-DEADLINE 2>/dev/null || true)
      falsified_mechanism=$(report_field "$final_report" LOOP-FALSIFIED 2>/dev/null || true)
      falsified_measurement=$(report_field "$final_report" LOOP-MEASURED 2>/dev/null || true)
    fi
    if [ "$retry_spent_answer_empty" -eq 1 ] && [ ! -s "$final_report" ] && [ -z "$verdict_claim" ] &&
      [ -z "$timeout_claim" ] && [ -z "$deadline_claim" ]; then
      answer_bytes=$(wc -c < "$final_report" 2>/dev/null || printf 0)
      # The in-progress mark comes off before the run stops, for the reason the arm below gives: an item the run
      # asked twice and could not read must be as eligible afterwards as it was before the run picked it.
      loop_labels unpick "$issue" >&2 || true
      in_progress=""
      stop_run "the child answered nothing twice — the $last_child left a 0-byte report over HEAD ${head_now:0:12} (base ${base:0:12}), and the answer-empty retry left $answer_bytes B, neither with a verdict line; the retry spent $retry_spent_child_start of $retry_max (LOOP_RETRY_MAX=$retry_max, LOOP_RETRY_DELAYS=$retry_delays) — ${retry_log_answer_empty:-the retry ran before this run}"
      break
    fi
  fi

  # A red gate is not a landing (issue #837, the owner's decision recorded on the item at 2026-11-11T06:04:18Z): the
  # change is completed — its pointers checked, its delta merged and the directory archived — **before** the gate
  # measures the tree, so a gate that does not pass leaves a change nobody may read as archived. The restore is the
  # run's and not a child's prose for the reason every other judgement here is the run's: a restore after a green gate
  # would unarchive the very change the item is about to land.
  #
  # The predicate is the item's **verdict**, and only where no verdict exists is it the gate's own tally. A red gate
  # the closing child waived with `LOOP-GATE-WAIVED` is a close the record carries and the report counts
  # (`run#aCloseOverARedGateIsWaivedOrBlocked`, issue #811), so the archive it closed over stays — the tail's report is
  # read before this point, which is why this decision is taken here and not before the tail is started. A gate no
  # child could speak for — no tail ran at all — is restored from the tally alone, through the one predicate its
  # readers share. It happens before the run commits, so the tree the next attempt reads is the unarchived change and
  # no reader meets an archive no gate passed.
  gate_did_not_pass=0
  gate_restore_line=""
  if [ "$verdict_claim" = blocked ]; then gate_did_not_pass=1; fi
  if [ "$tail_started" -eq 0 ]; then
    gate_tally_at_restore=$(report_field "$gate_report" LOOP-GATE)
    if [ "$(gate_tally_state "$gate_tally_at_restore")" = red ]; then gate_did_not_pass=1; fi
    case "$gate_tally_at_restore" in *"not run"*) gate_did_not_pass=1 ;; esac
    # A gate child that archived and then answered nothing twice leaves no verdict and no tally (issue #810, the
    # `phase_failure` arm above): nothing speaks for that gate, so the archive it wrote is restored like a red one.
    if [ -z "$verdict_claim" ] && [ -z "$gate_tally_at_restore" ] && [ -n "$phase_failure" ]; then gate_did_not_pass=1; fi
  fi
  if [ "$gate_did_not_pass" -eq 1 ]; then
    restore_name=$(report_field "$final_report" LOOP-CHANGE)
    case "$restore_name" in ''|-) restore_name="${change_diff_ready:-}" ;; esac
    case "$restore_name" in
      ''|-|none) gate_did_not_pass=0 ;;
      *) # Nothing to restore is not a refusal to report: a child that never started, or one that answered nothing, names
         # no change and leaves no archive, so this arm stays silent rather than printing a refusal into a transcript that
         # asked for none — and only a tree that really carries the archive is moved.
         [ -n "$(loop_change_archive_dir "$worktree" "$restore_name")" ] || gate_did_not_pass=0 ;;
    esac
  fi
  if [ "$gate_did_not_pass" -eq 1 ]; then
    gate_restore_line=$(loop_change_restore "$worktree" "$restore_name" 2>&1) || true
    if [ -n "$gate_restore_line" ]; then
      relay "$gate_restore_line"
      # A refusal is not a restore: it is recorded as a skip, so `state.sh show` does not read an operator-facing
      # refusal as a step that succeeded.
      loop_state step A --name change-restore \
        --status "$(case "$gate_restore_line" in LOOP-CHANGE-RESTORED:*) printf ok ;; *) printf skip ;; esac)" \
        --note "$gate_restore_line" >/dev/null 2>&1 || true
    fi
  fi

  # The run verifies rather than trusts: it commits the child's work itself and then reads the tree, the
  # archive and the gate's evidence, instead of repeating the child's claims (D3, D8).
  #
  # A child the run itself ended is unfinished, and the tree at that moment is whatever it was doing — a
  # half-applied mutation, a half-written file, a gate's scratch directory — so committing it asserts work the
  # child never claimed. Measured 2026-10-22 (issue #271): the worker of #262 was ended at 23:48 with mutation
  # 19.2 applied (`if false; then` in place of the corrective-round test), `end-item` committed that tree 18 s
  # later and the dead arm stayed on `origin/fix/issue-262-…` until it was amended by hand at 05:05. A commit is
  # the run's statement about a child that finished, so a child the run ended gets none: the branch stays at the
  # base, the item is blocked with the transcript, and the tree is kept exactly as the child left it — nothing is
  # discarded without `--reset-worktree` (issue #173), and the next start refuses it and names it.
  head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
  if [ "$last_exit" -eq "$LOOP_CHILD_TIMEOUT" ] || [ -n "$timeout_claim" ]; then
    progress commit "end-item skipped — the run ended this child, so its tree is not committed; HEAD is ${head_now:0:12} (base ${base:0:12})"
  else
    bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" >/dev/null 2>&1 || true
    head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
    progress commit "end-item returned — HEAD ${head_now:0:12} (base ${base:0:12})"
  fi

  # A child that never started is the run's fault, not the item's (issue #212). `run_child` returns the child's
  # status and nothing else, so a status alone cannot tell a child whose argv the environment refused — measured
  # 2026-10-20: every child of a run died in under 4 s with one 35-byte complaint and exit 1 — from a child that
  # worked the item and failed. The item's own evidence separates the two: a child that wrote no verdict and left
  # the tree at the base never read the item. Recording that as a blocked item put a question on the tracker that
  # nobody had asked and spent one of the run's decision slots, twice in 3 minutes. It is recorded once as the run's
  # own stop instead, and the item is left exactly as its owner set it. Two children stay on the item's side of this
  # line: one the run itself ended carries its own transcript line and is a blocked item (issue #203), and one that
  # left work behind has a branch, which is what a later pass escalates (issue #176).
  #
  # The status is not one of the two facts, because a provider refuses a call without touching the child's argv:
  # measured 2026-11-01T23:30:01Z (issue #742), #739 and #740 each left a 0-byte report and had every `turn_end`
  # event on `stopReason: error` after the provider refused each request, and the run read both as `piExit=0`,
  # recorded both `blocked` on "reported success but the tree is still at the base commit" and escalated both — two
  # decision slots no reviewer and no worker asked for, and the run stopped on two consecutive blocked items 125 s
  # into a 360-minute budget. A status of 0 beside an answer that carries no verdict and a tree still at the base is
  # the same "no part of it was read", so the evidence decides this line and the status is only quoted in it.
  # A child that stopped at the diff is not this arm's (issue #797): it answered the item — the diff marker is its own
  # claim that there is work — so a tree at the base beside that answer is the item's block, read at the diff and named
  # below, not the run's stop. Only an answer with nothing of its own in it still says no part of the item was read.
  if [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] && [ -z "$empty_tree_reading" ] && [ "$head_now" = "$base" ]; then
    child_words=$(last_line "$final_report")
    # How much answer there was, in the transcript the stop is recorded from: 0 B is the shape a refused provider
    # leaves, and it is what tells a reader of the stop reason why the run did not ask the item's owner anything.
    answer_bytes=$(wc -c < "$final_report" 2>/dev/null || printf 0)
    # …and the shape itself, where it is kept (issue #828): a provider that refused every call left its own refusal
    # in the child's event stream, and the stop names it so a reader tells a refused provider from a broken child.
    refused_by=$(child_refusal "${events_file:-}")
    # The in-progress mark comes off before the run retries or stops: an item nothing was done to must be as
    # eligible afterwards as it was before the run picked it.
    loop_labels unpick "$issue" >&2 || true
    in_progress=""
    # The transient class this arm recognises (issue #752): the child never read the item. Spend the recorded
    # retry budget on it before the stop — once per class per run, with the delay the configuration records by
    # position — so one refused provider costs the item's retry and not the window.
    if [ "$retry_spent_child_start" -lt "$retry_max" ]; then
      retry_spent_child_start=$((retry_spent_child_start + 1))
      retry_delay=$(retry_delay_at "$retry_spent_child_start" "$retry_delays")
      retry_log_child_start="${retry_log_child_start}${retry_log_child_start:+; }attempt $retry_spent_child_start after ${retry_delay} s: ${child_words:-no words} (${answer_bytes} B)"
      loop_emit child-start-retry child="$last_child" retry="$retry_spent_child_start" of="$retry_max" \
        delay="$retry_delay" retry_max="$retry_max" retry_delays="$retry_delays" issue="$issue" --stderr
      [ "$retry_delay" -eq 0 ] 2>/dev/null || sleep "$retry_delay"
      continue
    fi
    stop_run "the child could not start — the $last_child exited $last_exit with an answer of $answer_bytes B and no verdict line, leaving the tree at the base${child_words:+: $child_words}${refused_by:+ — refused by $refused_by}$(item_outside_write_clause "${outside_write_note:-}")${retry_log_child_start:+; retried $retry_spent_child_start of $retry_max (LOOP_RETRY_MAX=$retry_max, LOOP_RETRY_DELAYS=$retry_delays) — $retry_log_child_start}"
    break
  fi
  archives=$(git -C "$worktree" diff --name-only "$base"..HEAD -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 {print $4}' | sort -u)
  change=$(printf '%s\n' "$archives" | grep -x -- "$change_claim" 2>/dev/null | head -1)
  [ -n "$change" ] || change=$(printf '%s\n' "$archives" | head -1)
  # The gate's evidence is the tally this item's gate wrote, and one directory holds them (issue #849): the path the
  # profile names (`LOOP_GATE_TALLY_DIR`), which the worker's own start clears (`clear_stale_test_results`) and which
  # the gate clears once more before its first step (issue #848) — so what it holds now is this item's and nothing
  # else. Reading every XML under any `test-results` path in the worktree instead counted what stood before the item:
  # this repository tracks 24 such files, and `head -20` made the column the constant 20, so the no-evidence arm below
  # could never fire in it (measured 2026-11-11: `find . -path '*test-results*' -name '*.xml' | wc -l` = 24,
  # `git ls-files` counts 24). A profile that names no directory leaves the run with no tally to read.
  gate_tally=$(loop_conf_value LOOP_GATE_TALLY_DIR)
  case "$gate_tally" in
    /*) gate_tally_dir="$gate_tally" ;;
    '') gate_tally_dir="" ;;
    *) gate_tally_dir="$worktree/$gate_tally" ;;
  esac
  gate_xmls=""
  if [ -n "$gate_tally_dir" ] && [ -d "$gate_tally_dir" ]; then
    gate_xmls=$(find "$gate_tally_dir" -maxdepth 1 -name '*.xml' 2>/dev/null | head -20)
  fi
  gate_found=$(printf '%s' "$gate_xmls" | grep -c . || true)
  progress gate "read — $gate_found XML in the gate's tally directory ${gate_tally:-<none named>}"
  # The gate tally the item carries: the child's claim when it made one, what the run found otherwise, and "skipped"
  # when the project configured no gate at all (a project with no build still runs — D16). An item that claims no gate
  # and left no tally in that directory carries **no** gate result (issue #849): the empty field is the fact the report
  # reads as the gate a caller is owed (`tools/loop/report.sh`), while `0 xml` — what the tree-wide count wrote —
  # recorded an item as one that ran a gate and measured nothing.
  if [ -z "$LOOP_GATE_CMD" ]; then
    gate_field="skipped (no gate configured)"
  elif [ -n "$gate_claim" ]; then
    gate_field="$gate_claim"
  elif [ "$gate_found" -gt 0 ]; then
    gate_field="$gate_found xml"
  else
    gate_field=""
  fi
  # Evidence has to outlive the run: a change that cites a test result must carry it in its own
  # `evidence/` directory, because that is the versioned half of the change (invariant 1).
  cited_evidence=0
  if [ -n "$red_test" ] || [ -n "$gate_claim" ]; then cited_evidence=1; fi
  change_evidence=""
  [ -n "$change" ] && change_evidence="$worktree/openspec/changes/archive/$change/evidence"
  carried=0
  if [ -d "$change_evidence" ] && [ -n "$(ls -A "$change_evidence" 2>/dev/null)" ]; then carried=1; fi

  # A difference the run cannot tie to this child is recorded and does not decide the item (#274, #276, #285,
  # #286): the tree it measures is one the run's own landings fast-forward in the project and a peer session
  # writes too, so two readings of it are not evidence about the one child the run started. A write the in-time
  # reading tied to this child's own tree — or any difference at all, when the run declared the tree exclusive
  # (`--strict-outside-write yes`) — is evidence, and decides. The worker's own reading was recorded where it was
  # taken (issue #800); what is left here is the gate child's and the tail child's, each under a name of its own —
  # the step a later reader looks a difference up by must name the child it belongs to (issue #758, #744).
  if [ "$gate_child" = "gate worker" ] && [ -n "$gate_write_note" ] && [ "$gate_write_decides" -eq 0 ]; then
    loop_state step A --name outside-write-gate --status ok --note "$gate_write_note" >/dev/null 2>&1 || true
    progress note "$gate_write_note"
  fi
  # …and the tail child's reading, under a name of its own when one ran: two children are two readings, and the step
  # a later reader looks a difference up by must name the child it belongs to (issue #744).
  if [ "$tail_started" -eq 1 ] && [ -n "$outside_write_note" ] && ! outside_write_decides; then
    loop_state step A --name outside-write-tail --status ok --note "$outside_write_note" >/dev/null 2>&1 || true
    progress note "$outside_write_note"
  fi
  outcome="closed"
  blocked_reason=""
  # A round the transport cut twice refused nothing and does not stop the item (issue #953): the gate child below is
  # the item's one verdict, so `pre_gate_refusal` stays empty for that shape and the arms here read what the gate
  # child answered.
  # Which class of arm stopped the item: `refused` for a review round that refused the change, `unmergeable` for a
  # base that would not take the branch, `bound` for a child the run's own bound ended, and one class per remaining arm
  # (issue #865). Every block reaches the record through `block` above, so the class and the result are written
  # together and the vocabulary `state.sh done --stop-reason` holds is the whole of it.
  stop_reason=""
  # The pre-gate round comes first because it stopped the item before any gate ran (issue #758): its reason names the
  # round, and an item it ended has no gate result for the arms below to read.
  if [ -n "$pre_gate_refusal" ]; then
    # A round the run's own bound ended is not a refusal (issue #790): the item is recorded `cut` below, and it carries
    # `bound`, the class a bound leaves on the item whichever phase of it the run ended (the run's item #898). The class is what
    # the state write below reads, so the round's ending reaches the record through the same field the item's own
    # child's does, and the round's own field carries the round, its key and its figure beside it (issue #923).
    if [ "$pre_gate_cut" -eq 1 ]; then
      outcome="blocked"; blocked_reason="$pre_gate_refusal"; stop_reason="bound"
    else
      block refused "$pre_gate_refusal"
    fi
  elif [ "$continuation_too_wide" -eq 1 ]; then
    # The continuation the run started was ended at a context ceiling (issue #956), so the
    # item has been measured twice at the reading one child may hold: what it does not fit is its own size, not the
    # phase's progress. The verdict is the shaping one — `too-wide`, which the profile's `roles.shapingVerdicts` names
    # and `labels.sh` writes as the shaping label beside the hold — so the item stops re-entering the queue at full
    # size instead of paying the same ceiling again on a fresh branch. The reason names the ceiling the second child
    # reached and the phases that child itself listed, because reshaping the item is the answer, and it writes no
    # decision label: a hold is not a question (spec, "A hold is not a decision").
    outcome="skipped"
    skip_verdict=too-wide
    root_cause="the item does not fit one child's context: the continuation was ended at a context ceiling —
${timeout_claim:-the recorded context bound}${continuation_phases:+ · the phases it listed: $continuation_phases}"
  elif [ "$last_exit" -ne 0 ]; then
    block child-failed "the $last_child process exited $last_exit"
    # A child the run itself ended says so in the item's own reason. "exited 124" alone leaves the owner unable
    # to tell a run that bounded a hung child from a child that failed by itself (issue #203).
    if [ -n "$timeout_claim" ]; then blocked_reason="$blocked_reason — $timeout_claim"; fi
    # …and the mechanism is `bound`, not a refusal (issue #786): the run ended this child at a bound it enforces, so
    # nothing about the item was measured and the next item's failure is not predicted by this one's. That one word
    # is what `state.sh next` reads to keep such a block out of the pair it stops a run on, and what the block's
    # own escalation below reads to write no decision label.
    if [ "$last_exit" -eq "$LOOP_CHILD_TIMEOUT" ] || [ -n "$timeout_claim" ]; then stop_reason="bound"; fi
  elif outside_write_decides; then
    block outside-write "the $last_child wrote outside the worktree — the project's own tree changed while it ran: $outside_write_note${outside_write_attributed:+ (attributed to this child: $outside_write_attributed)}"
  elif [ "$verdict_claim" = "skipped" ] && [ "${skip_verdict:-}" = "falsified" ] \
    && [ -n "$falsified_mechanism" ] && [ -n "$falsified_measurement" ] && [ "$head_now" = "$base" ]; then
    # Measured 2026-10-20: three of the first four items of one run (#89, #99, #137) were items whose premise the
    # worker disproved, and each was escalated as a decision although the run held the number that settled it.
    # The empty tree is part of the evidence — a falsification that changed files is a change.
    outcome="falsified"
  elif [ "$verdict_claim" = "skipped" ]; then
    outcome="skipped"
  elif [ "$verdict_claim" = "blocked" ]; then
    # The verdict a tail writes when the gate did not pass (issue #744): it archived nothing, so the archived-change
    # arm below would record the opposite of what it reported — "reported success but no archived change" for a child
    # that said the gate failed — and the gate's own numbers would be dropped from the reason a person reads.
    block child-blocked "${root_cause:+$root_cause — }the item's verdict is blocked${gate_claim:+ — the gate reported $gate_claim}"
  elif [ -n "$phase_failure" ]; then
    # A phase that answered nothing is that phase's failure (issue #810). The arms below read a report the phase never
    # wrote — its empty verdict is neither `skipped` nor `blocked`, and its empty change claim fails beside a commit
    # the change child did make — so the reading taken in the phase decides here, and the reason names the phase a
    # person has to look at instead of the item's tree.
    block child-failed "$phase_failure"
  elif [ "$head_now" = "$base" ]; then
    block uncommitted "reported success but the tree is still at the base commit — nothing was committed$(item_outside_write_clause)"
  elif [ -z "$change" ]; then
    block uncommitted "reported success but no archived change appears in the run's diff"
  elif [ -n "$LOOP_GATE_CMD" ] && [ -n "$gate_claim" ] && [ "$gate_found" -eq 0 ]; then
    block unverified "the report claims a gate result but the gate's tally directory holds no XML (${gate_tally:-no LOOP_GATE_TALLY_DIR is named})"
  elif [ "$cited_evidence" -eq 1 ] && [ "$carried" -eq 0 ]; then
    block unverified "the change cites a test result but carries no evidence in its own evidence/ directory"
  fi
  # …and a phase the run's own bound ended is not a block at all (issues #785, #790): a review round the run cut
  # leaves an item whose work finished — its change archived, its gate answered and its branch carrying the commits
  # — and a child the run ended at a bound leaves an item whose tree was left mid-phase. Either way what remains is
  # a phase the next run resumes rather than a finding about the item, so the result is the fifth one and the reason
  # the arms above recorded is kept word for word — it is what the report and the state's own table print. The bound
  # is read off the mechanism the non-zero-exit arm wrote instead of the exit status a second time: `run_child`
  # reports `LOOP_CHILD_TIMEOUT` and the child's own `LOOP-TIMEOUT` line for every bound it enforces — the child
  # clock, the run's deadline and the role's turns — and that arm turns all three into the one word `bound`.
  if [ "$pre_gate_cut" -eq 1 ] || [ "$stop_reason" = bound ]; then outcome="cut"; fi
  # Which bound the item's own class was cut at travels beside the class (issue #923): `bound` alone says the run ended
  # the item and not what it had spent, so the archive could be totalled by the class and never by the key — six items
  # a bound ended, and no figure for any of them. The key and the figure are read from the same `LOOP-TIMEOUT` line the
  # reason quotes, and an item whose clock, the run's deadline or a vanished writer ended it gets neither: none of the
  # three is a value `loop.conf` records, and a figure guessed from the value it usually sits beside would read as a
  # measurement. A round the run ended before the gate is read here like any other cut — its reason quotes the bound's
  # own line — and one it ends after the gate is read below, where the round's fields are known (the run's item #898).
  bound_key=""
  bound_value=""
  if [ "$stop_reason" = bound ]; then
    bound_line=$(bound_key_value "${blocked_reason:-}")
    [ -n "$bound_line" ] || bound_line=$(bound_key_value "${timeout_claim:-}")
    bound_key="${bound_line%% *}"
    bound_value="${bound_line##* }"
  fi
  # Both records are the cut's own (`--stop-reason bound`, or the round that ended it), so they travel together to the
  # one write that can carry them: the blocked arm names a finding about the item, and no bound is a finding. The array
  # is built at that write and not here (issue #923): the review rounds run *after* this block, so the round's ending
  # is unknown until the terminal arm below.
  bound_args=()
  [ -z "$bound_key" ] || bound_args+=(--bound-key "$bound_key" --bound-value "$bound_value")
  # The silence over a red gate is refused **here** (issue #811): the outcome is decided above, and this is the last
  # moment before the branch is merged and the item closed on the tracker. An item whose tally carries a nonzero
  # `failures=` or `errors=` and whose child recorded no waiver takes the blocked path every other refusal takes —
  # rather than the close, which would fail the write that records it after the tracker was already told the item
  # closed. `state.sh done` keeps the same refusal as its own backstop for a caller that is not this script, so the
  # clause is refused, never defaulted, in both places.
  # Which tree fails the case the red tally names (issue #853) is taken here, where both halves are in hand, and since
  # issue #861 it is taken **before** the waiver refusal below: `flaked` is the one answer that stands in the waiver's
  # place, so the refusal is the line that has to read it. For `delta`, `base` and `unattributable` it changes no
  # outcome — the answer is what makes the block readable, not a verdict on a gate that did not pass (§4d).
  gate_attribution=""
  [ "$(gate_tally_state "$gate_field")" = red ] &&
    gate_attribution=$(attribute_red_gate "$gate_field" "$gate_tally_dir" "$worktree" "$base")
  attribution_args=()
  [ -z "$gate_attribution" ] || attribution_args+=(--gate-attribution "$gate_attribution")
  # …and the refusal itself reads that answer (issue #861): a case that passes when it is run once more in the item's
  # own worktree is not the delta's red, so it is not the item's block either — the record keeps the tally and the
  # `flaked` answer that stands where a waiver clause would, and the item closes with no clause to name.
  if [ "$outcome" = "closed" ] && [ "$(gate_tally_state "$gate_field")" = red ] && [ -z "$gate_waived" ] \
    && [ "$gate_attribution" != "flaked" ]; then
    block refused "the gate did not pass ($gate_field) and the close records no waiver — LOOP-GATE-WAIVED is the one clause the item closed over, and a close without it is a judgement nobody can read"
  fi
  # The same refusal has a reverse side, and it landed in the same place (issue #811): `state.sh done` refuses a
  # clause written beside a tally that *passed*, and a suite written with no clause to name, and both of those fields
  # come from the child's own report. Left to travel they would fail the closed arm's state write — after the tracker
  # was closed — so they are dropped here, where the outcome is still the run's to decide: a clause for a gate that
  # passed waives nothing, and a suite with no clause names nothing.
  if [ -n "$gate_waived" ] && [ "$(gate_tally_state "$gate_field")" = green ]; then
    gate_waived=""
    gate_waived_suite=""
  fi
  if [ -n "$gate_waived_suite" ] && [ -z "$gate_waived" ]; then gate_waived_suite=""; fi
  # The attribution the waiver refusal reads is taken above, before that refusal (issue #861).

  review_rounds=0
  review_verdict="${pre_gate_refusal_verdict:-off}"
  # The round that read the diff is one this run read and paid for, and it is not a round `--max-review-rounds` bounds
  # (issue #813): when it answered and no refusal took precedence, the record carries its verdict and its count, so the
  # report's review line counts it instead of printing the item as one no round was read for.
  if [ -z "$pre_gate_refusal_verdict" ] && [ -n "$pre_gate_verdict" ]; then
    review_verdict="$pre_gate_verdict"
    review_rounds=1
  fi
  review_notes_text=""
  # The report the round is decided from: the reviewer's answer, or the retry's when the first one arrived cut.
  reviewer_report=""
  if { [ "$outcome" = "closed" ] || [ "$outcome" = "falsified" ]; } && [ "$review" = "on" ]; then
    review_verdict="FAIL"
    round=1
    # The round budget is the recorded one (`--max-review-rounds`, or the config's default), and one
    # corrective refusal extends it by a round: a report that asks only for deletions, corrected pointers or
    # narrower wording is answerable by one worker pass, and blocking it hands a person work the loop could
    # finish (#187). A second failed report — or a first one that adds a claim, a case or an assertion — still
    # blocks, which is the line the prompt draws.
    round_cap="$max_review_rounds"
    corrective_round_used=0
    # Why the last round took no round after it, where the budget is not the answer: a refusal that named no finding
    # ends the rounds itself (issue #1085), and the item's own text carries that reason instead of the budget's.
    refusal_named_nothing=""
    # Read once: the same pathspec the round's diff is built with is the one its prompt names.
    review_exclude_spec=$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)
    # The base a round's diff is taken from, and which is the run's own except for the round a corrective refusal
    # buys: that one is handed the correction round alone, from the refused round's HEAD (#245). `correction_scope`
    # is the section the same round's prompt carries instead of an empty placeholder.
    diff_base="$base"
    correction_scope=""
    while [ "$round" -le "$round_cap" ]; do
      review_rounds=$round
      diff_file="$item_dir/review-$round.diff"
      write_review_diff "$diff_base" "$diff_file"
      # The round's three parts, each capped (issue #924); the section is empty when none of them is over it.
      review_inputs=$(bound_review_inputs "$diff_file" "${pre_gate_report:-}")
      review_input_cut_scale=$(review_input_scale_of "$diff_file")
      before=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      reviewer_report="$item_dir/reviewer-report-$round.md"
      reviewer_prompt="$item_dir/reviewer-prompt-$round.md"
      # ── the round, and the round a bound cut restarted in this run (issue #918) ─────────────────────────────────
      #
      # The child below is started, read and then possibly started again, and only the three things a round's
      # attempts share are hoisted out of the loop: the attempt's own prompt and report names (`<round>.md`,
      # `<round>-retry.md`, `<round>-restart.md`, `<round>-restart-retry.md`, so `child_role` reads every one of them
      # as the reviewer's), the restart section the second reading is handed, and the flag that says the round has
      # nothing to carry below. The digest `before` is taken before the first attempt and read after the last, so a
      # write by any of them is the round's write and not only the final child's.
      review_attempt=""
      review_restart_section=""
      review_replaced_report=""
      review_cut_twice=0
      review_restarts_used=0
      # The round is announced on the branch *before* the reviewer starts, so an answer that never arrives is a
      # fact a later run reads (#262): with only the answer written, a reviewer killed mid-answer left a branch
      # that read exactly like a passed one, and the next run adopted it. A round that cannot be announced stays
      # a round the run holds, and the warning names what a later run will read instead.
      if [ -n "$change" ]; then
        bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$round" requested >&2 ||
          loop_emit review-announce-unrecorded round="$round" change="$change" --stderr
      fi
      while :; do
        render_reviewer_prompt "$reviewer_prompt" "$correction_scope" "" "$review_inputs" "$review_restart_section"
        run_child "$reviewer_prompt" "$reviewer_report"
        # A reviewer is a child too, and the run's own deadline can end it as it can end the item's worker (issue
        # #674): the clock is the run's budget, so the round was cut rather than refused, and the item waits for
        # the next run instead of being asked about. Read here, before the retry, because a retry started past the
        # deadline is a second child the same clock would only have to end.
        reviewer_deadline=$(report_field "$reviewer_report" LOOP-DEADLINE 2>/dev/null || true)
        # An answer cut on its way to the file decides nothing, and it is not a reviewer that stayed quiet either
        # (#224): it is the run's own transport, so the round is asked once more instead of the item being blocked.
        # A round the run itself ended already says so in its report (`LOOP-TIMEOUT`), so only a silent cut is
        # retried — a timeout is read exactly as it was.
        if [ -z "$reviewer_deadline" ] && [ -z "$(report_field "$reviewer_report" LOOP-TIMEOUT)" ] &&
          ! answer_complete "$reviewer_report" '^VERDICT: (PASS|FAIL)'; then
          loop_emit review-answer-cut-retry round="$round" last="$(last_line "$reviewer_report")" --stderr
          review_replaced_report="$reviewer_report"
          reviewer_report="$item_dir/reviewer-report-$round$review_attempt-retry.md"
          render_reviewer_prompt "$item_dir/reviewer-prompt-$round$review_attempt-retry.md" "$correction_scope" "" \
            "$review_inputs" "$review_restart_section"
          run_child "$item_dir/reviewer-prompt-$round$review_attempt-retry.md" "$reviewer_report"
          reviewer_deadline=$(report_field "$reviewer_report" LOOP-DEADLINE 2>/dev/null || true)
          if [ -z "$reviewer_deadline" ] && [ -z "$(report_field "$reviewer_report" LOOP-TIMEOUT)" ] &&
            ! answer_complete "$reviewer_report" '^VERDICT: (PASS|FAIL)'; then
            # Twice is not a hiccup the run can wait out: the round has no verdict at all, so the block says that
            # instead of naming a refusal no reviewer made, and it records no failed review on the branch — a
            # later run must not be told the reviewer refused this change.
            review_verdict="cut-short"
            block unanswered "the reviewer's report was cut short twice — the round $round answer never arrived, so no reviewer refused this change"
            review_cut_twice=1
            break
          fi
        fi
        # A **bound** ended this round, and the round is what the bound ended — not the item (issue #918). The run
        # restarts the reading in a fresh session on the input the cap already bounded, hands it what the cut round
        # wrote, and only a second bound is the item's cut. `LOOP_REVIEW_RESTART_MAX` is the ceiling and the run's
        # own deadline is the other: a restart begun past the clock is ended on its first second and the reason a
        # reader gets would be the clock's rather than the bound's.
        reviewer_timeout_line=$(report_field "$reviewer_report" LOOP-TIMEOUT)
        if [ -n "$reviewer_timeout_line" ] && [ -z "$reviewer_deadline" ] &&
          [ "$review_restarts_used" -lt "$review_restart_max" ] && review_restart_before_deadline; then
          review_restarts_used=$((review_restarts_used + 1))
          review_restart_note "round $round" "$reviewer_timeout_line" "$reviewer_report"
          review_restart_section=$(review_restart_scope "$reviewer_report" "$reviewer_timeout_line" \
            "round $round after the gate" "${pre_gate_report:-}")
          progress note "#${issue} restarts review round $round in a fresh session: the run ended it at $reviewer_timeout_line, and the findings it had written are handed to the restart"
          review_replaced_report="$reviewer_report"
          reviewer_report="$item_dir/reviewer-report-$round-restart.md"
          reviewer_prompt="$item_dir/reviewer-prompt-$round-restart.md"
          review_attempt="-restart"
          continue
        fi
        break
      done
      if [ "$review_cut_twice" -eq 1 ]; then break; fi
      after=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      if [ "$read_only" = "on" ] && [ "$before" != "$after" ]; then
        # The reviewer wrote in the tree it reads, so it cannot have been the reviewer: the round is discarded (D3).
        review_verdict="reviewer-wrote"
        block reviewer-wrote "the review round was discarded: the working tree changed while the reviewer ran"
        break
      fi
      # The project's own tree is read as well, by the child's own run (the in-time reading), and it is not the
      # reviewer's either — but it is not only the reviewer's to change either (#274, #276, #285, #286): a write
      # the run can tie to the reviewer's own tree, or any difference in a run that declared the tree exclusive,
      # discards the round; anything else is recorded and the round stands.
      if outside_write_decides; then
        review_verdict="reviewer-wrote"
        block outside-write "the review round was discarded: the project's own tree changed while the reviewer ran — $outside_write_note${outside_write_attributed:+ (attributed to this round: $outside_write_attributed)}"
        break
      elif [ -n "$outside_write_note" ]; then
        loop_state step A --name outside-write-review-$round --status ok --note "$outside_write_note" >/dev/null 2>&1 || true
        progress note "$outside_write_note"
      fi
      # The two guards above run first, because a round the reviewer wrote through is discarded whatever ended the
      # child. Neither of them refuses the change here: the round was cut by the run's own clock rather than
      # answered (issue #674), so the item waits for the next run as a worker the same clock cut does — the claim
      # names the clock, and no decision label is written for a question the budget asked.
      if [ -n "$reviewer_deadline" ]; then
        deadline_claim="$reviewer_deadline"
        review_verdict="deadline"
        outcome="cut"
        # The run's own deadline is a bound it enforces, so the item carries the class a bound leaves on it — and no
        # key, because a clock is not a value `loop.conf` records (the run's item #898).
        stop_reason="bound"
        record_cut_round "$round"
        blocked_reason="the run's deadline passed while the reviewer of round $round was running — the round was cut, not refused, so the change is left for the next run"
        # …and, when this reading is the restart of a round the run had already cut, what both readings wrote (issue
        # #987). The restart was handed the first reading's findings in its prompt and wrote none of them down, so a
        # person who takes the kept patch has no account of the change at all unless this file exists. The reading this
        # attempt stands in place of is the one the run had in hand before it named this one — `<round>-restart.md`
        # when the restart's own answer was cut on its way to the file, the round's first attempt when it was not (issue
        # #1045) — and not a name derived from this report's.
        review_findings_file=$(review_findings_for_double_cut "$item_dir" "$reviewer_report" "$review_replaced_report" || true)
        break
      fi
      # The child's own turn bound ends a child the same way (`LOOP_CHILD_TURNS_MAX_<ROLE>`, issues #686 and #784), and
      # so does the item's context bound (`LOOP_ITEM_CONTEXT_MAX`, issue #760), and a round they ended has no answer to
      # read: the transcript carries `LOOP-TIMEOUT` and no `VERDICT:` line. Reading that silence as
      # a refusal named a reviewer that refused nothing and bought a rework worker for it — measured on item #51,
      # whose round 1 ended at the turn bound (250 turns then) and whose record the report would otherwise count as a refusal
      # (issue #247, review round 2). The round is cut, as the twice-cut path below records it, so the item waits
      # for the next run and a rate counts it as undecided. The reason it leaves names the bound and the figure it
      # reached, quoted from that same transcript line, so the two bounds are not offered in place of the one that
      # fired (issue #829).
      reviewer_timeout_line=$(report_field "$reviewer_report" LOOP-TIMEOUT)
      if [ -n "$reviewer_timeout_line" ]; then
        review_verdict="cut-short"
        outcome="cut"
        # The round the run ended is the item's own cut, with the class the bound leaves on it (the run's item #898): `bound`
        # reaches the record through the field a child's ending does, and the key is read from the round's fields below
        # — this write is the class, that one is the figure.
        stop_reason="bound"
        record_cut_round "$round"
        blocked_reason="the run ended the reviewer of round $round at one of its spend bounds — $reviewer_timeout_line — the round was cut, not refused, so the change is left for the next run"
        # The round's own ending, recorded where a reader can count it (issue #923): beside the class the item now
        # carries, the bound travels in the round's own field — its number, its key and the figure it reached.
        # …and what the two readings of a round the run cut twice wrote (issue #987), for the reason the arm above
        # states: the second bound is the item's cut, and what ended it leaves no answer to read. The reading this
        # attempt stands in place of is the caller's, for the reason the arm above states as well (issue #1045).
        review_findings_file=$(review_findings_for_double_cut "$item_dir" "$reviewer_report" "$review_replaced_report" || true)
        round_bound_round="$round"
        round_bound_line=$(bound_key_value "$reviewer_timeout_line")
        round_bound_key="${round_bound_line%% *}"
        round_bound_value="${round_bound_line##* }"
        break
      fi
      # The word on the verdict is not what decides: the report's blocking count is (#205). `VERDICT: PASS`
      # with a non-zero count contradicts itself and is read as a refusal, and a refusal with a zero count is
      # a pass whose notes are recorded — the findings are wording, pointers and naming, which cost the change
      # no round. A report with no count line keeps today's reading, so an old-shaped review never passes.
      blocking=$(blocking_count "$reviewer_report")
      if [ "$(report_verdict "$reviewer_report")" = PASS ] &&
        { [ -z "$blocking" ] || [ "$blocking" -eq 0 ]; }; then
        review_verdict="PASS"
      elif [ -n "$blocking" ] && [ "$blocking" -eq 0 ]; then
        review_verdict="NOTES"
      fi
      # A round that answered is written on the branch as the answer to the round it announced (#262), and the
      # answer *no* is written here beside it (#975). The refusal used to be written after the loop, where only the
      # last round is known, so a round that refused and was then repaired left its announcement and nothing else —
      # 62 of the archive's 156 first rounds could not say they had refused. The write the run makes when it ends on
      # a refusal stays (`record-review-failure` on the way out): it is the same record, and a round already written
      # here is not written a second time. See `worktree.sh`'s `record-review-failure`.
      if [ "$review_verdict" = "PASS" ] || [ "$review_verdict" = "NOTES" ]; then
        if [ -n "$change" ]; then
          bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$round" passed >&2 ||
            loop_emit review-answer-unrecorded round="$round" change="$change" --stderr
        fi
      elif [ -n "$change" ]; then
        bash "$LOOP_DIR/worktree.sh" record-review-failure "$issue" "$change" "$round" >&2 ||
          loop_emit review-refusal-unrecorded round="$round" change="$change" --stderr
      fi
      if [ "$review_verdict" = "PASS" ]; then
        break
      fi
      if [ "$review_verdict" = "NOTES" ]; then
        review_notes_text=$(review_notes "$reviewer_report")
        break
      fi
      # The round after a refusal is owed by a report that **named** a finding (issue #1085). The count above is the
      # reviewer's claim about its own grading; the `BLOCKING:` lines are the findings, and a finding is the one thing
      # a worker pass can act on. Every `VERDICT: FAIL` report in the archive carries one, at line start or wrapped in
      # the emphasis #1042's round 1-restart and #999's round 2 write theirs with, and the count says nothing about
      # whether the line is there. Measured over the archive: in the window from 2026-11-24 the reviewer is 12
      # sessions and 63.7 Mtok and 8 of those sessions end at exactly `LOOP_CHILD_TURNS_MAX_REVIEWER` turns with no
      # refusal in any of them, so the bound ends the reading and not the item. A refusal that names nothing is
      # therefore the item's last round: the refusal, the block and the escalation stay as they are, and the round
      # nobody owed is written on the item rather than paid for — a round that is skipped in silence is a lie about
      # the run.
      if [ "$(blocking_findings "$reviewer_report")" -eq 0 ]; then
        refusal_named_nothing="the round $round refused the change and named no finding — no BLOCKING: line for the count it states — so the run takes no round after it: a person reads the report and decides"
        break
      fi
      if [ "$round" -ge "$round_cap" ]; then
        # A corrective refusal earns one more round, once: a reviewer that says "nothing here adds a claim" has
        # answered the question its FAIL otherwise leaves open (#186, #187). That round exists only to decide the
        # findings the refusing round wrote, so it is handed the correction round — the refused round's own HEAD
        # as the diff's base — and the refusing report, rather than the whole change again (#245).
        corrections_only=$(report_field "$reviewer_report" LOOP-CORRECTIONS-ONLY)
        if [ "$corrective_round_used" -eq 0 ] && [ "$corrections_only" = "yes" ]; then
          corrective_round_used=1
          round_cap=$((round_cap + 1))
          diff_base=$(git -C "$worktree" rev-parse HEAD 2>/dev/null || printf '%s' "$base")
          correction_scope=$(review_correction_scope "$diff_base" "$reviewer_report")
        else
          break
        fi
      fi
      # FAIL: the same change goes back to a new worker process with the reviewer's findings. Its session runs the
      # gate itself (`gate_step_text off`): the round that refused it came *after* a gate, so there is nothing left to
      # read before one, and the round its repair buys is the next review round.
      #
      # What the append carries is the findings and not the report (issue #1088): the `BLOCKING:` lines are the finding
      # — the vocabulary a round after a refusal is owed by (#1085) — and each names the path or the line its refusal is
      # about, so the report's prose is not re-sent with every turn of the rework session. The report stays one
      # `ctx_read` away at the path printed beside them, so a finding the summary leaves out is read and not lost.
      findings=$(blocking_finding_lines "$reviewer_report")
      render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt-round2.md" \
        "GATE_STEP=$(gate_step_text off)" \
        "WORKER_REFERENCE=$item_dir/worker-reference.md" \
        "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
        "SCRATCH_DIR=$scratch_dir" \
        "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
        "CLASS=$class" "PROPOSE_CLASS=$propose_class"
      # The reference is the item's one copy, rendered beside the item's own prompt above: the rework round is the
      # same item reading the same rules, so it is handed that file rather than a second rendering of it (issue #1082).
      # The bound weighs the file the child is handed, so it is measured after the append (issue #1088): measured
      # before it, the largest prompt the run composes — the rework prompt and the refusal it carries — was the one
      # the bound could never fire on.
      printf '\n## The reviewer refused this change; change exactly these things\n\n%s\n\n## The report those findings were read from\n\n%s\n' \
        "$findings" "$reviewer_report" >> "$item_dir/worker-prompt-round2.md"
      worker_prompt_bound_check "$item_dir/worker-prompt-round2.md" worker-round2
      run_child "$item_dir/worker-prompt-round2.md" "$item_dir/worker-report-round2.md"
      round2_exit=$?
      # The rework round is split like the item's first one (issue #744): the repair a refusal buys ends at its
      # gate too, so its archive and evidence are written by a fresh child charged the baseline rather than by the
      # session that carried every finding the reviewer wrote.
      round2_tail_started=0
      round2_tail_exit=0
      round2_report="$item_dir/worker-report-round2.md"
      round2_verdict=$(report_field "$item_dir/worker-report-round2.md" LOOP-VERDICT)
      # The round-2 worker's own difference is snapshotted for the reason the item's worker's is (issue #744): the
      # tail child overwrites `outside_write_note`, so a rework worker that wrote into the project's tree would lose
      # the reading that decides its item, and the tail's write would be blamed on it.
      round2_write_note="$outside_write_note"
      round2_write_decides=0
      outside_write_decides && round2_write_decides=1
      if [ "$round2_exit" -eq 0 ] && [ "$round2_write_decides" -eq 0 ] &&
        { [ -z "$round2_verdict" ] || [ "$round2_verdict" = closed ]; } &&
        [ -z "$(report_field "$item_dir/worker-report-round2.md" LOOP-TIMEOUT)" ] &&
        [ -z "$(report_field "$item_dir/worker-report-round2.md" LOOP-DEADLINE)" ]; then
        tail_started=0
        tail_exit=0
        if tail_after_gate "$item_dir/worker-report-round2.md" "$item_dir/worker-tail-round2-prompt.md" \
          "$item_dir/worker-tail-report-round2.md"; then
          round2_tail_started="$tail_started"
          round2_tail_exit="$tail_exit"
        fi
      fi
      if [ "$round2_tail_started" -eq 1 ]; then
        round2_report="$item_dir/worker-tail-report-round2.md"
        round2_exit="$round2_tail_exit"
        loop_state step A --name worker-tail-round2 \
          --status "$(if [ "$round2_tail_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
          --note "exit $round2_tail_exit" >/dev/null
      fi
      outside_write=$(report_field "$round2_report" LOOP-OUTSIDE-WRITE)
      if [ "$round2_write_decides" -eq 1 ] && [ "$round2_tail_started" -eq 0 ]; then
        # The round-2 worker's own reading, which decided the item before its tail could have run.
        review_verdict="outside-write"
        block outside-write "the round-2 worker wrote outside the worktree — the project's own tree changed while it ran: $round2_write_note"
        break
      fi
      if outside_write_decides; then
        # Named for what it is: a write outside the worktree, not a second failed review round.
        review_verdict="outside-write"
        block outside-write "the round-2 tail worker wrote outside the worktree — the project's own tree changed while it ran: $outside_write_note${outside_write_attributed:+ (attributed to this child: $outside_write_attributed)}"
        break
      elif [ -n "$round2_write_note" ]; then
        # Recorded and not acted on, for the reason the worker's own difference is (#274, #276, #285, #286).
        loop_state step A --name outside-write-round-2 --status ok --note "$round2_write_note" >/dev/null 2>&1 || true
        progress note "$round2_write_note"
      fi
      if [ "$round2_tail_started" -eq 1 ] && [ -n "$outside_write_note" ] && ! outside_write_decides; then
        loop_state step A --name outside-write-round-2-tail --status ok --note "$outside_write_note" >/dev/null 2>&1 || true
        progress note "$outside_write_note"
      fi
      # The rule the item's own commit follows is the same one here (issue #271): the run commits a child's tree
      # only once that child has finished by its own exit. A round-2 worker the cap or the deadline ended is unfinished
      # the item's worker was — the tree is whatever it was doing, and a reviewer's refusal is not a licence to
      # assert work nobody claimed — so this commit is skipped too, the item is blocked with the round's own
      # transcript, and the tree stays as the child left it. Measured 2026-10-23 (the round-1 review of #271): the
      # shipped requirement was universal and this call site was left as it stood, so the sentence and the code
      # disagreed until the reviewer read them against each other.
      round2_timeout=$(report_field "$round2_report" LOOP-TIMEOUT)
      # …and the deadline's own line, because a rework worker the run's clock cut is the same clock's item as one
      # the item's own worker cut (issue #674).
      round2_deadline=$(report_field "$round2_report" LOOP-DEADLINE 2>/dev/null || true)
      if [ -n "$round2_deadline" ]; then deadline_claim="$round2_deadline"; fi
      if [ "$round2_exit" -eq "$LOOP_CHILD_TIMEOUT" ] || [ -n "$round2_timeout" ]; then
        review_verdict="round2-ended"
        # …and the result is the fifth one (issue #790): a bound the run enforced on its rework worker is the same
        # bound it enforces on the item's own, so what it leaves is a phase the next run resumes rather than a
        # finding about the item. The mechanism rides beside it, so the spend a bound caused is one field on either
        # result (issues #786, #790).
        outcome="cut"
        stop_reason="bound"
        # …and the branch says it too (issue #788): the round's refusal stays on it, and this record beside the
        # announcement is what stops the next pre-screen reading the item as a question the run asked itself.
        record_rework_cut "$round"
        blocked_reason="the run ended the rework worker of the refusing round (its wall-clock or the run's own deadline), so its tree is not committed — ${round2_timeout:-the child exceeded its wall clock}"
        break
      fi
      bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" >/dev/null 2>&1 || true
      write_review_diff "$diff_base" "$item_dir/review-$((round + 1)).diff"
      round=$((round + 1))
    done
    if [ "$review_verdict" = "FAIL" ]; then
      # What ended the rounds is the item's reason: the budget, or the last refusal naming nothing (issue #1085).
      block refused "${refusal_named_nothing:-the change failed its last review round ($review_rounds of $round_cap) — a decision is needed, not another attempt}"
      # The verdict has to outlive this run and travel with the change: the branch is what a later run adopts
      # from, and its archived change plus green XML say nothing about a reviewer that refused it (#185). A
      # falsification has no change to record the verdict against — its tree did not move — so the branch keeps
      # no review marker and `worktree.sh` is not asked to write one. The round's announcement is already on the
      # branch (#262), so the refusal answers a round a later run can see was asked.
      if [ -n "$change" ]; then
        bash "$LOOP_DIR/worktree.sh" record-review-failure "$issue" "$change" "$review_rounds" >&2 ||
          loop_emit review-failed-unrecorded --stderr
      fi
    fi
  fi

  # A falsification claims that the tree was only measured. The review round can run a corrective worker, and
  # that worker may commit — so the claim is read again after the review instead of before it: a falsified item
  # whose tree moved is a change, and closing it as mis-diagnosed would be a closure on evidence the reviewer
  # never saw (a reviewer's refusal is exactly the case where the report is the whole claim).
  if [ "$outcome" = "falsified" ]; then
    head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
    if [ "$head_now" != "$base" ]; then
      block contradicted "the falsification claimed an unchanged tree, but the tree moved during its review — a change exists, so the item is not mis-diagnosed"
    fi
  fi

  # A finished item is not closed until the fix is in the tree: the branch the run committed on is merged onto
  # the base here, and a merge the run cannot take leaves the item blocked with its branch (#229). This runs
  # before the outcome is written, so a refusal takes the same blocked path every other refusal takes.
  merged_branch=""
  merged_commits=""
  merged_sha=""
  if [ "$outcome" = "closed" ]; then
    merge_committed_branch "$issue" "fix/issue-$issue-$slug" || {
      block unmergeable "the branch fix/issue-$issue-$slug could not be merged onto the base, so the item is not closed on a fix the tree does not have — $merge_refusal"
    }
    # The branch the run committed for this item is done with once the run merged it: the work is in the
    # base, and the branch is the run's own leftover. The release frees the worktree from it and the
    # deletion is git's safe one, which refuses a branch whose work is not in the base.
    if [ "$outcome" = "closed" ]; then
      bash "$LOOP_DIR/worktree.sh" release-branch "fix/issue-$issue-$slug" >&2 || true
    fi
  fi

  # The wall time is recorded in milliseconds-derived seconds: a stub run finishes inside one second, and
  # a "positive duration" that rounds to zero would make the metric unassertable (D11).
  wall_ms=$(( $(date +%s%3N) - started ))
  wall="$((wall_ms / 1000)).$(printf '%03d' $((wall_ms % 1000)))"
  # A bound that ended a review round after the gate is the item's own class, and its key travels beside it (issue
  # #898): those rounds run after the block above, where the class and its key are derived, so the round's own fields
  # are read here for the two the record keeps. A round the run's deadline ended carries no key — a clock is not a
  # value `loop.conf` records — and keeps the class alone, as the item's own child does.
  if [ "$outcome" = cut ] && [ "$stop_reason" = bound ] && [ -z "$bound_key" ] && [ -n "$round_bound_key" ]; then
    bound_key="$round_bound_key"
    bound_value="$round_bound_value"
    bound_args+=(--bound-key "$bound_key" --bound-value "$bound_value")
  fi
  closure="no"
  case "$outcome" in
    closed)
      evidence_paths="$item_dir, $worktree/openspec/changes/archive/$change, ${gate_xmls:-no gate evidence}"
      [ -z "$merged_branch" ] || evidence_paths="$evidence_paths, merged $merged_branch (commits $merged_commits) onto the base"
      close_reason="${root_cause:-closed by the run}"
      note_field=""
      if [ -n "$review_notes_text" ]; then
        # A refusal that blocked nothing carries its notes into the tracker, in the closure comment and in the
        # state: the round would otherwise have cost the item a decision and lost what the reviewer found (#205).
        note_field="round $review_rounds, 0 blocking finding:\n$review_notes_text"
        close_reason="$close_reason

**Review notes** (recorded, not blocking):
$review_notes_text"
      fi
      close_args=("$issue" --reason "$close_reason" --evidence "$evidence_paths")
      # The commit the landing made, for the same reason (#715): `merged_sha` is the base re-read after the merge.
      [ -z "$merged_sha" ] || close_args+=(--landed "$merged_sha")
      if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi
      if loop_labels close "${close_args[@]}"; then closure="$(if [ "$may_close" = yes ]; then printf yes; else printf skipped; fi)"; fi
      # The waiver travels only when the child recorded one: `--gate-waived` is refused beside a gate that passed,
      # so an arm that always passed it would refuse every green close (issue #811).
      waived_args=()
      [ -z "$gate_waived" ] || waived_args+=(--gate-waived "$gate_waived")
      [ -z "$gate_waived_suite" ] || waived_args+=(--gate-waived-suite "$gate_waived_suite")
      loop_state done "$issue" --result done --verdict closed --change "$change" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" --gate "$gate_field" \
        ${waived_args[@]+"${waived_args[@]}"} ${attribution_args[@]+"${attribution_args[@]}"} \
        --closed "$closure" --root-cause "$root_cause" --red-test "$red_test" \
        --merged-branch "$merged_branch" --merged-commits "$merged_commits" --merged-sha "$merged_sha" \
        --events "$events_file" --note "$note_field" --review-verdict "$review_verdict" \
        --review-input "$review_input_cut_scale" --review-restart "$review_restart_line" >/dev/null
      score_landings
      ;;
    falsified)
      # The run holds the evidence that settles the item: what it blames was measured and does not hold. That is a
      # finding, not a question, so the item is closed as mis-diagnosed — the same two artifacts a fix closure
      # posts (a reason, then the evidence), with the settled state a decision not to act records. With closure
      # disabled the same comment is posted and the item stays open, and no decision label is written either
      # way: the run already answered the item it would have asked about.
      falsified_reason="mis-diagnosed: the named cause does not hold — $falsified_mechanism

**Measured:** $falsified_measurement"
      falsified_evidence="$item_dir, $worktree unchanged at $base, review round $review_rounds"
      loop_labels unpick "$issue" >&2 || true
      closure="skipped"
      if [ "$may_close" = yes ]; then
        if loop_issue_tool close.sh "$issue" --reason "$falsified_reason" --evidence "$falsified_evidence" \
          --not-planned >&2; then closure="yes"; fi
      else
        loop_issue_tool close.sh "$issue" --reason "$falsified_reason" --evidence "$falsified_evidence" \
          --not-planned --comment-only >&2 || true
      fi
      loop_state done "$issue" --result falsified --verdict falsified \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" --closed "$closure" \
        --gate "$gate_field" \
        --root-cause "$falsified_mechanism" --red-test "$falsified_measurement" \
        --events "$events_file" --note "the premise does not hold" --review-verdict "$review_verdict" \
        --review-input "$review_input_cut_scale" --review-restart "$review_restart_line" >/dev/null
      ;;
    skipped)
      # The in-progress mark comes off on every exit path (labels.sh, header): an item a run skipped is no
      # more in progress than one it blocked, and an item that carries `agent/in-progress` while nothing is
      # running misreports the tracker to whoever reads it next.
      loop_labels unpick "$issue" >&2 || true   # the exit path clears the mark
      escalated_label=""
      if [ "$skip_verdict" = "$decision_agent" ] || [ "$skip_verdict" = "needs-diagnosis" ] \
        || [ "$skip_verdict" = "falsified" ] || [ "$skip_verdict" = "stale" ]; then
        # Escalation, not abandonment: the work already done is named on the item (spec, D9), and so is the
        # question — a worker that reached a decision states it in LOOP-DECISION, and an escalation that
        # carries a verdict without one leaves the owner a state nobody can answer (#194). The worker's
        # verdict is not the label written — a skip on `needs-diagnosis` writes the decision label — so the
        # label is recorded with the item, which is what the report reads (issue #177).
        escalate_reason="${root_cause:-$skip_verdict}"
        if [ -n "$worker_decision" ]; then
          escalate_reason="$escalate_reason

**The decision:** $worker_decision"
        elif [ "$skip_verdict" = "stale" ]; then
          # A stale skip is the one verdict that says nothing is left to do, and it carries neither the
          # measurement nor the review the falsification closure requires (`falsified-items-close-in-the-loop`).
          # It is therefore a question, and the bare hold it used to write stated none: the pre-screen reports
          # such an item as held — neither work nor a decision — for good (issue #682, measured on #147). The
          # verdict leaves one two-way choice, so the run states it with its own finding as the ground.
          escalate_reason="$escalate_reason

**The decision:** retire the item as stale, or re-diagnose it — ${root_cause:-the claim no longer holds in the tree}. Retiring it costs nothing, because the tree does not hold the claim any more; re-diagnosing costs another item's attempt and may find the defect the claim states wrongly."
        fi
        if loop_labels escalate "$issue" --reason "$escalate_reason" \
          --evidence "$item_dir, branch fix/issue-$issue-$slug, red test: ${red_test:-not reached}" >&2; then
          escalated_label="$decision_label"   # the skip's own escalation
        fi
      else
        # A hold is not a decision, but it must still say why it was taken (#192): the verdict and the one
        # clause that produced it travel with the state as a comment on the item.
        loop_labels "${skip_verdict:-unspecifiable}" "$issue" \
          --reason "${root_cause:-$skip_verdict}" \
          --evidence "$item_dir, branch fix/issue-$issue-$slug, red test: ${red_test:-not reached}" >&2 || true
      fi
      # The note is what `report.sh` prints on its `skipped:` line, so it carries the child's own sentence rather
      # than a constant beside it (issue #854): the run of 2026-11-11T11:37:08Z printed
      # `#838 too-wide (skipped by the worker)` and `#844 stale (skipped by the worker)` over two children that had
      # written a paragraph each, so 441 s and 5.2 Mtok of that run had to be re-opened by hand to answer the two
      # `decide` lines. The constant stays as the fallback for a child that gave no reason.
      loop_state done "$issue" --result skipped --verdict "${skip_verdict:-unspecified}" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --gate "$gate_field" \
        --root-cause "$root_cause" --red-test "$red_test" --note "${root_cause:-skipped by the worker}" \
        --events "$events_file" --decision-label "$escalated_label" --review-verdict "$review_verdict" \
        --review-input "$review_input_cut_scale" --review-restart "$review_restart_line" >/dev/null
      ;;
    cut)
      # The round the run's own bound ended (issue #785): the item's work finished — its change is archived, its gate
      # answered and its branch carries the commits — and the phase that did not finish is the one the next run
      # resumes. Nothing here writes a decision label: a person owes this item no answer, and a block recorded as one
      # spent a decision slot and an evening on #780, #783, #767 and #763 on 2026-11-05. `state.sh done --result cut`
      # keeps it out of the two-blocked streak `next` stops on, so a run of bound-ended rounds ends on its budget
      # rather than on its own bounds.
      label_failures=""
      loop_labels unpick "$issue" >&2 || label_failures="unpick"
      # The queue's own pass is not taken after this item: the branch it leaves carries the commits the run made, and a
      # pre-screen asking about them would name the item the run has just recorded — with a question the run answered.
      bound_cut=1
      cut_note="${blocked_reason:-the run ended the phase at a bound}"
      # A child the run ended says in the item's own note what became of the attempt (issues #786, #790): the tree it
      # left was mid-phase, so nothing about the item was measured, and a reader of the state — the next run's
      # pre-screen among them — must not have to derive that from the absence of a label.
      #
      # The one cut that is a size finding (issue #1039): the item's *own worker* ended at the turn bound that worker
      # role enforces, with nothing merged. Nothing was left mid-phase — the tree is still at the base, so the next run
      # has nothing to resume and pays the same bound on the same item (#1021 three times, #984 twice) — so the finding
      # is the item's shape, recorded as the context ceiling already records it: the shaping verdict `too-wide`, whose
      # hold and shaping label keep the item out of the queue until a person reshapes it. The key is the one the child's
      # own `LOOP-TIMEOUT` line carried, read above; every other bound — the child clock, the run's deadline, a review
      # round's turn bound, the gate child's, the tail's, the rework worker's — keeps that sentence and the plain unpick,
      # because each of those leaves a phase the next run resumes at the size it was. The two sentences are exclusive:
      # a note that said both that the item is held and that it is the next run's to resume is the state this issue
      # answers, so the size finding's sentence takes the place of the phase one rather than standing beside it.
      if [ "$stop_reason" = bound ] && [ "$bound_key" = LOOP_CHILD_TURNS_MAX_WORKER ]; then
        cut_note="$cut_note · the run ended this attempt at the worker's own turn bound with nothing merged, so the item is held as too-wide for a person to reshape rather than handed to the next run"
        if ! loop_labels too-wide "$issue" \
          --reason "the item's own worker reached the worker role's turn bound of ${bound_value:-?} turn(s) with nothing merged, so the item does not fit one child: the tree it leaves is the base, and the next run would spend the same bound on it" \
          --evidence "$item_dir, branch fix/issue-$issue-$slug, red test: ${red_test:-not reached}" >&2; then
          label_failures="${label_failures:+$label_failures and }the shaping hold"
        fi
      elif [ "$stop_reason" = bound ]; then
        cut_note="$cut_note · the run ended this attempt at a bound it enforces, so the item is left for the next run rather than asked about"
      fi
      if [ -n "$leftover_patch" ]; then
        cut_note="$cut_note · its work is kept at $leftover_patch"
      fi
      # The round the run cut twice wrote findings, and #918's restart handed them to one attempt's **prompt** and
      # put none of them down (issue #987): both readings' `BLOCKING:`/`NOTE:` lines are kept in one file beside the
      # item, and the note names it — so the report's `resume #<item>` line, the only line a caller gets about a cut
      # item, carries it too and the patch kept beside the item is resumed from what two rounds found.
      if [ -n "$review_findings_file" ]; then
        cut_note="$cut_note · the findings of both readings of the round the run cut twice are kept at $review_findings_file"
      fi
      if [ -n "$label_failures" ]; then
        cut_note="$cut_note · the run could not write $label_failures to the tracker, so the item's state there is this run's account only"
      fi
      if [ -n "$round_bound_key" ]; then
        bound_args+=(--round-bound "$round_bound_round" --round-bound-key "$round_bound_key" --round-bound-value "$round_bound_value")
      fi
      # The work this cut keeps is gated before the item ends (issue #885): the tree is still the one the ended child
      # wrote to, so this is the only moment the gate can measure that work on the base it was made against, and the
      # caller's resume action then carries a result instead of a patch nobody has run anything against.
      gate_the_kept_work "$issue" "$item_dir"
      cut_gate_args=()
      if [ -n "$cut_gate_result" ]; then
        cut_note="$cut_note · the gate ran on the work it kept and $cut_gate_result — $cut_gate_log"
        cut_gate_args+=(--cut-gate "$cut_gate_log" --cut-gate-result "$cut_gate_result")
        progress note "#${issue} is cut with its work kept, so its gate ran on that tree before the item ends: $cut_gate_result — $cut_gate_log"
      fi
      loop_state done "$issue" --result cut --verdict cut --change "$change_claim" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --gate "$gate_field" ${attribution_args[@]+"${attribution_args[@]}"} \
        --root-cause "$root_cause" --red-test "$red_test" --note "$cut_note" \
        --events "$events_file" --leftover "$leftover_patch" \
        ${cut_gate_args[@]+"${cut_gate_args[@]}"} \
        --stop-reason "$stop_reason" ${bound_args[@]+"${bound_args[@]}"} \
        --label-failures "$label_failures" \
        --review-verdict "$review_verdict" --review-input "$review_input_cut_scale" --review-restart "$review_restart_line" >/dev/null
      ;;
    *)
      # A label write the tracker refuses is a fact of the run, not a swallow (issue #279): measured
      # 2026-10-23T11:38Z, a run recorded item #260 blocked with `escalatedLabel: ""` while its six neighbours
      # carried the decision label, and left `agent/in-progress` on an item nothing was working any more — so the
      # decision list did not hold it, the cap did not count it, and the next attempt was told nothing. The run
      # clears its own `in_progress` variable on this path, so no later step retries the write: the transition that
      # failed is recorded with the item, and the report names what the tracker does not carry.
      label_failures=""
      loop_labels unpick "$issue" >&2 || label_failures="unpick"
      # A blocked item is escalated in the same pass, exactly as a skipped one is: the reason is held in the
      # run state, and the next run replaces that state wholesale, so a block that left the tracker untouched
      # would vanish — the decision list would not hold it, the decision cap would not count it, and the next
      # attempt would be told nothing (issue #174). The escalation writes the label, the status and the comment
      # that names the reason and the work already done, and the label it wrote is recorded with the item, so
      # the report names it as the run's own escalation (issue #177).
      escalated_label=""
      # The block's escalation carries the decision itself, not only the news that one is needed: the reviewer
      # already answered the question when it wrote its report, so the owner reads the options and the
      # recommendation instead of re-deriving them (#186). Every block that reaches this arm is escalated: the two
      # causes that used to be excused here — the run's deadline and a bound — leave a `cut` above (issues #674,
      # #786, #790), so a block nobody asked about was the one thing this arm could still produce.
      escalation_reason="${blocked_reason:-the item was blocked}"
      decision_field=$(report_field "${reviewer_report:-/dev/null}" LOOP-DECISION 2>/dev/null || true)
      # A block that ended before any review round has no reviewer to quote, and the fork is not therefore absent: the
      # worker writes it in `LOOP-DECISION` for exactly this verdict (`prompts/worker.md`, "…or a blocked item"), and
      # it is read here beside the reviewer's. Measured on #894 (2026-11-17): three options and a recommendation sat in
      # the child's report while the item asked "retry the item, or abandon it" — and the fork was readable only by
      # opening an evidence directory a person does not have. The run's own sentence is the last resort, not the rule.
      [ -n "$decision_field" ] || decision_field="$worker_decision"
      if [ -n "$decision_field" ]; then
        escalation_reason="$escalation_reason

**The decision:** $decision_field"
      else
        # A block that holds no question at all — no reviewer's, no worker's — is most often the run's own child
        # rather than the item (a claim with no commit, a timeout, a child that could not start). Escalating the reason
        # alone put an entry in the decision queue that nobody can answer, and the ceiling counts labels (issues #194,
        # #225). The block leaves exactly one two-way choice, so the run states it, with the reason it holds as the
        # ground for either answer.
        escalation_reason="$escalation_reason

**The decision:** retry the item, or abandon it — $blocked_reason. The reason above is the run's finding, not a
question the item asked; a retry costs another attempt, and an abandonment removes it from the loop."
      fi
      if loop_labels escalate "$issue" --reason "$escalation_reason" \
        --evidence "$item_dir, branch fix/issue-$issue-$slug" >&2; then
        escalated_label="$decision_label"   # the block's own escalation
      else
        label_failures="${label_failures:+$label_failures }escalate"
      fi
      # The item's own reason carries the failure too, so the state's own table (`state.sh report`) names an item
      # whose decision was never written, and not only the report's action list does.
      blocked_note="${blocked_reason:-the item was blocked}"
      # A bound-ended attempt says the same sentence on the `cut` arm above (issues #786, #790): the one block
      # whose cause was the run's own bound is written there now, so nothing here has to tell it apart.
      # The work an ended child left is named in the item's own record, not only in its transcript: a clock-cut
      # item writes no comment on the tracker, so the state and the report are the only places a caller reads it.
      if [ -n "$leftover_patch" ]; then
        blocked_note="$blocked_note · its work is kept at $leftover_patch"
      fi
      if [ -n "$label_failures" ]; then
        blocked_note="$blocked_note · the run could not write $label_failures to the tracker, so the item's state there is this run's account only"
      fi
      # The landing's refusal, recorded where the run knows it (issue #883): the block's note quotes the refusal,
      # and the report prints the landing from `mergeRefused` instead of reading the branch and the paths back out
      # of that note. The fields travel only from the arm that has them, so no other block records a refusal.
      refused_args=()
      if [ -n "$merge_refused_branch" ]; then
        refused_args+=(--merge-refused-branch "$merge_refused_branch")
        for refused_path in ${merge_refused_paths[@]+"${merge_refused_paths[@]}"}; do
          refused_args+=(--merge-refused-path "$refused_path")
        done
      fi
      loop_state done "$issue" --result blocked --verdict blocked --change "$change_claim" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --gate "$gate_field" ${attribution_args[@]+"${attribution_args[@]}"} \
        ${refused_args[@]+"${refused_args[@]}"} \
        --root-cause "$root_cause" --red-test "$red_test" --note "$blocked_note" \
        --events "$events_file" --decision-label "$escalated_label" --leftover "$leftover_patch" \
        --label-failures "$label_failures" --stop-reason "$stop_reason" \
        --review-verdict "$review_verdict" --review-input "$review_input_cut_scale" --review-restart "$review_restart_line" >/dev/null
      # The refusal belongs to the item it refused: cleared here so the next item's block cannot record a landing that
      # was never asked of it.
      merge_refused_branch=""
      merge_refused_paths=()
      ;;
  esac
  detail="${change:-$blocked_reason}"
  if [ "$outcome" = skipped ]; then detail="${skip_verdict:-unspecified}"; fi
  # A cut names the phase the run ended rather than the change it left: the change is the same one the item's branch
  # carries either way, and what a reader of the line has to tell apart is a cut from a block (issue #785).
  if [ "$outcome" = cut ]; then detail="${blocked_reason:-cut at a bound}"; fi
  loop_emit item-result issue="$issue" result="$outcome" detail="$detail" wall="$wall" rounds="$review_rounds"
  in_progress=""

  # The base a landing was merged into is the tree the next reader's CI measures, and `--push yes` promises to
  # publish: the item's branch and the base it landed on leave the run together, so `origin/main` stops standing
  # where a person's last push left it (issue #1018). The base goes through the same function, so a base that fails
  # is one named line and no retry.
  #
  # A push the remote refused is read here and nowhere else (issue #1027): the function returns a status, and the
  # refusal reaches the record beside the landing's sha. Which is why the loop names both refs rather than calling
  # the function twice: a refusal has to be written with the head the push tried to publish and what the remote's
  # tracking ref names instead, and the base is not an item's — its refusal lands on the run.
  if [ "$push" = yes ]; then
    for publish_ref in "fix/issue-$issue-$slug" "$(loop_base_ref)"; do
      push_branch "$publish_ref" && continue
      publish_issue="$issue"
      case "$publish_ref" in fix/issue-*) ;; *) publish_issue="-" ;; esac
      loop_state publish-refused "$publish_issue" --branch "$publish_ref" \
        --sha "$(git -C "$worktree" rev-parse --verify --quiet "refs/heads/$publish_ref" || true)" \
        --remote "$(git -C "$worktree" rev-parse --verify --quiet "refs/remotes/origin/$publish_ref" || true)" \
        >/dev/null || loop_note "the refusal of $publish_ref could not be recorded in the run's state"
    done
  fi
done

# ── the run's end ───────────────────────────────────────────────────────────────────────────────

end_run
exit "$LOOP_OK"
