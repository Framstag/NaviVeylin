#!/usr/bin/env bash
# run.sh — one command starts a budgeted, unattended run. No dialogue, no session, no question.
#
# This script is the run's only writer and its only git actor: it takes the lock, owns the state, creates
# the worktree and the branches, commits each step and writes every label. The agent processes it starts
# get a prepared work package and a working directory, and nothing else — not a budget, not a queue, not
# a tracker credential, and never a decision about whether an item closed (design D2, D3, D7, D12).
#
# A script cannot ask, so every budget is a flag whose default is recorded in `loop.conf`, and the run
# stops on its own authority: the item cap, the deadline, an empty queue, two consecutive blocked items,
# or a remaining time the next item cannot be paid for. The stop conditions are `state.sh next`'s, so the
# run never does its own budget arithmetic (invariant 3).
#
# Exit: 0 the run ended (including "nothing eligible"), 2 usage/malformed input, 3 stop (unauthorised
#       identity, or nothing eligible), 5 refuse (another run holds the lock, the decision cap is
#       reached, the base has moved, the kept worktree carries a previous run's work).
#
# The run records the one condition that ended it (`state.sh stop`), so the printed report and the state
# file state the same end (#184).
#
# Usage: run.sh [--items N] [--minutes M] [--review on|off] [--may-close yes|no] [--push yes|no]
#               [--max-decisions N] [--max-review-rounds N] [--review-read-only on|off] [--dry-run]
#               [--clean-worktree] [--reset-worktree] [--base <sha>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

items=""
minutes=""
review=""
may_close=""
max_decisions=""
max_review_rounds=""
push="no"
read_only="on"
dry_run=0
clean_worktree=0
reset_worktree=0
base=""
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
    --base) need_value "$@"; base="${2:-}"; shift 2 ;;
    --dry-run) dry_run=1; shift ;;
    --clean-worktree) clean_worktree=1; shift ;;
    --reset-worktree) reset_worktree=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) loop_die "run: unknown argument $1" ;;
  esac
done

loop_resolve
loop_roles_load
# The resolved paths and the configuration go to every child, so all of them agree on which run and which
# project they belong to.
export LOOP_STATE_FILE="$LOOP_STATE_FILE" LOOP_WORKTREE="$LOOP_WORKTREE"
export LOOP_CONF="${LOOP_CONF:-$LOOP_DIR/loop.conf}"

# The measuring command the worker is handed (issue #246). A project that configures none still renders a task,
# so the placeholder carries a sentence instead of an empty span, and no render site reads the key directly.
measure_cmd="${LOOP_MEASURE_CMD:-}"
[ -n "$measure_cmd" ] || measure_cmd="(this project configures no measuring command)"

: "${items:=$(loop_conf_value LOOP_ITEMS_DEFAULT)}"
: "${minutes:=$(loop_conf_value LOOP_MINUTES_DEFAULT)}"
: "${review:=$(loop_conf_value LOOP_REVIEW_DEFAULT)}"
: "${may_close:=$(loop_conf_value LOOP_MAY_CLOSE_DEFAULT)}"
: "${max_decisions:=$(loop_conf_value LOOP_MAX_DECISIONS_DEFAULT)}"
: "${max_review_rounds:=$(loop_conf_value LOOP_MAX_REVIEW_ROUNDS_DEFAULT)}"

for pair in "items=$items" "minutes=$minutes" "max-decisions=$max_decisions"; do
  case "${pair#*=}" in ''|*[!0-9]*) loop_die "run: ${pair%%=*} must be a non-negative integer" ;; esac
done
# Zero rounds is not a cheap review, it is a review that never runs: the item would be blocked on a round that
# never happened, and the branch would carry a `review-failed-round-0` that refuses it for ever.
case "$max_review_rounds" in ''|*[!0-9]*|0) loop_die "run: --max-review-rounds must be a positive integer" ;; esac
case "$review" in on|off) ;; *) loop_die "run: --review must be on or off" ;; esac
case "$may_close" in yes|no) ;; *) loop_die "run: --may-close must be yes or no" ;; esac
case "$push" in yes|no) ;; *) loop_die "run: --push must be yes or no" ;; esac
case "$read_only" in on|off) ;; *) loop_die "run: --review-read-only must be on or off" ;; esac

# The one indirection through which every child is started (D3, D15). An empty value would start nothing
# and report success for it, so it stops the run instead.
if [ -z "$(printf '%s' "${LOOP_AGENT_CMD:-}" | tr -d '[:space:]')" ]; then
  loop_die "run: LOOP_AGENT_CMD is empty in $LOOP_CONF — the run has no way to start an agent process"
fi

# The run header names every effective value, so a run can be repeated and audited (D2). The child's
# wall-clock is a recorded value too (`loop.conf`), so it is named here with the rest of the budget.
child_max="${LOOP_CHILD_MAX_SECONDS:-2700}"
case "$child_max" in ''|*[!0-9]*|0) child_max=2700 ;; esac

# The host is part of a run's numbers, so the run reads it and names it beside the budget. Both values are
# recorded (`loop.conf`, `LOOP_MIN_FREE_MIB` and `LOOP_MEMINFO_PATH`) because both are facts about the host and
# neither is a fact about a phase (D16). Measured 2026-10-19 (issue #209): item #126's gate found 194 MB free
# and 18 GB of shared swap and spent 30 min inside `:app:kspMobileDebugUnitTestKotlin` that takes 3m47s on a
# quiet host, and a `free` taken during another run of the same tree read 467 MB free against 13.9 GB of swap
# used. The run's wall time then measures the desktop session, not the change. A host that publishes no
# `MemAvailable` — a container without procfs, a platform that keeps the number elsewhere — makes the check
# pass: a check that cannot be made is not a refusal, and the engine must run there too.
host_free_mib() { # → MiB the host has available, or nothing when this host publishes no such number
  local meminfo kb
  meminfo="${LOOP_MEMINFO_PATH:-/proc/meminfo}"
  [ -n "$meminfo" ] || return 0
  [ -r "$meminfo" ] || return 0
  kb=$(sed -n 's/^MemAvailable:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$meminfo" 2>/dev/null | head -1)
  case "$kb" in ''|*[!0-9]*) return 0 ;; esac
  printf '%s\n' $((kb / 1024))
}

# The floor an item may not start below, in MiB. Absent or malformed keeps the recorded default, exactly as the
# child's wall-clock does, and `0` disables the check for a project that has nothing to compare against.
min_free_mib="${LOOP_MIN_FREE_MIB:-1024}"
case "$min_free_mib" in ''|*[!0-9]*) min_free_mib=1024 ;; esac
free_mib=$(host_free_mib)

# A repository whose `core.bare` is true is not a work tree: `git status` still answers, so a run looks healthy,
# but every merge, fast-forward and commit in it is refused with "this operation must be run in a work tree".
# The run merges the branch it closes (#229) and so does the caller's publish, and both then read as "the branch
# could not be merged" — the real cause never named (issue #241, measured 2026-10-21: five refused
# fast-forwards, `git config --get core.bare` → `true`, `git rev-parse --is-inside-work-tree` → `false`, and one
# `git config core.bare false` fixed all of it). The run refuses before it writes anything, and says what to do.
printf 'agent-issue-loop: items=%s minutes=%s review=%s may-close=%s push=%s max-decisions=%s max-review-rounds=%s review-read-only=%s child-max-seconds=%s min-free-mib=%s free-mib=%s dry-run=%s reset-worktree=%s\n' \
  "$items" "$minutes" "$review" "$may_close" "$push" "$max_decisions" "$max_review_rounds" \
  "$read_only" "$child_max" "$min_free_mib" "${free_mib:-unknown}" "$dry_run" "$reset_worktree"
