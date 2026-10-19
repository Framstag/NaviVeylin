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
# Usage: run.sh [--items N] [--minutes M] [--review on|off] [--may-close yes|no] [--push yes|no]
#               [--max-decisions N] [--review-read-only on|off] [--dry-run] [--clean-worktree]
#               [--reset-worktree] [--base <sha>]
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

items=""
minutes=""
review=""
may_close=""
max_decisions=""
push="no"
read_only="on"
dry_run=0
clean_worktree=0
reset_worktree=0
base=""
while [ $# -gt 0 ]; do
  case "$1" in
    --items) items="${2:-}"; shift 2 ;;
    --minutes) minutes="${2:-}"; shift 2 ;;
    --review) review="${2:-}"; shift 2 ;;
    --may-close) may_close="${2:-}"; shift 2 ;;
    --push) push="${2:-}"; shift 2 ;;
    --max-decisions) max_decisions="${2:-}"; shift 2 ;;
    --review-read-only) read_only="${2:-}"; shift 2 ;;
    --base) base="${2:-}"; shift 2 ;;
    --dry-run) dry_run=1; shift ;;
    --clean-worktree) clean_worktree=1; shift ;;
    --reset-worktree) reset_worktree=1; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) loop_die "run: unknown argument $1" ;;
  esac
done

loop_resolve
loop_roles_load
# The resolved paths and the configuration go to every child, so all of them agree on which run and which
# project they belong to.
export LOOP_STATE_FILE="$LOOP_STATE_FILE" LOOP_WORKTREE="$LOOP_WORKTREE"
export LOOP_CONF="${LOOP_CONF:-$LOOP_DIR/loop.conf}"

: "${items:=$(loop_conf_value LOOP_ITEMS_DEFAULT)}"
: "${minutes:=$(loop_conf_value LOOP_MINUTES_DEFAULT)}"
: "${review:=$(loop_conf_value LOOP_REVIEW_DEFAULT)}"
: "${may_close:=$(loop_conf_value LOOP_MAY_CLOSE_DEFAULT)}"
: "${max_decisions:=$(loop_conf_value LOOP_MAX_DECISIONS_DEFAULT)}"

for pair in "items=$items" "minutes=$minutes" "max-decisions=$max_decisions"; do
  case "${pair#*=}" in ''|*[!0-9]*) loop_die "run: ${pair%%=*} must be a non-negative integer" ;; esac
done
case "$review" in on|off) ;; *) loop_die "run: --review must be on or off" ;; esac
case "$may_close" in yes|no) ;; *) loop_die "run: --may-close must be yes or no" ;; esac
case "$push" in yes|no) ;; *) loop_die "run: --push must be yes or no" ;; esac
case "$read_only" in on|off) ;; *) loop_die "run: --review-read-only must be on or off" ;; esac

# The one indirection through which every child is started (D3, D15). An empty value would start nothing
# and report success for it, so it stops the run instead.
if [ -z "$(printf '%s' "${LOOP_AGENT_CMD:-}" | tr -d '[:space:]')" ]; then
  loop_die "run: LOOP_AGENT_CMD is empty in $LOOP_CONF — the run has no way to start an agent process"
fi

# The run header names every effective value, so a run can be repeated and audited (D2).
printf 'agent-issue-loop: items=%s minutes=%s review=%s may-close=%s push=%s max-decisions=%s review-read-only=%s dry-run=%s reset-worktree=%s\n' \
  "$items" "$minutes" "$review" "$may_close" "$push" "$max_decisions" "$read_only" "$dry_run" "$reset_worktree"