# What follows the header, and where to watch it happen (issue #191). The pre-screen is the next step and it is
# not instant — measured 2026-10-20: 53 s for 84 records over 188 tracker items — and a header that named only its
# arguments left the operator to decide from silence whether the run was alive, which is how two runs of that day
# were interrupted by hand inside that window. The two views that exist while a run works are named here, so the
# answer does not depend on the operator remembering them.
printf 'run: the pre-screen runs next and is not instant; live view: bash tools/loop/state.sh show · state directory %s\n' \
  "$(dirname "$LOOP_STATE_FILE")"

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
adopt_finished_attempt() { # <issue> <branch> → 0 adopted and closed, 5 not adoptable (the reason printed)
  local issue="$1" branch="$2" out merged_branch merged_commits merged_change merged_evidence
  out=$(bash "$LOOP_DIR/worktree.sh" adopt-item "$issue" "$branch" 2>&1) || {
    printf '%s\n' "$out"
    return "$LOOP_REFUSE"
  }
  printf '%s\n' "$out"
  # The merge moved the base branch itself, so the base the run recorded is the commit every later item must
  # descend from — without this the next `start-item` would refuse the run's own merge as a stale base.
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "${LOOP_BASE_REF:-main}") || base=""
  [ -z "$base" ] || loop_state set-base "$base" >/dev/null 2>&1 || true
  merged_branch=$(printf '%s\n' "$out" | sed -n 's/^adopted: branch \([^ ]*\) .*/\1/p')
  merged_commits=$(printf '%s\n' "$out" | sed -n 's/.* · commits \([^ ·]*\) ·.*/\1/p')
  merged_change=$(printf '%s\n' "$out" | sed -n 's/.* · change \([^ ·]*\) ·.*/\1/p')
  merged_evidence=$(printf '%s\n' "$out" | sed -n 's/.* · evidence \(.*\)$/\1/p')
  local close_args=("$issue" \
    --reason "a previous attempt finished this item and was merged, not re-run" \
    --evidence "branch $merged_branch (commits $merged_commits), $worktree/openspec/changes/archive/$merged_change, evidence checked: $merged_evidence")
  if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi
  local closure="no"
  if loop_labels close "${close_args[@]}" >&2; then
    closure="$(if [ "$may_close" = yes ]; then printf yes; else printf skipped; fi)"
  fi
  loop_state done "$issue" --result done --verdict closed --change "$merged_change" \
    --closed "$closure" --note "$out" \
    --merged-branch "$merged_branch" --merged-commits "$merged_commits" --merged-evidence "$merged_evidence" \
    >/dev/null
  # The branch is merged, so the work is in the base and the branch itself is a leftover: the run removes
  # the branch it merged (a cherry-picked adoption is not an ancestor of the base, so git's safe delete
  # refuses it and the branch stays for the sweep — which asks the same question by patch identity).
  bash "$LOOP_DIR/worktree.sh" release-branch "$branch" >&2 || true
  printf 'item #%s: closed (merged %s — a finished attempt the base did not have) · %s review round(s)\n' \
    "$issue" "$merged_branch" 0
}

# The branch the success path just committed on is the fix's only home until it is merged: `end-item` commits on
# `fix/issue-<n>-<slug>`, and the loop's only merge, `adopt-item`, is reached for a *previous* attempt's branch —
# which needs the item to be visited again, and a closed item is never picked (#229). Measured 2026-10-20 on
# item #201: the run closed it with its two commits still on the branch and the base at the run's base, and a
# person fast-forwarded `main` 35 minutes later. So the run merges what it committed, before it closes, and a
# refused merge blocks the item instead: a closure the tree does not back is what `status/fixed-by` must never
# mean. The line the merge prints is the run's record of it; the refusal is left in `merge_refusal` for the
# block's reason.
merge_refusal=""
merge_committed_branch() { # <issue> <branch> → 0 merged and the line printed, 5 refused (the reason in merge_refusal)
  # `base` is deliberately not in this list: the merge moves the run's own base, and every later item is cut from
  # it. Declared here, the assignment below wrote a copy — the state file advanced and the run kept the commit it
  # read once at its start, so the next item was handed a base several merges old (issue #252).
  local issue="$1" branch="$2" out
  merge_refusal=""
  out=$(bash "$LOOP_DIR/worktree.sh" merge-item "$issue" "$branch" 2>&1) || {
    merge_refusal="$out"
    printf '%s\n' "$out" >&2
    return "$LOOP_REFUSE"
  }
  printf '%s\n' "$out"
  # The merge moved the base branch itself, so the base the run recorded is the commit every later item must
  # descend from — without this the next `start-item` would refuse the run's own merge as a stale base.
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "${LOOP_BASE_REF:-main}") || base=""
  # A base the tree moved to but the state does not carry is not a warning to swallow: the run hands the next item
  # a commit the tree has left, which is the same defect one step further on. The merge is refused instead, so the
  # item blocks with its branch named rather than closing on a base nobody can reproduce (issue #252).
  if [ -z "$base" ] || ! loop_state set-base "$base" >/dev/null 2>&1; then
    merge_refusal="the base could not be recorded after the merge — ${LOOP_BASE_REF:-main} did not resolve, or the run's state refused it, so the state and the tree would disagree about what every later item descends from"
    printf '%s\n' "$merge_refusal" >&2
    return "$LOOP_REFUSE"
  fi
  # The branch and its commits are what a later reader checks the closure against: git lists the branch, nothing
  # else does once the run's state is replaced by the next run's.
  merged_branch=$(printf '%s\n' "$out" | sed -n 's/^merged: branch \([^ ]*\) .*/\1/p')
  merged_commits=$(printf '%s\n' "$out" | sed -n 's/.* · commits \([^ ·]*\)$/\1/p')
}

# The pre-screen asks git for a previous attempt's branch and records it as a decision (#175), and the start's
# own check refuses one that appears while the run is open (#163). Both are the *same* decision this function
# makes once, before anything is escalated for them, so an item whose attempt finished is merged and closed
# instead of being handed to the owner.
adopt_finished_exclusions() {
  [ "$dry_run" -eq 0 ] || return 0
  local issue branch
  jq -r --arg d "$decision_agent" '
    .excluded[] | select(.verdict == $d) | select((.evidence // "") | startswith("fix/issue-"))
    | [(.issue | tostring), .evidence] | @tsv
  ' "$LOOP_STATE_FILE" 2>/dev/null |
  while IFS=$'\t' read -r issue branch; do
    [ -n "$issue" ] || continue
    adopt_finished_attempt "$issue" "$branch" >&2 || true
  done
}

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
    printf 'worktree kept for the next run: %s\n' "$worktree"
  fi
}

# ── the refusals that must happen before anything exists ────────────────────────────────────────

# 1. the identity: an unauthorised run stops before creating anything (spec, "an unauthorised identity
# stops the run before any work; no branch, label or state is written" — the state it just wrote is the
# run's own record, and no branch, worktree or label is created past this point).
preflight_out=$(loop_issue_tool policy.sh preflight 2>&1)
preflight_status=$?
if [ "$preflight_status" -eq 3 ]; then
  printf '%s\nstop: the identity this run acts as is not authorised — nothing was created\n' "$preflight_out"
  exit "$LOOP_STOP"
fi
if [ "$preflight_status" -ne 0 ]; then
  printf '%s\n' "$preflight_out" >&2
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
# The process group of the child that is running right now, and nothing else: `run_child` sets it under `set -m`
# and clears it once it has waited, so the traps below can end the child (and everything the child started) when
# the run takes a signal — the path a person's `kill`, or an operator's Ctrl-C, takes — while it is waiting for
# that child (issue #213).
active_child=""
on_exit() {
  local rc=$?
  trap - EXIT
  # The child first: a run ended by a signal must not leave the item's agent working in the tree while the run
  # writes the item's outcome. `loop_end_group` sends `TERM`, so the child's own cleanup runs, and waits for it.
  loop_end_group "$active_child"
  active_child=""
  if [ "$interrupted" -eq 1 ] && [ -n "$in_progress" ]; then
    # A killed run must leave no item looking in progress: the label comes off and the item is recorded
    # blocked, so the next run's pre-screen can take it up again (D7).
    loop_labels unpick "$in_progress" >&2 || true
    loop_state done "$in_progress" --result blocked --note "interrupted by a signal" >&2 || true
  fi
  loop_state unlock --owner "$$" >&2 || true
  exit "$rc"
}
on_signal() { interrupted=1; exit "$1"; }
trap on_exit EXIT
trap 'on_signal 130' INT
trap 'on_signal 143' TERM

# 3. the state: this run's own record, written now that this run is the only one that can write it. The
# process that writes it is named in it, so a reader can tell a run that is going from one that was killed.
init_args=(--items "$items" --minutes "$minutes" --review "$review" --may-close "$may_close" \
  --max-decisions "$max_decisions" --max-review-rounds "$max_review_rounds")
if [ -n "$base" ]; then init_args+=(--base "$base"); fi
init_args+=(--owner-pid "$$")
loop_state init "${init_args[@]}" || exit $?
base=$(loop_state get run.base)

# 4. the kept worktree. Keeping it is deliberate (the next run inherits its warm checkout, D4) and refusing a
#    previous run's leftovers is the right rule — but the refusal may not end a run with an exit code and no
#    item: it names the item whose branch the kept tree carries, records the refusal under that item,
#    escalates it and prints the report, so the "a run that ends with nothing processed and no reason"
#    outcome stays impossible on the path where it is most likely — a killed or crashed run (issue #173).
#    The check is one `git status` in a directory the run already knows: it needs neither the state nor the
#    lock, so it happens here, before the decision cap is counted and long before the pre-screen is paid for.
#    A worktree a *live* run is working in is not leftovers: a live run holds the lock, and this run was
#    refused at that lock before it got here — so a tree that is not clean now is a previous run's.
worktree="$LOOP_WORKTREE"
worktree_is_dirty() {
  git -C "$LOOP_ROOT" worktree list --porcelain 2>/dev/null | grep -qx "worktree $worktree" &&
    [ -n "$(git -C "$worktree" status --porcelain 2>/dev/null)" ]
}
if [ -d "$worktree" ] && worktree_is_dirty; then
  leftovers=$(git -C "$worktree" status --porcelain)
  # The item the leftovers belong to: `start-item` writes the item's branch into the worktree's own branch
  # marker, so a tree a killed run left behind still names the item it was working on.
  marker=$(cat "$worktree/.pi/loop-branch" 2>/dev/null || true)
  owner_item=$(printf '%s\n' "$marker" | sed -n 's#^fix/issue-\([0-9][0-9]*\)-.*#\1#p' | head -1)
  if [ "$reset_worktree" -eq 1 ] && [ "$dry_run" -eq 0 ]; then
    printf 'reset-worktree: discarding what a previous run left in %s:\n' "$worktree"
    printf '%s\n' "$leftovers" | sed 's/^/  /'
    git -C "$worktree" reset -q --hard || loop_die "run: could not reset the kept worktree"
    git -C "$worktree" clean -qfd || loop_die "run: could not clean the kept worktree"
    printf 'reset-worktree: %s is clean again — the commits on its branches are left alone\n' "$worktree"
  else
    printf 'refusing to start: the kept worktree %s is not clean — a previous run left work behind\n' "$worktree"
    printf '%s\n' "$leftovers" | sed 's/^/  /'
    if [ -n "$owner_item" ]; then
      printf 'the leftovers belong to #%s (branch %s)\n' "$owner_item" "$marker"
    else
      printf 'the worktree is on %s, which names no item — nothing is attributed\n' "${marker:-an unknown branch}"
    fi
    printf 'repair: run.sh --reset-worktree discards them, and names them before it does; nothing is committed or discarded without that flag\n'
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
    bash "$LOOP_DIR/report.sh"
    exit "$LOOP_REFUSE"
  fi
fi

# 5. the decision cap: decisions pile up across runs, so a per-run count cannot see them (D9).
open_decisions=$(loop_issue_tool list.sh --json --limit 400 2>/dev/null |
  jq -r --arg label "$decision_label" '[.[] | select([.labels[].name] | index($label))] | length')
if [ "${open_decisions:-0}" -ge "$max_decisions" ]; then
  printf 'refusing to start: %s item(s) already await a decision and the cap is %s\n' "$open_decisions" "$max_decisions"
  loop_issue_tool list.sh --json --limit 400 2>/dev/null |
    jq -r --arg label "$decision_label" \
      '.[] | select([.labels[].name] | index($label)) | "  #\(.number) \(.title)"'
  printf 'nothing new is produced while decisions accumulate — decide these first\n'
  exit "$LOOP_REFUSE"
fi

# 6. the queue. Nothing eligible is an explicit non-error: a stopped clock is not a failed loop (D5).
if [ "$items" -eq 0 ]; then
  printf 'nothing eligible: the run was started with --items 0 (an empty budget is not a failure)\n'
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
    printf 'nothing eligible: the pre-screen found no candidate this run may work\n'
    end_run
    exit "$LOOP_OK"
    ;;
  *) exit "$prescreen_status" ;;
esac

if [ "$dry_run" -eq 1 ]; then
  printf '\ndry run: what would happen, and nothing else\n'
  jq -r '.queue[] | "  rank \(.rank) · #\(.issue) [\(.class)/\(.category)] \(.title)"' "$LOOP_STATE_FILE"
  jq -r '.excluded[] | "  excluded #\(.issue) \(.verdict): \(.reason)"' "$LOOP_STATE_FILE"
  bash "$LOOP_DIR/report.sh"
  printf 'dry run: no worktree, no label and no agent process was created or started\n'
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
printf 'worktree: %s on %s from %s\n' "$worktree" "$branch" "${base:0:12}"
progress worktree "ready — $(basename "$worktree") on $branch from ${base:0:12}"

# The only push in the loop, and only when the run asked to publish (D4): no pull request is opened by
# this change at all.
push_branch() { git -C "$worktree" push --set-upstream origin "$1" >&2; }

report_field() { # report_field <file> <FIELD>
  sed -n "s/^${2}: //p" "$1" 2>/dev/null | tail -1
}

# The child's own last words, when it wrote no report line: a process that never got to work the item prints a
# complaint instead of a verdict, and that line is the whole of what the run has to name (issue #212).
last_line() { # last_line <file>
  grep -v '^[[:space:]]*$' "$1" 2>/dev/null | tail -1
}