init_args=(--items "$items" --minutes "$minutes" --review "$review" --may-close "$may_close" --max-decisions "$max_decisions")
if [ -n "$base" ]; then init_args+=(--base "$base"); fi
loop_state init "${init_args[@]}" || exit $?
base=$(loop_state get run.base)

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
  printf 'item #%s: closed (merged %s — a finished attempt the base did not have) · %s review round(s)\n' \
    "$issue" "$merged_branch" 0
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
end_run() {
  bash "$LOOP_DIR/report.sh"
  if [ "$clean_worktree" -eq 1 ]; then
    bash "$LOOP_DIR/worktree.sh" remove || true
  elif [ -n "${worktree:-}" ] && [ -d "$worktree" ]; then
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

# 2. the kept worktree. Keeping it is deliberate (the next run inherits its warm build, D4) and refusing a
#    previous run's leftovers is the right rule — but the refusal may not end a run with an exit code and no
#    item: it names the item whose branch the kept tree carries, records the refusal under that item,
#    escalates it and prints the report, so the "a run that ends with nothing processed and no reason"
#    outcome stays impossible on the path where it is most likely — a killed or crashed run (issue #173).
#    The check is one `git status` in a directory the run already knows: it needs neither the state nor the
#    lock, so it happens here, before the decision cap is counted and long before the pre-screen is paid for.
#    A worktree a *live* run is working in is not leftovers, and the lock in the run's own state directory is
#    the one signal that tells the two apart — read here, never taken (this run takes it further down, and a
#    run that holds it refuses there as it always has).
worktree="$LOOP_WORKTREE"
run_lock="$(dirname "$LOOP_STATE_FILE")/lock"
worktree_is_dirty() {
  git -C "$LOOP_ROOT" worktree list --porcelain 2>/dev/null | grep -qx "worktree $worktree" &&
    [ -n "$(git -C "$worktree" status --porcelain 2>/dev/null)" ]
}
if [ -d "$worktree" ] && [ ! -e "$run_lock" ] && worktree_is_dirty; then
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

# 3. the decision cap: decisions pile up across runs, so a per-run count cannot see them (D9).
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

# 4. the queue. Nothing eligible is an explicit non-error: a stopped clock is not a failed loop (D5).
if [ "$items" -eq 0 ]; then
  printf 'nothing eligible: the run was started with --items 0 (an empty budget is not a failure)\n'
  end_run
  exit "$LOOP_OK"
fi

# A dry run writes no label (D6): the pre-screen's refusal labels are skipped on it, so "what would
# happen" stays a statement about the run and not about the tracker.
prescreen_args=()
[ "$dry_run" -eq 0 ] || prescreen_args+=(--dry-run)
prescreen_out=$(bash "$LOOP_DIR/prescreen.sh" "${prescreen_args[@]}" 2>&1)
prescreen_status=$?
printf '%s\n' "$prescreen_out"
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

# ── the lock, and the traps that clean up after an interrupt ────────────────────────────────────

loop_state lock --owner "$$" || exit $?

in_progress=""
interrupted=0
on_exit() {
  local rc=$?
  trap - EXIT
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

stage_dir=$(dirname "$LOOP_STATE_FILE")

# The only push in the loop, and only when the run asked to publish (D4): no pull request is opened by
# this change at all.
push_branch() { git -C "$worktree" push --set-upstream origin "$1" >&2; }

report_field() { # report_field <file> <FIELD>
  sed -n "s/^${2}: //p" "$1" 2>/dev/null | tail -1
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
project_state() { git -C "$LOOP_ROOT" status --porcelain 2>/dev/null; }

run_child() { # run_child <prompt-file> <report-file> — every child goes through LOOP_AGENT_CMD (D3)
  # The working directory is the project and not the worktree on purpose: the project's own tool policy is
  # resolved at the directory the process starts in, and every path the child is handed — the work package,
  # its evidence directory, the change root its prompt names — lies inside the project. The tree it is told
  # to change is still the worktree, by the path in its prompt.
  local before after rc
  before=$(project_state)
  ( cd "$LOOP_ROOT" && $LOOP_AGENT_CMD "$1" ) > "$2" 2>&1
  rc=$?
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
    printf 'stop: %s\n' "$next_out"
    loop_state stop "$next_out" >/dev/null
    break
  fi
  issue="$next_out"
  issue_json=$(loop_issue_json "$issue") || { printf 'run: gh could not read #%s\n' "$issue" >&2; break; }
  title=$(printf '%s' "$issue_json" | jq -r '.title // ""')
  slug=$(slugify "$title")
  [ -n "$slug" ] || slug="issue-$issue"

  if ! start_out=$(bash "$LOOP_DIR/worktree.sh" start-item "$issue" "$slug" 2>&1); then
    printf '%s\n' "$start_out"
    case "$start_out" in
      *"base moved"*) exit "$LOOP_REFUSE" ;;
      *"carries commits the base does not"*)
        # A previous attempt left work on the item's branch. What it carries decides: a finished change with a
        # green verification is merged and the item closed, and only a branch without that evidence is the
        # owner's — recorded and escalated rather than dropped, because an unrecorded refusal is how a run ends
        # with "nothing processed" and no reason (D7, D9). The run then takes the next candidate.
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
      *) break ;;
    esac
  fi
  loop_labels pick "$issue" || { printf 'run: could not mark #%s in progress\n' "$issue" >&2; break; }
  in_progress="$issue"

  item_dir="$stage_dir/items/$issue"
  mkdir -p "$item_dir"
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
    "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "WORKER_SKILLS=$LOOP_WORKER_SKILLS"

  started=$(date +%s%3N)
  run_child "$item_dir/worker-prompt.md" "$item_dir/worker-report.md"
  worker_exit=$?
  loop_state step A --name worker --status "$(if [ "$worker_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
    --note "exit $worker_exit" >/dev/null

  verdict_claim=$(report_field "$item_dir/worker-report.md" LOOP-VERDICT)
  skip_verdict=$(report_field "$item_dir/worker-report.md" LOOP-SKIP-VERDICT)
  change_claim=$(report_field "$item_dir/worker-report.md" LOOP-CHANGE)
  root_cause=$(report_field "$item_dir/worker-report.md" LOOP-ROOT-CAUSE)
  red_test=$(report_field "$item_dir/worker-report.md" LOOP-RED-TEST)
  gate_claim=$(report_field "$item_dir/worker-report.md" LOOP-GATE)
  outside_write=$(report_field "$item_dir/worker-report.md" LOOP-OUTSIDE-WRITE)

  # The run verifies rather than trusts: it commits the child's work itself and then reads the tree, the
  # archive and the gate's evidence, instead of repeating the child's claims (D3, D8).
  commit_out=$(bash "$LOOP_DIR/worktree.sh" end-item "$issue" "$slug" 2>&1)
  head_now=$(bash "$LOOP_DIR/worktree.sh" head 2>/dev/null)
  archives=$(git -C "$worktree" diff --name-only "$base"..HEAD -- openspec/changes/archive 2>/dev/null |
    awk -F/ 'NF > 3 {print $4}' | sort -u)
  change=$(printf '%s\n' "$archives" | grep -x -- "$change_claim" 2>/dev/null | head -1)
  [ -n "$change" ] || change=$(printf '%s\n' "$archives" | head -1)
  gate_xmls=$(find "$worktree" -path '*test-results*' -name '*.xml' 2>/dev/null | head -20)
  gate_found=$(printf '%s' "$gate_xmls" | grep -c . || true)
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
  elif [ -n "$outside_write" ]; then
    outcome="blocked"; blocked_reason="the worker wrote outside the worktree — the project's own tree changed while it ran"
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
  if [ "$outcome" = "closed" ] && [ "$review" = "on" ]; then
    review_verdict="FAIL"
    round=1
    while [ "$round" -le 2 ]; do
      review_rounds=$round
      diff_file="$item_dir/review-$round.diff"
      git -C "$worktree" diff "$base"..HEAD > "$diff_file"
      before=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; project_state; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      render_prompt "$LOOP_DIR/prompts/reviewer.md" "$item_dir/reviewer-prompt-$round.md" \
        "ISSUE=$issue" "CHANGE_ROOT=$worktree" "DIFF_PATH=$diff_file" "EVIDENCE_DIR=$item_dir"
      run_child "$item_dir/reviewer-prompt-$round.md" "$item_dir/reviewer-report-$round.md"
      after=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; project_state; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      if [ "$read_only" = "on" ] && [ "$before" != "$after" ]; then
        # The reviewer wrote, so it cannot have been the reviewer: the round is discarded (D3).
        review_verdict="reviewer-wrote"
        outcome="blocked"; blocked_reason="the review round was discarded: the working tree changed while the reviewer ran"
        break
      fi
      if grep -q '^VERDICT: PASS' "$item_dir/reviewer-report-$round.md" 2>/dev/null; then
        review_verdict="PASS"
        break
      fi
      if [ "$round" -eq 2 ]; then break; fi
      # FAIL: the same change goes back to a new worker process with the reviewer's findings.
      findings=$(cat "$item_dir/reviewer-report-$round.md")
      render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt-round2.md" \
        "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
        "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "WORKER_SKILLS=$LOOP_WORKER_SKILLS"
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
      git -C "$worktree" diff "$base"..HEAD > "$item_dir/review-$((round + 1)).diff"
      round=$((round + 1))
    done
    if [ "$review_verdict" = "FAIL" ]; then
      outcome="blocked"
      blocked_reason="the change failed its second review round — a decision is needed, not a third attempt"
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
      close_args=("$issue" --reason "${root_cause:-closed by the run}" --evidence "$evidence_paths")
      if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi
      if loop_labels close "${close_args[@]}"; then closure="$(if [ "$may_close" = yes ]; then printf yes; else printf skipped; fi)"; fi
      loop_state done "$issue" --result done --verdict closed --change "$change" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" --gate "$gate_field" \
        --closed "$closure" --root-cause "$root_cause" --red-test "$red_test" >/dev/null
      ;;
    skipped)
      # The in-progress mark comes off on every exit path (labels.sh, header): an item a run skipped is no
      # more in progress than one it blocked, and an item that carries `agent/in-progress` while nothing is
      # running misreports the tracker to whoever reads it next.
      loop_labels unpick "$issue" >&2 || true   # the exit path clears the mark
      escalated_label=""
      if [ "$skip_verdict" = "$decision_agent" ] || [ "$skip_verdict" = "needs-diagnosis" ]; then
        # Escalation, not abandonment: the work already done is named on the item (spec, D9). The worker's
        # verdict is not the label written — a skip on `needs-diagnosis` writes the decision label — so the
        # label is recorded with the item, which is what the report reads (issue #177).
        if loop_labels escalate "$issue" --reason "${root_cause:-$skip_verdict}" \
          --evidence "$item_dir, branch fix/issue-$issue-$slug, red test: ${red_test:-not reached}" >&2; then
          escalated_label="$decision_label"   # the skip's own escalation
        fi
      else
        loop_labels "${skip_verdict:-unspecifiable}" "$issue" >&2 || true
      fi
      loop_state done "$issue" --result skipped --verdict "${skip_verdict:-unspecified}" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --root-cause "$root_cause" --red-test "$red_test" --note "skipped by the worker" \
        --decision-label "$escalated_label" >/dev/null
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
      if loop_labels escalate "$issue" --reason "${blocked_reason:-the item was blocked}" \
        --evidence "$item_dir, branch fix/issue-$issue-$slug" >&2; then
        escalated_label="$decision_label"   # the block's own escalation
      fi
      loop_state done "$issue" --result blocked --verdict blocked --change "$change_claim" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --root-cause "$root_cause" --red-test "$red_test" --note "$blocked_reason" \
        --decision-label "$escalated_label" >/dev/null
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