# Whether a child's answer arrived whole — the shape of the report, never its content (#224). Two facts make it
# checkable without a model: the adapter prints exactly one newline after every answer it hands the run, and the
# prompt asks for its closing line last (the reviewer's `VERDICT:`, after the count it licenses). An answer that
# ends mid-word, or carries no closing line at all, was cut on its way to the file. Measured 2026-10-20: a
# round-2 report ended `… and the post-restore gre` while its own text recorded all seven scenarios PASS, and the
# run read the absence as a refusal, took another worker round and escalated work that had passed.
answer_complete() { # answer_complete <report file> <closing-line regex>
  [ -s "$1" ] || return 1
  [ -z "$(tail -c 1 "$1" 2>/dev/null)" ] || return 1
  grep -qE "$2" "$1" 2>/dev/null
}

# The reviewer's severity count, and nothing else about its prose (#205). A report may say `VERDICT: FAIL` and
# count zero blocking findings, which is a pass whose notes are recorded; a report that says nothing at all is
# empty here, and an empty count keeps the loop's conservative reading — an old-shaped review never passes.
blocking_count() { # blocking_count <report file> → the count, or empty when the line is absent or malformed
  local value
  value=$(report_field "$1" LOOP-BLOCKING)
  case "$value" in ''|*[!0-9]*) return 0 ;; esac
  printf '%s\n' "$value"
}

# What a round that refused nothing leaves behind: its `NOTE:` findings, one per line as bullets, or the path of
# the report when it graded none. This is what the item's closure comment and its state note carry, so a note is
# recorded rather than spent on another round (#205).
review_notes() { # review_notes <report file>
  local notes
  notes=$(sed -n 's/^NOTE: //p' "$1" 2>/dev/null)
  if [ -z "$notes" ]; then
    printf 'no NOTE: finding — the report is %s\n' "$1"
  else
    printf '%s\n' "$notes" | sed 's/^/- /'
  fi
}

slugify() { # a change name out of an item's title: lowercase, dashes, bounded
  printf '%s' "$1" |
    sed -e 's/^[^A-Za-z]*//' -e 's/§[0-9]*//g' |
    tr '[:upper:]' '[:lower:]' |
    sed -e 's/[^a-z0-9]\+/-/g' -e 's/^-//' -e 's/-$//' |
    cut -c1-40 |
    sed -e 's/-$//'
}

# The project's own tracked state, read around every child. A child is started in the project (a fresh
# process resolves the project's own tool policy at the directory it is started in), and the worktree is
# the only tree a phase may write — so a write the child left in the project's tree is caught here and
# never carried into the item's commit (D4, "one writer per tree").
project_state() {
  # The tree's own answer to "am I a work tree" travels with its status: a run whose project repository is flipped
  # to `core.bare=true` by something it started keeps answering `git status` — with nothing — while every merge
  # and commit in it is refused (#241). Compared before and after a child, the two lines disagree, and that
  # disagreement is the report.
  printf 'work-tree=%s\n' "$(git -C "$LOOP_ROOT" rev-parse --is-inside-work-tree 2>/dev/null | head -1)"
  git -C "$LOOP_ROOT" status --porcelain 2>/dev/null
}
# …and the base it recorded, so a child that commits on the project's own branch without touching a file is
# still a change of the tree the run promised to build on. `project_state` is compared before and after every
# child, which is what catches a write outside the worktree.

# The diff a review round is handed is the change, not the generated output a change is archived beside:
# item #126's round-3 diff (issue #204) carried 9 935 changed lines, 9 364 of them gate HTML and XML, against
# 112 lines of Kotlin. Which directories hold generated output is a fact about a project's change records,
# so the exclusion is a recorded pathspec (`loop.conf`'s `LOOP_REVIEW_EXCLUDE_PATHSPEC`) and this phase names
# no path of its own (D16). It excludes a *directory tree* and not a file extension on purpose: a `.txt` a
# reviewer must read can live outside an evidence directory, and an `.md` blended into one is still generated.
write_review_diff() { # write_review_diff <base> <file>
  local spec
  spec=$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)
  if [ -n "$spec" ]; then
    git -C "$worktree" diff "$1"..HEAD -- . "$spec" > "$2"
  else
    git -C "$worktree" diff "$1"..HEAD > "$2"
  fi
}

# The round a corrective refusal buys is handed the corrections, not the change again (issue #245; the decision
# recorded on the item, option 3). The refusing round said every finding was subtractive, so what the next
# reviewer has to decide is whether those findings were answered — and the whole change since the base is the
# wrong reading surface for it: it is the #204 measurement (9 935 changed lines, 9 364 of them generated output)
# at a smaller scale, and two rounds have already graded it. So the range is the refused round's own HEAD
# (`git diff <refused>..HEAD`, the ref `write_review_diff` is called with) and the findings are the refusing
# report, quoted. Nothing is promised that the run does not know: the range is named, and a finding the tree
# still carries is the round's to refuse.
review_correction_scope() { # <refused head> <refusing report path>
  local head="$1" report="$2"
  printf '## This round verifies the corrections, not the change again\n\n'
  printf 'The round before this one refused the change and stated `LOOP-CORRECTIONS-ONLY: yes`: every finding it\n'
  printf 'raised was subtractive, so one worker pass was what it asked for. This round decides whether that pass\n'
  printf 'answered them.\n\n'
  printf -- '- the diff under review at `{{DIFF_PATH}}` is `git diff %s..HEAD` — the correction round alone, not\n' "$head"
  printf '  the whole change since the base. A hunk of the change is absent from it on purpose; read the change\n'
  printf '  itself in `{{CHANGE_ROOT}}` where a correction needs its context.\n'
  printf -- '- the refusing round is `%s`, quoted in full below. Each of its findings is what this round\n' "$report"
  printf '  decides: a finding the tree still carries is a `BLOCKING:` finding, and one the correction answers is\n'
  printf '  recorded as closed. A correction whose hunk is missing is an unanswered finding, not an absence to\n'
  printf '  report against the diff.\n\n'
  printf '### The refusing round%ss findings\n\n' "'"
  cat "$report"
  printf '\n'
}

# Test results are the gate's evidence, and the run reads them out of the worktree after a worker — so a
# directory an earlier item wrote is indistinguishable from this item's own gate. The kept worktree carries them
# across items, and a child the round cap ends leaves the half-written state of its own gate behind (issue #253:
# item #116's killed Gradle left `auto/build/test-results/testDebugUnitTest/binary/in-progress-results-generic.bin`,
# and the next gate failed with `java.io.EOFException` on infrastructure instead of on a case; the same leftovers
# defeat the "no test-results XML exists in the tree" refusal below, because there is always an XML).
#
# So every worker starts on a worktree without one. What is removed is a directory named `test-results` whose
# parent is a `build` directory, inside the worktree, through `loop_scratch_remove`, whose four refusals are the
# whole safety argument: only inside the tree, never through a symlink, never a path git tracks. The reviewer's
# child is deliberately not covered: the round reads the gate result the worker just wrote, and taking it out of
# the tree would hand the reviewer a tree the worker's evidence has been removed from.
clear_stale_test_results() {
  local dir rel cleared=""
  [ -d "$worktree" ] || return 0
  while IFS= read -r dir; do
    [ -n "$dir" ] || continue
    rel="${dir#"$worktree"/}"
    loop_scratch_remove "$worktree" "$rel" --apply || continue
    cleared="${cleared:+$cleared }$rel"
  done < <(find "$worktree" -type d -name test-results -path '*/build/test-results' 2>/dev/null | sort)
  [ -n "$cleared" ] || return 0
  printf 'run: cleared %s from the worktree before this child — test results an earlier child wrote, which its gate would have counted\n' "$cleared"
}

# A child's own stream is silent by construction: the adapter the configured agent command names keeps the
# child's event stream for the report (`LOOP_CHILD_EVENTS`) and prints its answer once it has exited (measured
# 2026-10-19: on a pipe nothing appears until then), so a run used to print `pick #N` and then nothing for the
# whole item — a working child and a hung one looked the same. The wait therefore reports itself: one line when
# the child starts, one every `LOOP_PROGRESS_SECONDS` (default 30), and one when it ends. What those lines carry
# is read from the tree the child writes — its log's size, the worktree's changed files, its commits since the
# base — never from the child's own claim, which only exists once it is over. `fmt_elapsed` and the `progress`
# line the run's own phases use live in `lib.sh`, so a phase line and a child's line agree on what an elapsed
# time looks like (issue #191).
run_child() { # run_child <prompt-file> <report-file> — every child goes through LOOP_AGENT_CMD (D3)
  # The child is bounded by the recorded wall-clock (`LOOP_CHILD_MAX_SECONDS`, default 2700 = 45 min) and not
  # only by the run's `--minutes`: the deadline is consulted between items, so a child that never returns
  # spends the whole budget inside one wait and nothing notices (measured 2026-10-19: an item's worker phase
  # ran 83 min that way — issue #203). A child that outlasts the cap is ended together with everything it
  # started and its transcript says so, so the run's ordinary "exited N" path records the item `blocked`.
  # The working directory is the project and not the worktree on purpose: the project's own tool policy is
  # resolved at the directory the process starts in, and every path the child is handed — the work package,
  # its evidence directory, the change root its prompt names — lies inside the project. The tree it is told
  # to change is still the worktree, by the path in its prompt.
  local before after rc phase stop hb started interval cap child timed_out=0
  before=$(project_state)
  phase=$(basename "$1" .md)
  # The worker is the child whose gate result the run reads out of the worktree, so it starts on a tree that
  # carries no other child's test results (issue #253).
  case "$phase" in worker*) clear_stale_test_results ;; esac
  interval="${LOOP_PROGRESS_SECONDS:-30}"
  case "$interval" in ''|*[!0-9]*|0) interval=30 ;; esac
  cap="${LOOP_CHILD_MAX_SECONDS:-2700}"
  case "$cap" in ''|*[!0-9]*|0) cap=2700 ;; esac
  stop="$2.progress-stop"
  rm -f "$stop"
  : > "$2"
  started=$(date +%s)
  printf 'loop: %s started (log %s)\n' "$phase" "$2"
  ( ticks=0
    while [ ! -e "$stop" ]; do
      sleep 1
      [ -e "$stop" ] && break
      ticks=$((ticks + 1))
      [ $((ticks % interval)) -eq 0 ] || continue
      printf 'loop: %s running %s — log %s B, worktree %s changed file(s), %s commit(s) since the base\n' \
        "$phase" "$(fmt_elapsed "$ticks")" "$(wc -c < "$2" 2>/dev/null || printf 0)" \
        "$(git -C "$LOOP_WORKTREE" status --porcelain 2>/dev/null | wc -l | tr -d ' ')" \
        "$(git -C "$LOOP_WORKTREE" rev-list --count "${base:-HEAD}..HEAD" 2>/dev/null || printf 0)"
    done ) >&2 &
  hb=$!
  # `set -m` on the one command that starts the child, immediately undone: a background job of a shell with job
  # control is its own process group, which is what lets the cap end the child *and its descendants* — a Gradle
  # gate leaves daemons and test workers behind, and those are the memory the next item needs. Its stdin is
  # closed because a member of a background group that reads a terminal is stopped, not run, and an unattended
  # child has nobody to type at it anyway.
  set -m
  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1"; printf '%s\n' "$?" > "$2.status" ) > "$2" 2>&1 < /dev/null &
  child=$!
  active_child="$child"
  set +m
  # The wait ends on the **child's own completion record**, not on `kill -0` of its pid and not on `wait`: a child
  # that the shell has already reaped answers nothing, and a `wait` on a pid bash no longer tracks returns no
  # status, so a poll that believes the child is gone can leave the run blocked behind a `wait` for ever. Measured
  # 2026-10-20/21 (issue #227): fourteen runs sat inside this poll with only a `sleep` child, printing a heartbeat
  # every 30 s for up to 4 h 52 min — 606 lines, `log 0 B`, and not one `LOOP-TIMEOUT` line, although the cap was
  # 2700 s. The subshell writes its own status when it returns, which is the one event the run can wait for.
  local status_file="$2.status"
  rm -f "$status_file"
  while [ ! -e "$status_file" ]; do
    if [ $(( $(date +%s) - started )) -ge "$cap" ]; then
      timed_out=1
      kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
      sleep 1
      kill -KILL -"$child" 2>/dev/null || true
      break
    fi
    sleep 1
  done
  rc=0
  [ -e "$status_file" ] && rc=$(cat "$status_file" 2>/dev/null || printf 0)
  case "$rc" in ''|*[!0-9]*) rc=1 ;; esac
  # Nothing is left to a bare `wait`: a child that is still there after its own record was written, or after the
  # cap ended it, is ended here by pid, and the loop does not block on the shell's job table.
  if kill -0 "$child" 2>/dev/null; then loop_end_pid "$child"; fi
  active_child=""
  if [ "$timed_out" -eq 1 ]; then
    printf 'LOOP-TIMEOUT: the child exceeded %s s — the run ended it and everything it started\n' "$cap" >> "$2"
    rc="$LOOP_CHILD_TIMEOUT"
  fi
  : > "$stop"
  wait "$hb" 2>/dev/null
  rm -f "$stop"
  printf 'loop: %s finished in %s (exit %s, log %s B)\n' "$phase" \
    "$(fmt_elapsed $(( $(date +%s) - started )))" "$rc" "$(wc -c < "$2" 2>/dev/null || printf 0)"
  after=$(project_state)
  if [ "$before" != "$after" ]; then
    printf 'LOOP-OUTSIDE-WRITE: the project tree changed while this process ran\n' >> "$2"
  fi
  return "$rc"
}

render_prompt() { # render_prompt <template> <out> [KEY=VALUE]…
  local template="$1" out="$2"; shift 2
  loop_render "$template" "$@" > "$out"
}

# The round's prompt, rendered once per attempt: the same contract and the same diff, only the file it is written
# to differs, so a retried answer is the same question asked again rather than a second review (#224).
# `CORRECTION_SCOPE` is substituted **first**, not last: `loop_render` replaces one pair at a time on the template it
# has accumulated so far, and the section names `{{DIFF_PATH}}` and `{{CHANGE_ROOT}}` itself. A section inserted
# after those pairs would survive verbatim and hand the reviewer a file called `{{DIFF_PATH}}` — the round that
# exists to verify corrections pointing at a placeholder (item #245's round-1 review).
render_reviewer_prompt() { # render_reviewer_prompt <out file> [the corrections-only scope]
  render_prompt "$LOOP_DIR/prompts/reviewer.md" "$1" \
    "CORRECTION_SCOPE=${2:-}" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "DIFF_PATH=$diff_file" "EVIDENCE_DIR=$item_dir" \
    "REVIEW_EXCLUDE=${review_exclude_spec:-none}" "REVIEW_WALL_CLOCK=${child_max:-2700}"
}

# Why a run ended is the run's own number, not a line on a console that scrolls away: every path that leaves
# the item loop records its reason, so `report.sh` prints the end the state file holds (#184).
stop_run() { # <reason> — printed and recorded, then the caller breaks or exits
  printf 'stop: %s\n' "$1"
  loop_state stop "$1" >/dev/null 2>&1 || true
}

# ── the item loop ───────────────────────────────────────────────────────────────────────────────

queue_passes=1
while true; do
  if [ "$(jq -r '.queue | length' "$LOOP_STATE_FILE")" -eq 0 ] && [ "$queue_passes" -lt 2 ]; then
    # A queue that emptied mid-run gets one more pass, never a third (SKILL.md, "Pre-screen the backlog").
    queue_passes=$((queue_passes + 1))
    bash "$LOOP_DIR/prescreen.sh" >&2 || true
    escalate_prescreen_decisions
  fi
  if ! next_out=$(loop_state next); then
    # `next` names the stop condition on stdout and exits 3: it is the run's own verdict on why it may not
    # start another item, and the only account of the end besides the counters. Captured and dropped, the
    # printed report could not say why a run stopped (#184).
    stop_run "$next_out"
    break
  fi
  issue="$next_out"
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
    printf 'item #%s: blocked (%s)\n' "$issue" "$shortfall"
    stop_run "$shortfall; free memory and re-run — a starved host cannot become productive by taking the next item"
    break
  fi
  issue_json=$(loop_issue_json "$issue" --comments) || {
    printf 'run: gh could not read #%s\n' "$issue" >&2
    stop_run "gh could not read #$issue — the tracker did not answer"
    break
  }
  title=$(printf '%s' "$issue_json" | jq -r '.title // ""')
  # The item's class is a work input, not a label the worker guesses: a feature takes the propose-first path
  # while a bug and an improvement take the fix path (`tools/loop/prompts/worker.md`).
  class=$(printf '%s' "$issue_json" | jq -r '[.labels[]? | if type == "string" then . else .name end |
    select(startswith("class/")) | sub("^class/"; "")] | .[0] // ""')
  # The class that is planned before it is applied is the profile's value, not a word in the skeleton (D16).
  propose_class=$(loop_role proposeClass)
  slug=$(slugify "$title")
  [ -n "$slug" ] || slug="issue-$issue"

  if ! start_out=$(bash "$LOOP_DIR/worktree.sh" start-item "$issue" "$slug" 2>&1); then
    printf '%s\n' "$start_out"
    case "$start_out" in
      # The base moved: main is not what this run recorded, so nothing may be branched off it. That is a stop
      # with a reason, not a silent exit — a run that ends without recording why makes its own report read
      # "ended without recording a reason" and its numbers unattributable (#184, measured 2026-10-20).
      *"base moved"*) stop_run "the base moved: main advanced while this run was open — $(printf '%s' "$start_out" | head -1)"; break ;;
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
    printf 'run: could not mark #%s in progress\n' "$issue" >&2
    stop_run "the in-progress label could not be written on #$issue"
    break
  }
  in_progress="$issue"

  item_dir="$stage_dir/items/$issue"
  mkdir -p "$item_dir"
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
  package="$item_dir/work-package.md"
  fingerprint=$(printf '%s' "$issue_json" | jq -r '.body // ""' | { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
  # The item's text reaches the child inside a delimited data field, never in an instruction position
  # (D10): the work package is a file, and the prompt names its path instead of inlining it.
  printf '%s' "$issue_json" |
    issues_core WorkPackage --profile "$(loop_profile)" --issue - \
      --fingerprint "$fingerprint" --provenance "issue #$issue" > "$package" ||
    loop_die "run: could not render the work package for #$issue"

  render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt.md" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
    "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
    "CLASS=$class" "PROPOSE_CLASS=$propose_class"

  started=$(date +%s%3N)
  run_child "$item_dir/worker-prompt.md" "$item_dir/worker-report.md"
  worker_exit=$?
  loop_state step A --name worker --status "$(if [ "$worker_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
    --note "exit $worker_exit" >/dev/null

  verdict_claim=$(report_field "$item_dir/worker-report.md" LOOP-VERDICT)
  skip_verdict=$(report_field "$item_dir/worker-report.md" LOOP-SKIP-VERDICT)
  change_claim=$(report_field "$item_dir/worker-report.md" LOOP-CHANGE)
  root_cause=$(report_field "$item_dir/worker-report.md" LOOP-ROOT-CAUSE)
  worker_decision=$(report_field "$item_dir/worker-report.md" LOOP-DECISION 2>/dev/null || true)
  red_test=$(report_field "$item_dir/worker-report.md" LOOP-RED-TEST)
  gate_claim=$(report_field "$item_dir/worker-report.md" LOOP-GATE)
  outside_write=$(report_field "$item_dir/worker-report.md" LOOP-OUTSIDE-WRITE)
  timeout_claim=$(report_field "$item_dir/worker-report.md" LOOP-TIMEOUT)
  # A falsification is the one skip whose finding is not a question: the worker measured that the mechanism the
  # item blames does not hold. Both halves are required before the run treats it as one — what was blamed, and
  # what was measured instead with how it was taken — because a claim nobody can check is a question again
  # (openspec change `falsified-items-close-in-the-loop`).
  falsified_mechanism=$(report_field "$item_dir/worker-report.md" LOOP-FALSIFIED 2>/dev/null || true)
  falsified_measurement=$(report_field "$item_dir/worker-report.md" LOOP-MEASURED 2>/dev/null || true)

  # The run verifies rather than trusts: it commits the child's work itself and then reads the tree, the
  # archive and the gate's evidence, instead of repeating the child's claims (D3, D8).
  commit_out=$(bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" 2>&1)
  head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
  progress commit "end-item returned — HEAD ${head_now:0:12} (base ${base:0:12})"

  # A child that never started is the run's fault, not the item's (issue #212). `run_child` returns the child's
  # status and nothing else, so a status alone cannot tell a child whose argv the environment refused — measured
  # 2026-10-20: every child of a run died in under 4 s with one 35-byte complaint and exit 1 — from a child that
  # worked the item and failed. The item's own evidence separates the two: a child that ended non-zero, wrote no
  # verdict and left the tree at the base never read the item. Recording that as a blocked item put a question
  # on the tracker that nobody had asked and spent one of the run's decision slots, twice in 3 minutes. It is
  # recorded once as the run's own stop instead, and the item is left exactly as its owner set it. Two children
  # stay on the item's side of this line: one the run itself ended carries its own transcript line and is a
  # blocked item (issue #203), and one that left work behind has a branch, which is what a later pass escalates
  # (issue #176).
  if [ "$worker_exit" -ne 0 ] && [ -z "$verdict_claim" ] && [ -z "$timeout_claim" ] && [ "$head_now" = "$base" ]; then
    child_words=$(last_line "$item_dir/worker-report.md")
    # The in-progress mark comes off before the run stops: an item nothing was done to must be as eligible
    # afterwards as it was before the run picked it.
    loop_labels unpick "$issue" >&2 || true
    in_progress=""
    stop_run "the child could not start — it exited $worker_exit without a verdict and left the tree at the base${child_words:+: $child_words}"
    break
  fi
  archives=$(git -C "$worktree" diff --name-only "$base"..HEAD -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 {print $4}' | sort -u)
  change=$(printf '%s\n' "$archives" | grep -x -- "$change_claim" 2>/dev/null | head -1)
  [ -n "$change" ] || change=$(printf '%s\n' "$archives" | head -1)
  gate_xmls=$(find "$worktree" -path '*test-results*' -name '*.xml' 2>/dev/null | head -20)
  gate_found=$(printf '%s' "$gate_xmls" | grep -c . || true)
  progress gate "read — $gate_found test-results XML in the tree"
  # The gate tally the item carries: the child's claim when it made one, what the run found otherwise,
  # and "skipped" when the project configured no gate at all (a project with no build still runs — D16).
  if [ -z "$LOOP_GATE_CMD" ]; then
    gate_field="skipped (no gate configured)"
  else
    gate_field="${gate_claim:-$gate_found xml}"
  fi
  # Evidence has to outlive the run: a change that cites a test result must carry it in its own
  # `evidence/` directory, because that is the versioned half of the change (invariant 1).
  cited_evidence=0
  if [ -n "$red_test" ] || [ -n "$gate_claim" ]; then cited_evidence=1; fi
  change_evidence=""
  [ -n "$change" ] && change_evidence="$worktree/openspec/changes/archive/$change/evidence"
  carried=0
  if [ -d "$change_evidence" ] && [ -n "$(ls -A "$change_evidence" 2>/dev/null)" ]; then carried=1; fi

  outcome="closed"
  blocked_reason=""
  if [ "$worker_exit" -ne 0 ]; then
    outcome="blocked"; blocked_reason="the worker process exited $worker_exit"
    # A child the run itself ended says so in the item's own reason. "exited 124" alone leaves the owner unable
    # to tell a run that bounded a hung child from a child that failed by itself (issue #203).
    if [ -n "$timeout_claim" ]; then blocked_reason="$blocked_reason — $timeout_claim"; fi
  elif [ -n "$outside_write" ]; then
    outcome="blocked"; blocked_reason="the worker wrote outside the worktree — the project's own tree changed while it ran"
  elif [ "$verdict_claim" = "skipped" ] && [ "${skip_verdict:-}" = "falsified" ] \
    && [ -n "$falsified_mechanism" ] && [ -n "$falsified_measurement" ] && [ "$head_now" = "$base" ]; then
    # Measured 2026-10-20: three of the first four items of one run (#89, #99, #137) were items whose premise the
    # worker disproved, and each was escalated as a decision although the run held the number that settled it.
    # The empty tree is part of the evidence — a falsification that changed files is a change.
    outcome="falsified"
  elif [ "$verdict_claim" = "skipped" ]; then
    outcome="skipped"
  elif [ "$head_now" = "$base" ]; then
    outcome="blocked"; blocked_reason="reported success but the tree is still at the base commit — nothing was committed"
  elif [ -z "$change" ]; then
    outcome="blocked"; blocked_reason="reported success but no archived change appears in the run's diff"
  elif [ -n "$LOOP_GATE_CMD" ] && [ "$gate_found" -eq 0 ]; then
    outcome="blocked"; blocked_reason="the report claims a gate result but no test-results XML exists in the tree"
  elif [ "$cited_evidence" -eq 1 ] && [ "$carried" -eq 0 ]; then
    outcome="blocked"; blocked_reason="the change cites a test result but carries no evidence in its own evidence/ directory"
  fi

  review_rounds=0
  review_verdict="off"
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
      before=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; project_state; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      reviewer_report="$item_dir/reviewer-report-$round.md"
      render_reviewer_prompt "$item_dir/reviewer-prompt-$round.md" "$correction_scope"
      # The round is announced on the branch *before* the reviewer starts, so an answer that never arrives is a
      # fact a later run reads (#262): with only the answer written, a reviewer killed mid-answer left a branch
      # that read exactly like a passed one, and the next run adopted it. A round that cannot be announced stays
      # a round the run holds, and the warning names what a later run will read instead.
      if [ -n "$change" ]; then
        bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$round" requested >&2 ||
          printf 'run: review round %s could not be announced on the branch — a later run may adopt %s as if no review had been asked for\n' \
            "$round" "$change" >&2
      fi
      run_child "$item_dir/reviewer-prompt-$round.md" "$reviewer_report"
      # An answer cut on its way to the file decides nothing, and it is not a reviewer that stayed quiet either
      # (#224): it is the run's own transport, so the round is asked once more instead of the item being blocked.
      # A round the run itself ended already says so in its report (`LOOP-TIMEOUT`), so only a silent cut is
      # retried — a timeout is read exactly as it was.
      if [ -z "$(report_field "$reviewer_report" LOOP-TIMEOUT)" ] &&
        ! answer_complete "$reviewer_report" '^VERDICT: (PASS|FAIL)'; then
        printf 'run: the round %s answer was cut short (%s) — retrying the round once\n' \
          "$round" "$(last_line "$reviewer_report")" >&2
        reviewer_report="$item_dir/reviewer-report-$round-retry.md"
        render_reviewer_prompt "$item_dir/reviewer-prompt-$round-retry.md" "$correction_scope"
        run_child "$item_dir/reviewer-prompt-$round-retry.md" "$reviewer_report"
        if [ -z "$(report_field "$reviewer_report" LOOP-TIMEOUT)" ] &&
          ! answer_complete "$reviewer_report" '^VERDICT: (PASS|FAIL)'; then
          # Twice is not a hiccup the run can wait out: the round has no verdict at all, so the block says that
          # instead of naming a refusal no reviewer made, and it records no failed review on the branch — a
          # later run must not be told the reviewer refused this change.
          review_verdict="cut-short"
          outcome="blocked"
          blocked_reason="the reviewer's report was cut short twice — the round $round answer never arrived, so no reviewer refused this change"
          break
        fi
      fi
      after=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; project_state; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      if [ "$read_only" = "on" ] && [ "$before" != "$after" ]; then
        # The reviewer wrote, so it cannot have been the reviewer: the round is discarded (D3).
        review_verdict="reviewer-wrote"
        outcome="blocked"; blocked_reason="the review round was discarded: the working tree changed while the reviewer ran"
        break
      fi
      # The word on the verdict is not what decides: the report's blocking count is (#205). `VERDICT: PASS`
      # with a non-zero count contradicts itself and is read as a refusal, and a refusal with a zero count is
      # a pass whose notes are recorded — the findings are wording, pointers and naming, which cost the change
      # no round. A report with no count line keeps today's reading, so an old-shaped review never passes.
      blocking=$(blocking_count "$reviewer_report")
      if grep -q '^VERDICT: PASS' "$reviewer_report" 2>/dev/null &&
        { [ -z "$blocking" ] || [ "$blocking" -eq 0 ]; }; then
        review_verdict="PASS"
      elif [ -n "$blocking" ] && [ "$blocking" -eq 0 ]; then
        review_verdict="NOTES"
      fi
      # A round that answered is written on the branch as the answer to the round it announced (#262). The
      # refusal arm is written after the loop (`record-review-failure`), where the run knows the round was the
      # last one; here the answer is all that is known and all a later run needs.
      if [ "$review_verdict" = "PASS" ] || [ "$review_verdict" = "NOTES" ]; then
        if [ -n "$change" ]; then
          bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$round" passed >&2 ||
            printf 'run: the round %s answer could not be recorded on the branch — a later run reads %s as never answered\n' \
              "$round" "$change" >&2
        fi
      fi
      if [ "$review_verdict" = "PASS" ]; then
        break
      fi
      if [ "$review_verdict" = "NOTES" ]; then
        review_notes_text=$(review_notes "$reviewer_report")
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
      # FAIL: the same change goes back to a new worker process with the reviewer's findings.
      findings=$(cat "$reviewer_report")
      render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt-round2.md" \
        "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
        "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
        "CLASS=$class" "PROPOSE_CLASS=$propose_class"
      printf '\n## The reviewer refused this change; change exactly these things\n\n%s\n' "$findings" \
        >> "$item_dir/worker-prompt-round2.md"
      run_child "$item_dir/worker-prompt-round2.md" "$item_dir/worker-report-round2.md"
      outside_write=$(report_field "$item_dir/worker-report-round2.md" LOOP-OUTSIDE-WRITE)
      if [ -n "$outside_write" ]; then
        # Named for what it is: a write outside the worktree, not a second failed review round.
        review_verdict="outside-write"
        outcome="blocked"
        blocked_reason="the round-2 worker wrote outside the worktree — the project's own tree changed while it ran"
        break
      fi
      bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" >/dev/null 2>&1 || true
      write_review_diff "$diff_base" "$item_dir/review-$((round + 1)).diff"
      round=$((round + 1))
    done
    if [ "$review_verdict" = "FAIL" ]; then
      outcome="blocked"
      blocked_reason="the change failed its last review round ($review_rounds of $round_cap) — a decision is needed, not another attempt"
      # The verdict has to outlive this run and travel with the change: the branch is what a later run adopts
      # from, and its archived change plus green XML say nothing about a reviewer that refused it (#185). A
      # falsification has no change to record the verdict against — its tree did not move — so the branch keeps
      # no review marker and `worktree.sh` is not asked to write one. The round's announcement is already on the
      # branch (#262), so the refusal answers a round a later run can see was asked.
      if [ -n "$change" ]; then
        bash "$LOOP_DIR/worktree.sh" record-review-failure "$issue" "$change" "$review_rounds" >&2 ||
          printf 'run: the failed review could not be recorded on the branch — a later run may adopt it\n' >&2
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
      outcome="blocked"
      blocked_reason="the falsification claimed an unchanged tree, but the tree moved during its review — a change exists, so the item is not mis-diagnosed"
    fi
  fi

  # A finished item is not closed until the fix is in the tree: the branch the run committed on is merged onto
  # the base here, and a merge the run cannot take leaves the item blocked with its branch (#229). This runs
  # before the outcome is written, so a refusal takes the same blocked path every other refusal takes.
  merged_branch=""
  merged_commits=""
  if [ "$outcome" = "closed" ]; then
    merge_committed_branch "$issue" "fix/issue-$issue-$slug" || {
      outcome="blocked"
      blocked_reason="the branch fix/issue-$issue-$slug could not be merged onto the base, so the item is not closed on a fix the tree does not have — $merge_refusal"
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
      if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi
      if loop_labels close "${close_args[@]}"; then closure="$(if [ "$may_close" = yes ]; then printf yes; else printf skipped; fi)"; fi
      loop_state done "$issue" --result done --verdict closed --change "$change" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" --gate "$gate_field" \
        --closed "$closure" --root-cause "$root_cause" --red-test "$red_test" \
        --merged-branch "$merged_branch" --merged-commits "$merged_commits" \
        --events "$events_file" --note "$note_field" >/dev/null
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
        --root-cause "$falsified_mechanism" --red-test "$falsified_measurement" \
        --events "$events_file" --note "the premise does not hold" >/dev/null
      ;;
    skipped)
      # The in-progress mark comes off on every exit path (labels.sh, header): an item a run skipped is no
      # more in progress than one it blocked, and an item that carries `agent/in-progress` while nothing is
      # running misreports the tracker to whoever reads it next.
      loop_labels unpick "$issue" >&2 || true   # the exit path clears the mark
      escalated_label=""
      if [ "$skip_verdict" = "$decision_agent" ] || [ "$skip_verdict" = "needs-diagnosis" ] \
        || [ "$skip_verdict" = "falsified" ]; then
        # Escalation, not abandonment: the work already done is named on the item (spec, D9), and so is the
        # question — a worker that reached a decision states it in LOOP-DECISION, and an escalation that
        # carries a verdict without one leaves the owner a state nobody can answer (#194). The worker's
        # verdict is not the label written — a skip on `needs-diagnosis` writes the decision label — so the
        # label is recorded with the item, which is what the report reads (issue #177).
        escalate_reason="${root_cause:-$skip_verdict}"
        if [ -n "$worker_decision" ]; then
          escalate_reason="$escalate_reason

**The decision:** $worker_decision"
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
      loop_state done "$issue" --result skipped --verdict "${skip_verdict:-unspecified}" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --root-cause "$root_cause" --red-test "$red_test" --note "skipped by the worker" \
        --events "$events_file" --decision-label "$escalated_label" >/dev/null
      ;;
    *)
      loop_labels unpick "$issue" >&2 || true
      # A blocked item is escalated in the same pass, exactly as a skipped one is: the reason is held in the
      # run state, and the next run replaces that state wholesale, so a block that left the tracker untouched
      # would vanish — the decision list would not hold it, the decision cap would not count it, and the next
      # attempt would be told nothing (issue #174). The escalation writes the label, the status and the comment
      # that names the reason and the work already done, and the label it wrote is recorded with the item, so
      # the report names it as the run's own escalation (issue #177).
      escalated_label=""
      # The block's escalation carries the decision itself, not only the news that one is needed: the reviewer
      # already answered the question when it wrote its report, so the owner reads the options and the
      # recommendation instead of re-deriving them (#186).
      escalation_reason="${blocked_reason:-the item was blocked}"
      decision_field=$(report_field "${reviewer_report:-/dev/null}" LOOP-DECISION 2>/dev/null || true)
      if [ -n "$decision_field" ]; then
        escalation_reason="$escalation_reason

**The decision:** $decision_field"
      else
        # A block that never reached a review round asked the item no question — most often because the cause is
        # the run's own child rather than the item (a claim with no commit, a timeout, a child that could not
        # start). Escalating the reason alone put an entry in the decision queue that nobody can answer, and the
        # ceiling counts labels (issues #194, #225). The block leaves exactly one two-way choice, so the run
        # states it, with the reason it holds as the ground for either answer.
        escalation_reason="$escalation_reason

**The decision:** retry the item, or abandon it — $blocked_reason. The reason above is the run's finding, not a
question the item asked; a retry costs another attempt, and an abandonment removes it from the loop."
      fi
      if loop_labels escalate "$issue" --reason "$escalation_reason" \
        --evidence "$item_dir, branch fix/issue-$issue-$slug" >&2; then
        escalated_label="$decision_label"   # the block's own escalation
      fi
      loop_state done "$issue" --result blocked --verdict blocked --change "$change_claim" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --root-cause "$root_cause" --red-test "$red_test" --note "$blocked_reason" \
        --events "$events_file" --decision-label "$escalated_label" >/dev/null
      ;;
  esac
  detail="${change:-$blocked_reason}"
  if [ "$outcome" = skipped ]; then detail="${skip_verdict:-unspecified}"; fi
  printf 'item #%s: %s (%s) · %ss · %s review round(s)\n' "$issue" "$outcome" "$detail" "$wall" "$review_rounds"
  in_progress=""

  if [ "$push" = yes ]; then
    push_branch "fix/issue-$issue-$slug"
  fi
done

# ── the run's end ───────────────────────────────────────────────────────────────────────────────

end_run
exit "$LOOP_OK"
