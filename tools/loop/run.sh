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

# What one child may run (issue #663). Two recorded numbers decide it: the floor a child may run even when the
# profile records no gate cost (`LOOP_CHILD_MAX_SECONDS`, 45 min), and what one item's gate owes
# (`LOOP_ITEM_GATE_SECONDS`) — an item owes a red gate round and a green one, and the phases the gate is not. The
# child is given the larger of the two, because a child ended inside its own item's cost is a green change the run
# pays for and then discards: measured 2026-10-26, one gate round of a `tools/loop/**` item cost 971 s over 23
# suites, an item owed 957 s + 233 s + 972 s of gate and 542 s of phases — 2 704 s against the 2 700 s constant —
# and the child was ended 8 s after its green `gate-summary.xml` was written (#310, and #664, #666 the same way).
child_cap() { # → the seconds a child may run
  local floor owes
  floor="${LOOP_CHILD_MAX_SECONDS:-2700}"
  case "$floor" in ''|*[!0-9]*|0) floor=2700 ;; esac
  owes="${LOOP_ITEM_GATE_SECONDS:-0}"
  case "$owes" in ''|*[!0-9]*) owes=0 ;; esac
  if [ "$owes" -gt "$floor" ]; then printf '%s\n' "$owes"; else printf '%s\n' "$floor"; fi
}

# The run header names every effective value, so a run can be repeated and audited (D2). The child's
# wall-clock is a recorded value too (`loop.conf`), so it is named here with the rest of the budget — as the
# bound the child is given, so the header and the `LOOP-TIMEOUT` line that ends it cannot disagree.
child_max=$(child_cap)

# How many turns a child may take before the run ends it, **per role** (issues #686, #784). The wall clock bounds
# how long a child runs and says nothing about how much it spends being slow to converge: `cacheRead` is 97 % of
# the archive's spend, a turn re-reads the whole conversation, and no turn amortises two calls, so a heavy item is
# a chain of single-call turns at 85–106 k context each. The bound used to be the **item's**, one count over the
# event stream every child of the item appends to, so the worker, the round before the gate, the gate, the tail and
# each review round spent from one pot and the last phase inherited the remainder: measured 2026-11-05 over the ten
# items that ran all the phases, #780 spent 80/34/42/41 turns on its first four children and ended its round-1
# reviewer at 53 of the same 250, and #767 gave its round-2 worker three. The bound is the **child role's own** now,
# read through `child_role`, so a phase is bounded by what that phase costs and not by what the phases before it
# spent. The item's total is still counted and reported — `metrics.turns`, the report's `calls:` line, the band
# `LOOP_ITEM_TOKENS_WARN` records — and it stops nothing. The count is read from the stream the child appends to
# (`child_turns`), never from a child's claim. `0` is no bound, and a role this project records no key for has none:
# a project with no such record owes nothing by it, the shape `LOOP_MIN_FREE_MIB` and `LOOP_ITEM_TOKENS_WARN`
# already have. The derivation of this project's six values is beside them in `loop.conf`.
child_turn_bound() { # child_turn_bound <role> → the turns that role may spend; 0 = no bound
  local role="$1" value
  value=$(loop_conf_value "LOOP_CHILD_TURNS_MAX_$(printf '%s' "$role" | tr '[:lower:]-' '[:upper:]_')")
  case "$value" in ''|*[!0-9]*) value=0 ;; esac
  printf '%s\n' "$value"
}
# What the header names, so a run can be repeated and audited (D2) and the header and the `LOOP-TIMEOUT` line that
# ends a child cannot disagree about the bound they apply: every role `agent_roles` below can name, in that order,
# the rework round included — a bound the header does not print is one a run cannot be audited for (issue #842).
child_turn_bounds=""
for role in worker worker-gate worker-tail worker-rework reviewer reviewer-pre-gate; do
  child_turn_bounds="${child_turn_bounds:+$child_turn_bounds,}$role:$(child_turn_bound "$role")"
done

# A bound priced in what a turn costs, not in how many turns there are (issue #760). The turn bound above counts
# turns, and a turn is a unit of shape rather than of cost: every turn re-reads the whole conversation, so the same
# turn count costs 40 k or 240 k depending on how far the context has grown — over this project's archive (227 items,
# 2026-11-03, `tools/loop-spend.py --json`) tokens per item are p50 10.4 M and p90 23.6 M against turns per item p50
# 140 and p90 273, a 5x spend over a 2x range of turns. The reading that varies already exists
# (`tools/loop/state.sh`'s `child_usage_json`, the largest `input + cacheRead` a turn reported), but only the report
# reads it, after the item is over. So the run reads it too, where the wall clock and the turn count are already
# enforced, and takes two decisions from it: a checkpoint line at `LOOP_ITEM_CONTEXT_WARN` (nothing is ended) and an
# end at `LOOP_ITEM_CONTEXT_MAX`. Measured over the 300 item records of `.pi/issue-loop/run.json` and `runs/*.json`
# (2026-11-05): the largest context per item is p50 107 669, p75 143 124, p90 174 984, p95 191 815, maximum 244 764.
# The checkpoint is the p75, recorded exactly as measured, the way the token band records its own; it flags about a
# quarter of the items and costs one line. The maximum is 200 000 — above the p95 and at the window every provider
# this project runs sits at, where a turn costs 1.86x the archive's median turn (200 000 / 107 669) — and 10 of the
# 300 recorded items (3.3 %) reached it. Both are `0` or absent = no bound, the shape `LOOP_MIN_FREE_MIB` and
# `LOOP_ITEM_TOKENS_WARN` have, so a project that records neither owes nothing by them (D16).
item_context_warn="${LOOP_ITEM_CONTEXT_WARN:-0}"
case "$item_context_warn" in ''|*[!0-9]*) item_context_warn=0 ;; esac
item_context_max="${LOOP_ITEM_CONTEXT_MAX:-0}"
case "$item_context_max" in ''|*[!0-9]*) item_context_max=0 ;; esac

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

# The two numbers of a quota wait: how long the run sleeps when the reader names no reset instant, and the longest
# wait it will take before it ends the run instead (`0` = no cap). Read here beside the host floor, because both
# are the run's own numbers about a condition outside it — how long a pause may be is not something the reader of
# a balance knows, and how long to poll is not something the provider's answer carries.
quota_poll="${LOOP_QUOTA_POLL_SECONDS:-900}"
case "$quota_poll" in ''|*[!0-9]*|0) quota_poll=900 ;; esac
quota_hold_max="${LOOP_QUOTA_HOLD_MAX_SECONDS:-0}"
case "$quota_hold_max" in ''|*[!0-9]*) quota_hold_max=0 ;; esac

# A repository whose `core.bare` is true is not a work tree: `git status` still answers, so a run looks healthy,
# but every merge, fast-forward and commit in it is refused with "this operation must be run in a work tree".
# The run merges the branch it closes (#229) and so does the caller's publish, and both then read as "the branch
# could not be merged" — the real cause never named (issue #241, measured 2026-10-21: five refused
# fast-forwards, `git config --get core.bare` → `true`, `git rev-parse --is-inside-work-tree` → `false`, and one
# `git config core.bare false` fixed all of it). The run refuses before it writes anything, and says what to do.
# The command a child is started on, resolved from the two dimensions the run knows before it forks that child (issue
# #821): the role its prompt's name gives it (`child_role`, defined below beside `run_child`) and the class the item's
# own label carries (`class/<value>`, read before the item's first child starts). The chain is most specific first:
#
#   LOOP_AGENT_CMD_<ROLE>_<CLASS>   the role, refined by the item's class
#   LOOP_AGENT_CMD_<ROLE>           the role
#   LOOP_AGENT_CMD_<HEAD>_<CLASS>   the role it belongs to, refined by the class
#   LOOP_AGENT_CMD_<HEAD>           the role it belongs to — where `reviewer-pre-gate` inherits `REVIEWER`
#   LOOP_AGENT_CMD                  the project's one command
#
# `<HEAD>` is the role up to its first hyphen, so a qualifier inherits the command of the role it qualifies by one rule
# and not by a case per family. The class is a refinement of the role it is spelled after, which is why a role that
# records a command of its own still beats its head's class key. The spelling is derived and not invented: the role's
# transform is the one `run_child` already applied, extended to the class.
child_agent_cmd_key() { # child_agent_cmd_key <role> <class> → the key recording that child's command
  local head candidate cls
  head="${1%%-*}"
  cls=$(printf '%s' "$2" | tr '[:lower:]' '[:upper:]' | tr -c 'A-Z0-9' '_' | sed 's/_*$//')
  for candidate in \
    "LOOP_AGENT_CMD_$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')${cls:+_$cls}" \
    "LOOP_AGENT_CMD_$(printf '%s' "$1" | tr '[:lower:]-' '[:upper:]_')" \
    "LOOP_AGENT_CMD_$(printf '%s' "$head" | tr '[:lower:]-' '[:upper:]_')${cls:+_$cls}" \
    "LOOP_AGENT_CMD_$(printf '%s' "$head" | tr '[:lower:]-' '[:upper:]_')"; do
    if [ -n "$(loop_conf_value "$candidate" | tr -d '[:space:]')" ]; then printf '%s' "$candidate"; return 0; fi
  done
  printf 'LOOP_AGENT_CMD'
}

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
printf 'agent-issue-loop: items=%s minutes=%s review=%s pre-gate-review=%s may-close=%s push=%s max-decisions=%s max-review-rounds=%s review-read-only=%s strict-outside-write=%s child-max-seconds=%s child-turns-max=%s item-context-warn=%s item-context-max=%s min-free-mib=%s free-mib=%s dry-run=%s reset-worktree=%s agent-roles=%s quota=%s\n' \
  "$items" "$minutes" "$review" "$pre_gate" "$may_close" "$push" "$max_decisions" "$max_review_rounds" \
  "$read_only" "$strict_outside_write" "$child_max" "$child_turn_bounds" "$item_context_warn" "$item_context_max" "$min_free_mib" "${free_mib:-unknown}" "$dry_run" "$reset_worktree" "$agent_roles" "$quota_reader"
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
adopt_finished_attempt() { # <issue> <branch> → 0 adopted and closed, 5 not adoptable (the reason printed)
  local issue="$1" branch="$2" out merged_branch merged_commits merged_change merged_evidence
  out=$(bash "$LOOP_DIR/worktree.sh" adopt-item "$issue" "$branch" 2>&1) || {
    # Kept for the caller that records the refusal, exactly as `merge_refusal` is kept for the run's own merge
    # (#794): the text `adopt-item` printed is the item's record, not a line for the terminal alone.
    adoption_refusal="$out"
    printf '%s\n' "$out"
    return "$LOOP_REFUSE"
  }
  printf '%s\n' "$out"
  # The merge moved the base branch itself, so the base the run recorded is the commit every later item must
  # descend from — without this the next `start-item` would refuse the run's own merge as a stale base.
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)") || base=""
  [ -z "$base" ] || loop_state set-base "$base" >/dev/null 2>&1 || true
  merged_branch=$(printf '%s\n' "$out" | sed -n 's/^adopted: branch \([^ ]*\) .*/\1/p')
  merged_commits=$(printf '%s\n' "$out" | sed -n 's/.* · commits \([^ ·]*\) ·.*/\1/p')
  merged_change=$(printf '%s\n' "$out" | sed -n 's/.* · change \([^ ·]*\) ·.*/\1/p')
  merged_evidence=$(printf '%s\n' "$out" | sed -n 's/.* · evidence \(.*\)$/\1/p')
  local close_args=("$issue" \
    --reason "a previous attempt finished this item and was merged, not re-run" \
    --evidence "branch $merged_branch (commits $merged_commits), $worktree/openspec/changes/archive/$merged_change, evidence checked: $merged_evidence")
  # The commit the landing made is what the closure's claim rests on (#715): `base` is the branch this function
  # has just re-read, the same commit it records as `merged.sha` below.
  [ -z "$base" ] || close_args+=(--landed "$base")
  if [ "$may_close" = "no" ]; then close_args+=(--no-close); fi
  local closure="no"
  if loop_labels close "${close_args[@]}" >&2; then
    closure="$(if [ "$may_close" = yes ]; then printf yes; else printf skipped; fi)"
  fi
  loop_state done "$issue" --result done --verdict closed --change "$merged_change" \
    --closed "$closure" --note "$out" \
    --merged-branch "$merged_branch" --merged-commits "$merged_commits" --merged-evidence "$merged_evidence" \
    --merged-sha "$base" \
    --review-verdict off \
    >/dev/null
  score_landings
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
  base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)") || base=""
  # A base the tree moved to but the state does not carry is not a warning to swallow: the run hands the next item
  # a commit the tree has left, which is the same defect one step further on. The merge is refused instead, so the
  # item blocks with its branch named rather than closing on a base nobody can reproduce (issue #252).
  if [ -z "$base" ] || ! loop_state set-base "$base" >/dev/null 2>&1; then
    merge_refusal="the base could not be recorded after the merge — $(loop_base_ref) did not resolve, or the run's state refused it, so the state and the tree would disagree about what every later item descends from"
    printf '%s\n' "$merge_refusal" >&2
    return "$LOOP_REFUSE"
  fi
  # The branch and its commits are what a later reader checks the closure against: git lists the branch, nothing
  # else does once the run's state is replaced by the next run's.
  merged_branch=$(printf '%s\n' "$out" | sed -n 's/^merged: branch \([^ ]*\) .*/\1/p')
  merged_commits=$(printf '%s\n' "$out" | sed -n 's/.* · commits \([^ ·]*\)$/\1/p')
  # The commit the landing made is the base this function has just re-read (`base` above), not the abbreviated one
  # the merge line prints: `gh run list --commit` matches a commit, and the score of the pipeline it caused is what
  # the report's `landings:` line prints (issue #709).
  merged_sha="$base"
}

# The third trend figure is read here, once per landing, by the state's writer (issue #709): the run has just landed
# the item and the record carries the commit, so one `gh run list --commit` answers the pipeline that commit caused.
# It is best-effort on purpose — a landing the tracker does not answer for yet is recorded unscored and named by the
# report, and a network fault may never decide an item the run has already closed on its work.
score_landings() {
  loop_state score-landings 2>&1 | sed 's/^/  /' >&2 || true
}

# The pre-screen asks git for a previous attempt's branch and records it as a decision (#175), and the start's
# own check refuses one that appears while the run is open (#163). Both are the *same* decision this function
# makes once, before anything is escalated for them, so an item whose attempt finished is merged and closed
# instead of being handed to the owner.
adopt_finished_exclusions() {
  [ "$dry_run" -eq 0 ] || return 0
  local issue branch attempt_state
  jq -r --arg d "$decision_agent" '
    .excluded[] | select(.verdict == $d) | select((.evidence // "") | startswith("fix/issue-"))
    | [(.issue | tostring), .evidence] | @tsv
  ' "$LOOP_STATE_FILE" 2>/dev/null |
  while IFS=$'\t' read -r issue branch; do
    [ -n "$issue" ] || continue
    if adopt_finished_attempt "$issue" "$branch" >&2; then
      continue
    fi
    # The status the loop used to discard says which refusal this was (#794). `adoptable` is the branch's own
    # answer that nothing on it is the owner's, so what refused the merge is the tree it lands in, the base it
    # replays onto or the replay itself — a later run finds the first clean once the dirt is gone, so the item is
    # work and the refusal is its own result, recorded beside the exclusion. Any other state is the evidence's
    # refusal, and exactly as before it stays the pre-screen's decision.
    attempt_state=$(bash "$LOOP_DIR/worktree.sh" branch-state "$issue" "$branch" 2>/dev/null | cut -f1)
    [ "$attempt_state" = adoptable ] || continue
    loop_state adoption-refused "$issue" --reason "$adoption_refusal" >/dev/null ||
      printf 'run: #%s — the adoption refusal could not be recorded, so its exclusion stands as the pre-screen read it\n' \
        "$issue" >&2
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

# The work a child the run ended left is written down before the run moves on or stops (issue #308): the tracked
# diff, each untracked file, and the ignored files under the profile's record directory — this project's unarchived
# changes live under `openspec/changes/`, which is gitignored, and an unarchived change is exactly what a killed
# worker leaves behind. Every other ignored path is *named* in the header rather than copied, so no build directory
# lands in the patch, and so is any file above the size bound. Measured 2026-10-25: the item whose gate hung was
# repaired by hand from `git status` and a diff, because the run recorded that the tree was dirty and wrote the diff
# nowhere, and the only repair it named was `--reset-worktree`.
keep_leftovers() { # keep_leftovers <item> → sets $leftover_patch (+ the file it wrote)
  local item="$1" dir patch path size named
  leftover_patch=""
  case "$item" in ''|*[!0-9]*) return 0 ;; esac
  [ -d "$LOOP_WORKTREE" ] || return 0
  patch=$(loop_leftover_patch_of "$item")
  dir=$(dirname "$patch")
  mkdir -p "$dir" 2>/dev/null || return 0
  {
    printf '# the work #%s left in %s\n' "$item" "$LOOP_WORKTREE"
    # `diff HEAD` and not `diff`: a refused `end-item` leaves the work **staged** — the commit is what failed — and
    # `git diff` shows neither a staged addition nor a staged deletion, so the tracked half of the record would lose
    # exactly the work the loop's own commit path had staged (issue #308, measured on
    # `run#aRefusedStartKeepsTheLeftoversItFound`: `demo-fix.txt` was staged and absent from the patch).
    printf '# tracked changes (staged and unstaged, against HEAD):\n'
    git -C "$LOOP_WORKTREE" diff HEAD --no-color 2>/dev/null || true
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      size=$(wc -c < "$LOOP_WORKTREE/$path" 2>/dev/null || printf 0)
      if [ "$size" -gt 262144 ]; then
        printf '# %s: %s bytes, not copied\n' "$path" "$size"
      else
        diff -u --label "/dev/null" --label "b/$path" /dev/null "$LOOP_WORKTREE/$path" 2>/dev/null || true
      fi
    done < <(git -C "$LOOP_WORKTREE" ls-files --others --exclude-standard 2>/dev/null)
    if [ -n "$record_dir" ]; then
      printf "# ignored files under %s (the unarchived changes of this project):\n" "$record_dir"
      while IFS= read -r path; do
        [ -n "$path" ] || continue
        size=$(wc -c < "$LOOP_WORKTREE/$path" 2>/dev/null || printf 0)
        if [ "$size" -gt 262144 ]; then
          printf '# %s: %s bytes, not copied\n' "$path" "$size"
        else
          diff -u --label "/dev/null" --label "b/$path" /dev/null "$LOOP_WORKTREE/$path" 2>/dev/null || true
        fi
      done < <(git -C "$LOOP_WORKTREE" ls-files --others --ignored --exclude-standard -- "$record_dir" 2>/dev/null)
    fi
    named=0
    printf '# ignored paths not carried above (their contents are not in this patch):\n'
    while IFS= read -r path; do
      [ -n "$path" ] || continue
      if [ -n "$record_dir" ]; then case "$path" in "$record_dir"/*) continue ;; esac; fi
      if [ "$named" -lt 40 ]; then printf '# %s\n' "$path"; fi
      named=$((named + 1))
    done < <(git -C "$LOOP_WORKTREE" ls-files --others --ignored --exclude-standard 2>/dev/null)
    if [ "$named" -gt 40 ]; then printf '# … and %s more ignored path(s), not listed\n' "$((named - 40))"; fi
  } > "$patch" 2>/dev/null || { rm -f "$patch"; return 0; }
  [ -s "$patch" ] || { rm -f "$patch"; return 0; }
  leftover_patch="$patch"
}

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
  printf 'stop: %s\n' "$1"
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
  printf 'refusing to start: the project tree is on %s, and the landing step merges into it\n' \
    "${checkout_ref:-a detached HEAD}"
  printf 'the base branch is %s — switch this tree to it and run again:\n' "$(loop_base_ref)"
  printf '  git -C %s switch %s\n' "$LOOP_ROOT" "$(loop_base_ref)"
  stop_run "the project tree is checked out on ${checkout_ref:-a detached HEAD}, not the base branch $(loop_base_ref) — the landing step merges into this tree, so switch to it and re-run"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
fi

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
worktree_is_dirty() {
  git -C "$LOOP_ROOT" worktree list --porcelain 2>/dev/null | grep -qx "worktree $worktree" &&
    [ -n "$(git -C "$worktree" status --porcelain 2>/dev/null)" ]
}
# The item whose work a worktree holds: `start-item` writes the item's branch into the tree's own branch marker,
# so a tree somebody left mid-work still says whose it is. One reading, two readers — the start-time pre-flight
# below, and the start a refusal stops mid-run (issue #284) — because a second copy of the rule is a second rule.
owner_of_marker() { # owner_of_marker <branch> → the item that branch names, or nothing
  printf '%s\n' "${1:-}" | sed -n 's#^fix/issue-\([0-9][0-9]*\)-.*#\1#p' | head -1
}
# The items this run ended *itself*, whose work `keep_leftovers` wrote to the item's own patch (issue #308). It is
# the one distinction the mid-run start refusal cannot read from the tree: dirt this run ended and recorded may be
# discarded to start the next item, because what the refusal protects — a record beside the tree — already exists
# there (issue #664). The patch's path is derived, never stored: `lib.sh` owns that one derivation, because the
# writer, the reader and the start that applies it must agree about the file or one of them lies (issues #664, #754).
own_leftovers=" "
own_leftovers_of() { # own_leftovers_of <item> → 0 when this run ended that item and kept its work as a patch
  case "${1:-}" in ''|*[!0-9]*) return 1 ;; esac
  case "$own_leftovers" in *" $1 "*) ;; *) return 1 ;; esac
  [ -s "$(loop_leftover_patch_of "$1")" ]
}
# A tree whose only leftovers are the module's gitlink — the module sits on another pin and its own tree is clean —
# is not work: nothing inside the module was written, and placing the pin the base records loses nothing. The run
# repairs it where it used to stop on dirt no item owns (measured 2026-10-28): the pin was two unpublished commits ahead of
# the module's remote, the placement at `create` ended in `upload-pack: not our ref`, and the dirt that followed
# ended a run with 600 minutes left after 69 seconds. The project's configuration names the module, so a project
# without one answers no.
submodule_only_dirt() { # <the leftovers `git status --porcelain` printed> → 0 when only the module's pin differs
  local path line
  path=$(loop_conf_value LOOP_SUBMODULE_PATH)
  [ -n "$path" ] && [ -f "$worktree/.gitmodules" ] || return 1
  [ -n "${1:-}" ] || return 1
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    case "$line" in " M $path") ;; *) return 1 ;; esac
  done <<< "$1"
  [ -z "$(git -C "$worktree/$path" status --porcelain 2>/dev/null)" ]
}
place_submodule_pin() { # → 0 when the tree is clean afterwards; the placement is `worktree.sh`'s, not a second copy
  bash "$LOOP_DIR/worktree.sh" sync-module >&2 || return 1
  [ -z "$(git -C "$worktree" status --porcelain 2>/dev/null)" ]
}
retry_start_item() { # → 0 when the retried start succeeded; its output is left in $start_out
  if start_out=$(bash "$LOOP_DIR/worktree.sh" start-item "$issue" "$slug" --change "${item_change:-}" 2>&1); then
    start_after_discard=1
    return 0
  fi
  printf '%s\n' "$start_out"
  retry_refusal=$(printf '%s' "$start_out" | head -1)
  return 1
}
# The repair both refusals share: discard what the tree carries and answer whether it is clean afterwards. The
# start-time refusal reaches it behind `--reset-worktree`; the mid-run refusal reaches it only for the work this
# run ended itself, whose record already exists (issue #664) — one copy of the rule, because a second copy of a rule
# is a second rule (as `owner_of_marker` says above). The caller prints the line naming what is being discarded.
discard_kept_worktree() { # discard_kept_worktree <the name this repair prints itself under> <leftovers> → 0 when clean
  local label="${1:-reset-worktree}" leftovers_to_name="${2:-}" reset_after
  printf '%s\n' "$leftovers_to_name" | sed 's/^/  /'
  git -C "$worktree" reset -q --hard || loop_die "run: could not reset the kept worktree"
  git -C "$worktree" clean -qfd || loop_die "run: could not clean the kept worktree"
  # Neither verb above reaches a submodule the previous run's building left on another commit, which is dirt this
  # repair's own refusal names: the module's index already matches its own `HEAD`, so the reset has nothing to undo
  # there, and a module's path is tracked, so the clean leaves it alone. Measured 2026-10-25 in the kept worktree at
  # #309: `reset --hard` and `clean -fd` still printed the module path as modified, and
  # `submodule update --init --checkout <path>` printed nothing (issue #310). The path is the project's own, read
  # from the profile and only where the project carries a `.gitmodules`, and the checkout is best-effort with a
  # note exactly as the fresh worktree's is (D4).
  # …and the placement itself is `worktree.sh sync-module`, which also fetches a pin only the root tree's module holds
  # (measured 2026-10-28): `git -C"…" submodule update` here would be a second copy of that rule, and a second copy is a
  # second rule.
  if [ -n "$(loop_conf_value LOOP_SUBMODULE_PATH)" ] && [ -f "$worktree/.gitmodules" ]; then
    bash "$LOOP_DIR/worktree.sh" sync-module >/dev/null 2>&1 ||
      loop_note "$label: the submodule could not be checked out — the tree may still be dirty"
  fi
  # What is still dirty after both verbs and the checkout is dirt this repair cannot discard (a file inside the
  # module, say), so the repair says so and names it instead of announcing a clean tree the next start refuses.
  reset_after=$(git -C "$worktree" status --porcelain)
  if [ -n "$reset_after" ]; then
    printf '%s: the reset cannot discard what is left in %s:\n' "$label" "$worktree"
    printf '%s\n' "$reset_after" | sed 's/^/  /'
    return 1
  fi
  printf '%s: %s is clean again — the commits on its branches are left alone\n' "$label" "$worktree"
}
if [ -d "$worktree" ] && worktree_is_dirty; then
  leftovers=$(git -C "$worktree" status --porcelain)
  # The item the leftovers belong to: `start-item` writes the item's branch into the worktree's own branch
  # marker, so a tree a killed run left behind still names the item it was working on.
  marker=$(cat "$worktree/.pi/loop-branch" 2>/dev/null || true)
  owner_item=$(owner_of_marker "$marker")
  if [ "$reset_worktree" -eq 1 ] && [ "$dry_run" -eq 0 ]; then
    printf 'reset-worktree: discarding what a previous run left in %s:\n' "$worktree"
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
    printf 'the kept worktree held no work — only the module on another pin, and the pin its base records is placed\n'
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
  printf '%s\n' "$pin_out"
  stop_run "the base records a submodule pin no clone this host holds: $(printf '%s' "$pin_out" | head -1)"
  bash "$LOOP_DIR/report.sh"
  exit "$LOOP_REFUSE"
}
[ -z "$pin_out" ] || printf '%s\n' "$pin_out"

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
  printf 'refusing to start: the project tree carries uncommitted work, and the landing step merges into it\n'
  printf '%s\n' "$project_dirt" | sed 's/^/  /'
  printf 'the run cannot tell its own leftover from an edit made while it was not running, so nothing is discarded here\n'
  printf 'commit or discard these paths and run again — the list:\n'
  printf '  git -C %s status --porcelain\n' "$LOOP_ROOT"
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
  printf 'refusing to start: %s of the %s item(s) that await a decision are candidates, and the cap is %s\n' \
    "$open_decisions" "${labelled_decisions:-$open_decisions}" "$max_decisions"
  [ -z "$decision_list" ] || printf '%s\n' "$decision_list" | sed 's/^/  /'
  printf 'nothing new is produced while decisions accumulate — decide these first\n'
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
  printf '\ndry run: what would happen, and nothing else\n'
  jq -r '.queue[] | "  rank \(.rank)\(if .pinned then " pinned" else "" end) · #\(.issue) [\(.class)/\(.category)] \(.title)"' "$LOOP_STATE_FILE"
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
# this change at all. It runs after the item is done, and a closed item's branch is gone by then — the run
# merged it and `release-branch` deleted it a few lines earlier. Pushing a refspec whose local ref no longer
# exists fails with git's `src refspec … does not match any`, so a run asked to publish printed two error lines
# for the branch it had removed itself (measured 2026-10-23 on #277, inside an otherwise green run). The flag
# publishes what still exists; a branch whose work is in the base is a skip, and the run says which it did.
push_branch() { # push_branch <branch>
  if ! git -C "$worktree" rev-parse --verify --quiet "refs/heads/$1" >/dev/null; then
    printf 'run: nothing to publish for %s — the run merged it and released the branch, so its work is in the base\n' "$1" >&2
    return 0
  fi
  git -C "$worktree" push --set-upstream origin "$1" >&2 ||
    printf 'run: the push of %s failed — its commits stay in the run tree, not on the remote\n' "$1" >&2
}

# A report's own lines, with the fence a child may have put around its closing block taken off. A reviewer copies the
# closing block out of the prompt that shows it as a code block, and writes it as inline code — `` `LOOP-BLOCKING: 3` ``
# on one line and `` `VERDICT: FAIL` `` on the next — which is the same line, so one backtick at each end is removed
# before any field is read. Measured 2026-11-06 on item #788: both of its pre-gate reports ended exactly that way, each
# with the adapter's newline, and the run read a complete refusal as an answer the transport had cut (issue #793).
report_lines() { # report_lines <file>
  sed -e 's/^`//' -e 's/`$//' "$1" 2>/dev/null
}

report_field() { # report_field <file> <FIELD>
  report_lines "$1" | sed -n "s/^${2}: //p" | tail -1
}

# The word on the verdict line, and nothing else about it: the fence a child may have written around the line is not a
# cut answer, so every reader of `VERDICT:` reads the line where it stands (#793). A report that states no verdict
# prints nothing, which is the reading each caller already had for a report that never carried the line.
report_verdict() { # report_verdict <file> → PASS, FAIL, or empty
  local value
  value=$(report_field "$1" VERDICT)
  case "$value" in
    PASS*) printf 'PASS\n' ;;
    FAIL*) printf 'FAIL\n' ;;
  esac
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
# run read the absence as a refusal, took another worker round and escalated work that had passed. The fence a child
# may have put around that closing line is not a fact about its arrival: the line is read where it stands (#793).
answer_complete() { # answer_complete <report file> <closing-line regex>
  [ -s "$1" ] || return 1
  [ -z "$(tail -c 1 "$1" 2>/dev/null)" ] || return 1
  report_lines "$1" | grep -qE "$2" 2>/dev/null
}

# Whether a phase's own report is an answer at all (issue #810). A child that exits 0 and leaves a report with no
# closing line has answered nothing, and every arm the run decides an item from reads such a file as an answer it does
# not carry — so the shape of the answer is asked before any of them: the file, the newline the adapter prints after
# every answer, and the last line the phase's own prompt asks for. A child the run itself ended at a bound never returns 0
# (`LOOP_CHILD_TIMEOUT`, `tools/loop/lib.sh:27`), so the retry's `gate_exit -eq 0` guard leaves it as it stands (#224).
gate_answered() { # gate_answered <gate report> → 0 when the phase's answer arrived whole
  [ -z "$(report_field "$1" LOOP-TIMEOUT)" ] && [ -z "$(report_field "$1" LOOP-DEADLINE)" ] &&
    answer_complete "$1" '^LOOP-(GATE|VERDICT):'
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
# still a change of the tree the run promised to build on. `project_state` is read before and after every child,
# and what the two readings differ by is recorded (below) — a difference is evidence, not a verdict about the one
# child the run knows nothing about (#274, #276, #285).

# The difference between two readings, which is what a report needs: the lines one reading carries and the other
# does not. A constant sentence said only that the tree had changed, so neither the run nor a later reader of the
# item could tell which path to restore — or whether a path had changed at all, since the tree's own answer to
# "am I a work tree" travels in the same reading (issue #285).
project_state_diff() { # project_state_diff <before> <after> → the lines only one of the two readings carries
  { printf '%s\n' "$1"; printf '%s\n' "$2"; } | sed '/^[[:space:]]*$/d' | sort | uniq -u
}

# One clause out of a list of lines, quoted as it was read and bounded: a damaged tree carries thousands of
# `git status` lines, and a note is a sentence a person reads on the tracker.
outside_write_clause() { # <cap> — stdin: one line per item → `a; b; …`, `; and N more` past the cap
  awk -v cap="$1" 'NF { n++; if (n <= cap) printf "%s%s", (n > 1 ? "; " : ""), $0 }
                   END { if (n > cap) printf "%s and %d more", (cap > 0 ? "; " : ""), n - cap }'
}

# What changed, as the clause a note carries — the difference between the two readings, bounded. A constant
# sentence said only that the tree had changed, so neither the run nor a later reader could tell which path to
# restore (issue #285).
outside_write_clause_of() { # <before> <after> → one clause, quoted as read
  local paths
  paths=$(project_state_diff "$1" "$2" | cut -c1-120 | outside_write_clause 10)
  printf '%s' "${paths:-(no status line differs — only the work-tree answer changed)}"
}

# The processes of the tree the run started, asked of the kernel one call per level: the group the child leads
# (`set -m` before it is started) and everything descended from it, because a descendant that gave itself its own
# session is in no group the cap signals but is still this child's (the #272 survivor). One call per moved process
# instead of a sweep over every process on the host, which is what the attribution is read with and cannot afford:
# measured 2026-10-24 (issue #288), a host-wide sweep of 711 processes costs 1.0 s, so an answer read after it
# names processes that lived a second ago.
loop_tree_pids() { # <pid> → one pid per line: the child, its process group, and everything below it
  local child="${1:-}" frontier next seen=" " out="" pid
  [ -n "$child" ] || return 0
  frontier="$child $(pgrep -g "$child" 2>/dev/null | tr '\n' ' ')"
  while [ -n "${frontier// /}" ]; do
    next=""
    for pid in $frontier; do
      case "$seen" in *" $pid "*) continue ;; esac
      seen="$seen$pid "
      out="$out$pid"$'\n'
      next="$next $(pgrep -P "$pid" 2>/dev/null | tr '\n' ' ')"
    done
    frontier="$next"
  done
  printf '%s' "$out"
}

# Whether a pid still names the project as its working directory, and the worktree — the one tree a phase may
# write — as something else. The note's list and the attribution both ask it, so it is asked in one place.
pid_stands_outside_worktree() { # <pid> <project root> <worktree> → 0 when it does
  local cwd
  cwd=$(readlink "/proc/$1/cwd" 2>/dev/null) || return 1
  cwd=${cwd% (deleted)}
  [ -n "$cwd" ] || return 1
  case "$cwd" in "$2"|"$2"/*) ;; *) return 1 ;; esac
  case "$cwd" in "$3"|"$3"/*) return 1 ;; esac
  return 0
}

# The parent of a pid, read from `/proc/<pid>/stat` and not from `ps`: the attribution asks it for every ancestor
# of every candidate, and a fork per question would cost what the reading cannot pay (issue #288).
pid_ppid() { # <pid> → its parent, or nothing when the process is gone
  sed -n 's/^[0-9][0-9]* ([^)]*) [A-Z] \([0-9][0-9]*\).*/\1/p' "/proc/$1/stat" 2>/dev/null
}

# Whether a process got into the project's own tree *from* the worktree: an ancestor of it worked in the worktree,
# so the process that stands in the project deliberately left the tree it may write. This is what separates a
# writer from the run's own child chain, which `run_child` starts **in the project** — the child itself and every
# shell it was started through stand there for the whole item, so marking those would make every difference in
# the project's tree decide every item, which is the verdict #274 and #286 refused (issue #288).
pid_left_the_worktree() { # <pid> <worktree> → 0 when an ancestor of this pid worked in the worktree
  local p wt="$2" hops=0 cwd
  p=$(pid_ppid "$1")
  while [ -n "$p" ] && [ "$p" != "0" ] && [ "$p" != "1" ] && [ "$hops" -lt 64 ]; do
    # An ancestor that is already gone answers nothing; the walk continues, because what it stands for is the
    # chain, not that one process.
    cwd=$(readlink "/proc/$p/cwd" 2>/dev/null) || cwd=""
    case "${cwd% (deleted)}" in "$wt"|"$wt"/*) return 0 ;; esac
    p=$(pid_ppid "$p")
    hops=$((hops + 1))
  done
  return 1
}

# The processes the project shows that no phase may be working in: a survivor still names the tree it works in as
# its working directory, which is the question `loop_pids_under` already answers for the cap's own sweep (issue
# #272), and a working directory inside the worktree is a tree a phase *may* write. A caller that is itself
# standing in the project passes its own pid as an extra skip; the sweep is run from `/` so its own shell is never
# part of the answer.
outside_write_pids() { # <root> <worktree> [pid to skip …] → one `<pid> <command>` line per process
  local root="$1" wt="$2" pid skip s; shift 2
  for pid in $(loop_pids_under "$root"); do
    skip=0
    for s in "$@"; do [ "$pid" = "$s" ] && skip=1; done
    [ "$skip" -eq 1 ] && continue
    pid_stands_outside_worktree "$pid" "$root" "$wt" || continue
    printf '%s %s\n' "$pid" "$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | cut -c1-80 | sed 's/[[:space:]]*$//')"
  done
}

# The one line a child's transcript gains when the project's own tree moved while it ran: what changed, by the
# status line it changed to, and who else could have changed it (issue #285: #274's round-2 worker was blocked by a
# line that named no path, and the work was lost to a decision nobody could answer).
outside_write_line() { # outside_write_line <before> <after> → one `LOOP-OUTSIDE-WRITE: …` line
  local holders
  holders=$( cd / && outside_write_pids "$LOOP_ROOT" "$LOOP_WORKTREE" | cut -c1-120 | outside_write_clause 5 )
  printf 'LOOP-OUTSIDE-WRITE: the project tree changed while this process ran: %s' \
    "$(outside_write_clause_of "$1" "$2")"
  [ -z "$holders" ] || printf '; process(es) whose working directory is inside the project: %s' "$holders"
  printf '\n'
}

# What decides an item: a difference the run can tie to the tree it started — a process of the child's own tree
# was alive when the tree changed — or a run that declared the tree exclusive (`--strict-outside-write yes`). A
# difference it can tie to nothing is recorded and does not decide anything (issue #286).
outside_write_decides() { # → 0 when the recorded difference decides the phase
  [ -n "${outside_write_note:-}" ] &&
    { [ -n "${outside_write_attributed:-}" ] || [ "$strict_outside_write" = "yes" ]; }
}

# The difference read *while the child runs*, with the processes that were alive at that moment and the ones of
# them that belong to this child's own tree. A write is only evidence about the child when the two coincide, and
# the child is gone by the time its transcript is written — so the reading has to be taken during its life, from
# the heartbeat that is already ticking beside it (issue #286's attribution step).
#
# The child's own tree is therefore read FIRST and whole, and the sweep over the host comes after it (issue #288):
# the sweep is a `readlink` for every process on the machine — 1.0 s for 711 of them, measured 2026-10-24 — and a
# writer that was alive when the difference was read is gone by the time it ends, so an attribution read after it
# records a difference the run *can* place as one it cannot. The sweep answers who *else* stands in the project,
# which is context for a reader of the transcript and may cost what it costs; it must not be what decides.
outside_write_seen() { # <before> <now> <child> <own pid> → two `LOOP-OUTSIDE-WRITE-*` lines
  local before="$1" now="$2" child="$3" own="$4" alive attributed="" pid
  for pid in $(loop_tree_pids "$child"); do
    [ "$pid" = "$own" ] && continue
    pid_stands_outside_worktree "$pid" "$LOOP_ROOT" "$LOOP_WORKTREE" || continue
    pid_left_the_worktree "$pid" "$LOOP_WORKTREE" || continue
    attributed="${attributed:+$attributed,}$pid"
  done
  alive=$( cd / && outside_write_pids "$LOOP_ROOT" "$LOOP_WORKTREE" "$own" | cut -c1-120 | outside_write_clause 5 )
  printf 'LOOP-OUTSIDE-WRITE-FIRST: the project tree changed while this process ran: %s — read while the child was alive at %s\n' \
    "$(outside_write_clause_of "$before" "$now")" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'LOOP-OUTSIDE-WRITE-ALIVE: %s · attributed=%s\n' "${alive:-(none)}" "${attributed:-none}"
}

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
  # The tree the diff was written from, beside it (issue #819): the commit and a digest of the change, which is what the
  # gate's own record names for the tree it measured. A gate run before a correction and a diff written after it then
  # read as two trees, which is the whole of what the reviewer's item 3 asks — and the digest is `lib.sh`'s
  # `loop_change_identity`, derived from the run's own base whatever range this diff is taken over, so a correction
  # round's narrower diff is still compared against the change the gate measured. Both moments take that one derivation,
  # so both take one path set: the generated trees this diff leaves out leave the digest out too.
  loop_change_identity "$worktree" "${base:-$1}" > "$2.identity"
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

# How many turns the child running now has taken, read from the stream the adapter keeps for it (issue #686):
# `tools/pi-child-events.sh` appends one JSON object per event to the file `LOOP_CHILD_EVENTS` names, and a turn
# ends in exactly one `turn_end` line — the unit `tools/loop/state.sh` sums a child's spend per and the archive's
# 1.05–1.16 tool calls per turn are amortised over. A stream that is absent, empty or unreadable is no count
# rather than an error: a child whose command writes no events is bounded by its clock alone, as it was before,
# and a count this wait cannot take never ends a child.
child_turns() { # child_turns <events file> → the `turn_end` lines it holds, plus the turns a rotation dropped
  local n r
  [ -n "${1:-}" ] && [ -f "$1" ] || { printf '0\n'; return 0; }
  n=$(grep -cE '"type": *"turn_end"' "$1" 2>/dev/null) || true
  case "$n" in ''|*[!0-9]*) n=0 ;; esac
  # A rotated stream no longer holds the turns it dropped, and the count a bound compares against must not fall when
  # the writer rotates (issue #802): the record the adapter writes first carries them, cumulative, so a reader adds
  # what the stream says it dropped to what it still holds.
  r=$(sed -n 's/.*"type": *"stream_rotation".*"dropped_turn_ends": *\([0-9][0-9]*\).*/\1/p' "$1" 2>/dev/null |
    awk '{ sum += $1 } END { printf "%d", sum + 0 }')
  case "$r" in ''|*[!0-9]*) r=0 ;; esac
  printf '%s\n' "$(( n + r ))"
}

# The size of the conversation the child is paying for (issue #760), read from the same stream as the turn count and
# in the same whole second. A turn is not a unit of cost — every turn re-reads the whole context — so the reading
# that says what a further turn will cost is the largest one the stream has reported: `input + cacheRead` of a usage
# record (cumulative within a response, so the peak is what the window had to hold) or a `compaction_end`'s
# `tokensBefore` (the size it had to hold before it shrank), whichever is greater. It is the number
# `state.sh`'s `child_usage_json` takes for the report, taken while the child still runs.
#
# Only the complete lines appended since the last look are read: `wc -l` is the boundary, and a line still being
# written carries no newline, so a partial record is never parsed. That is what keeps a once-a-second reading cheap
# on a stream that runs to megabytes, where a re-parse of the whole file would spend on `jq` what the bound saves on
# tokens. A stream that is absent, empty or unreadable is no reading rather than an error, exactly as `child_turns`
# treats one: a child whose command writes no events stays bounded by its clock and by its turns.
child_context() { # child_context <events file> <lines already read> → "<largest context in the new lines> <lines read>"
  local f="${1:-}" done_lines="${2:-0}" total blob max
  [ -n "$f" ] && [ -f "$f" ] || { printf '0 0\n'; return 0; }
  total=$(wc -l < "$f" 2>/dev/null) || true
  case "$total" in ''|*[!0-9]*) total=0 ;; esac
  case "$done_lines" in ''|*[!0-9]*) done_lines=0 ;; esac
  [ "$total" -ge "$done_lines" ] || done_lines=0
  max=0
  if [ "$total" -gt "$done_lines" ]; then
    blob=$(tail -n +$((done_lines + 1)) "$f" 2>/dev/null | head -n $((total - done_lines)) | jq -s -c '
      (([.[] | select(.type == "message_update" or .type == "turn_end") | (.usage? // .message.usage?) | select(type == "object")
          | ((.input // 0) + (.cacheRead // 0))] | max) // 0) as $turn
      | (([.[] | select(.type == "compaction_end") | (.result?.tokensBefore? // empty) | numbers] | max) // 0) as $compacted
      | [$turn, $compacted] | max' 2>/dev/null) || blob=""
    case "$blob" in ''|*[!0-9]*) blob=0 ;; esac
    max=$blob
  fi
  printf '%s %s\n' "$max" "$total"
}

# Why a child that answered nothing may have answered nothing (issue #828): a provider that refuses every request
# leaves the proof inside the child's own event stream — a `turn_end` on `stopReason: error` carrying the provider
# and the model, and an `auto_retry_end` carrying the `finalError` — and the arm that records the run's "could not
# start" stop read none of it, so a reader of the stop reason could not tell a refused provider from a broken child
# (measured 2026-11-01: `.pi/issue-loop/items/85/events.jsonl` ends on a 429 weekly-limit refusal beside a 0-byte
# `worker-report.md`, and the run's stop named no part of it). Read from the tail of the stream, because the
# refusal is the newest thing in it and the file runs to megabytes; a stream that is absent, unreadable or carries
# no refusal is no reading rather than an error, exactly as `child_turns` treats one. The refusal's own text is
# squeezed to one line so the reason stays one line.
child_refusal() { # child_refusal <events file> → "<provider>/<model>: <refusal>" for the last refused request, or empty
  local f="${1:-}" out
  [ -n "$f" ] && [ -f "$f" ] || return 0
  out=$(tail -n 200 "$f" 2>/dev/null | jq -rs '
    [ .[] ] as $e
    | ($e | map(select((.finalError? // "") != ""
                        or (.message?.errorMessage? // "") != ""
                        or (.message?.stopReason? // "") == "error")) | last) as $refused
    | select($refused != null)
    | ($e | map(select((.message?.provider? // "") != "")) | last) as $named
    | (($named.message.provider // "")
       + (if ($named.message.model // "") != "" then "/" + $named.message.model else "" end)) as $who
    | (($refused.finalError // $refused.message.errorMessage // "")
       | gsub("[\\r\\n\\t]+"; " ")) as $why
    | select($why != "")
    | (if $who == "" then "" else $who + ": " end) + $why' 2>/dev/null) || out=""
  printf '%s' "$out"
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

# Which role a child plays, derived from the prompt's name — the same ones the fixture stub distinguishes
# (`worker`, `worker-rework`, `worker-gate`, `worker-tail`, `reviewer`, `reviewer-pre-gate`) and the naming `phase`
# already carries. The role names two configuration keys — `LOOP_AGENT_CMD_<ROLE>` and `LOOP_CHILD_TURNS_MAX_<ROLE>` —
# so a prompt file renamed for a round or a retry cannot move a child to another command or under another bound:
# `reviewer-prompt-2-retry.md` and `reviewer-prompt-1.md` are both `reviewer`. The order of the arms is the rule: a
# tail and the round before the gate are named before the plain worker and reviewer shapes they also match, and the
# rework round before the worker it is rendered from (issue #784).
#
# The rework round is a role and not a round (issue #821): the child a refusal buys answers a findings list and plans
# nothing, so it is priced and bounded apart from the item's first worker pass. What makes it a role is that the run
# renders its prompt at a site of its own — `worker-prompt-round2.md` — and the prompt's name is the only input
# `child_role` reads, so the arm reads the artifact the run already writes.
child_role() { # child_role <prompt-file> → worker | worker-rework | worker-gate | worker-tail | reviewer | reviewer-pre-gate
  case "${1##*/}" in
    *-tail*) printf 'worker-tail' ;;
    *pre-gate*) printf 'reviewer-pre-gate' ;;
    *gate*) printf 'worker-gate' ;;
    *round2*) printf 'worker-rework' ;;
    reviewer-*) printf 'reviewer' ;;
    worker-*) printf 'worker' ;;
    *) printf '%s' "$(basename "$1" .md)" ;;
  esac
}

run_child() { # run_child <prompt-file> <report-file> — every child goes through LOOP_AGENT_CMD (D3)
  # The child is bounded by the recorded wall-clock (`LOOP_CHILD_MAX_SECONDS`, default 2700 = 45 min) and not
  # only by the run's `--minutes`: the deadline is consulted between items, so a child that never returns
  # spends the whole budget inside one wait and nothing notices (measured 2026-10-19: an item's worker phase
  # ran 83 min that way — issue #203). A child that outlasts the cap is ended together with everything it
  # started and its transcript says so, so the run's ordinary "exited N" path records the item `blocked`.
  # A third bound lives beside it, the child's own turns (`LOOP_CHILD_TURNS_MAX_<ROLE>`, issues #686 and #784), and
  # it ends the child the same way: the wall clock says how long a child may run, not how much it spends failing to
  # converge, and each role is bounded by what that role costs rather than by what the item has already spent.
  # The working directory is the project and not the worktree on purpose: the project's own tool policy is
  # resolved at the directory the process starts in, and every path the child is handed — the work package,
  # its evidence directory, the change root its prompt names — lies inside the project. The tree it is told
  # to change is still the worktree, by the path in its prompt.
  local before after rc phase child_cmd role role_key role_cmd turns_max turns_base stop hb started interval poll cap child timed_out=0 sweep status_file ended_by deadline_epoch now last_second hb_base next elapsed turns ctx_lines=0 ctx_max=0 ctx_warned=0 ctx_read ctx_new
  # The patch the child this call ends left its work in, and nothing from the child before it: `keep_leftovers`
  # sets it, and the item's record must name the patch of the child that just ended (issue #674).
  leftover_patch=""
  before=$(project_state)
  phase=$(basename "$1" .md)
  # Which command starts this child — the same one indirection, one step finer, and now read from two dimensions rather
  # than one (issue #821): the role, its head, and the item's class, in that order of specificity
  # (`child_agent_cmd_key`, above). A role whose key the project recorded runs its children through it
  # (`LOOP_AGENT_CMD_REVIEWER`, say); a role that records none inherits the key of the role its name qualifies, and only
  # a role whose head records nothing keeps the single `LOOP_AGENT_CMD`, so a project that configures nothing runs one
  # command everywhere (the keys are empty in this project's `loop.conf`). What it buys is the difference the transcript
  # already measures: over 227 items (2026-11-03, `tools/loop-spend.py --json`) the reviewer's 7 522 turns carry 53.6 k
  # tokens each against the worker's 84.5 k at 25 654 turns, and the reviewer's job is a diff against the item's own
  # checklist. Which model a role runs on is a value this project records, not a fact the engine knows: no script under
  # `tools/loop/` names a model.
  child_cmd="$LOOP_AGENT_CMD"
  role=$(child_role "$1")
  role_key=$(child_agent_cmd_key "$role" "${class:-}")
  role_cmd=$(loop_conf_value "$role_key")
  if [ -n "$(printf '%s' "$role_cmd" | tr -d '[:space:]')" ]; then child_cmd="$role_cmd"; fi
  # …and the turns this role may spend, read from its own key rather than from one count the whole item shares (#784).
  turns_max=$(child_turn_bound "$role")
  # The stream every child of the item appends to already holds what the children before this one spent, so the bound
  # is read as a **difference**: the count standing here is the baseline and what this child is bounded by is the
  # turns it adds. Read before the child starts, so a turn it takes between the fork and this line is its own.
  turns_base=$(child_turns "${LOOP_CHILD_EVENTS:-}")
  # The worker is the child whose gate result the run reads out of the worktree, so it starts on a tree that
  # carries no other child's test results (issue #253). The **tail** child is the exception and is why this line is
  # not a `worker*` glob (issue #744): it is started *after* the gate wrote those results because the archive it
  # writes is read from them, so clearing them there would leave the item blocked on a gate result the run itself
  # had deleted. The **gate** child is the other exception (issue #758): the change child that went before it already
  # started on a cleared tree, and what stands in the tree now is that child's own focused runs, which the gate it is
  # about to run supersedes.
  case "$phase" in
    worker-tail*) ;;
    worker-gate*) ;;
    worker*) clear_stale_test_results ;;
  esac
  interval="${LOOP_PROGRESS_SECONDS:-30}"
  case "$interval" in ''|*[!0-9]*|0) interval=30 ;; esac
  # How often the wait *looks*, which is not how often it reports (issue #259): a whole second of granularity charged
  # every child 1–2 s of sleep — the poll, and the join of the heartbeat beside it — and one run case drives up to
  # three children. Validated like the two bounds: a value that is no positive number falls back to the default rather
  # than stopping the wait, and a bare `0` is not a busy loop.
  poll="${LOOP_POLL_SECONDS:-0.05}"
  case "$poll" in ''|.|*.|*[!0-9.]*|*.*.*) poll=0.05 ;; esac
  case "$poll" in *[1-9]*) ;; *) poll=0.05 ;; esac
  # The bound this child is given, derived once by `child_cap` from the profile's two recorded numbers (issue
  # #663): the header names the same value, so a reader of the run's own line and of the transcript that ends the
  # child reads one number.
  cap="$child_max"
  stop="$2.progress-stop"
  rm -f "$stop"
  : > "$2"
  rm -f "$2.outside-write"
  # The record the wait ends on is cleared **before** the child can write it (issue #227): cleared after the
  # launch, a child that finished inside the gap between the fork and the `rm` had its own completion record
  # deleted, and the run then waited the whole cap for a child that had already finished.
  status_file="$2.status"
  rm -f "$status_file"
  started=$(date +%s)
  printf 'loop: %s started (log %s)\n' "$phase" "$2"
  # `set -m` on the one command that starts the child, immediately undone: a background job of a shell with job
  # control is its own process group, which is what lets the cap end the child *and its descendants* — a Gradle
  # gate leaves daemons and test workers behind, and those are the memory the next item needs. Its stdin is
  # closed because a member of a background group that reads a terminal is stopped, not run, and an unattended
  # child has nobody to type at it anyway.
  set -m
  ( cd "$LOOP_ROOT" && $child_cmd "$1"; printf '%s\n' "$?" > "$2.status" ) > "$2" 2>&1 < /dev/null &
  child=$!
  active_child="$child"
  set +m
  ( hb_pid=$BASHPID
    hb_base=$SECONDS
    # The reading's own cadence, in whole seconds of this shell's clock (issue #673): the first reading is taken on
    # the first look, and the block below advances the next one to a whole second after each reading.
    read_next=$SECONDS
    next=$interval
    while [ ! -e "$stop" ]; do
      sleep "$poll"
      [ -e "$stop" ] && break
      # The difference is read *while the child runs*, because a writer is gone by the time the transcript is
      # written: only here can the run say which processes were alive at the moment the tree changed, and which of
      # them belong to this child's own tree. It is read **once per whole second of the child's life**, and not on the
      # reporting cadence (issue #673): an interval a person configures to read progress is not a resolution for
      # evidence, and a reading that arrives after a transient writer has gone places nothing — the case that pins a
      # placed write had to lower `LOOP_PROGRESS_SECONDS` to 1 s to be answered at all, which left it 1 s of phase
      # margin against the writer's own life and made it flaky. One reading per child once a difference is found — the
      # first difference is the one a reader of the item needs, and a tree another process keeps rewriting would
      # otherwise fill the item's directory.
      if [ ! -e "$2.outside-write" ] && [ "$SECONDS" -ge "$read_next" ]; then
        read_next=$(( SECONDS + 1 ))
        now=$(project_state)
        if [ "$now" != "$before" ]; then
          outside_write_seen "$before" "$now" "$child" "$hb_pid" > "$2.outside-write" 2>/dev/null || true
        fi
      fi
      # The heartbeat reports elapsed **seconds**, never the number of looks (issue #259): the wait looks twenty times
      # a second, so a count of iterations would shrink the interval along with the granularity and a configured 30
      # would have become 1.5 s of wall clock.
      elapsed=$(( SECONDS - hb_base ))
      [ "$elapsed" -ge "$next" ] || continue
      next=$(( elapsed + interval ))
      printf 'loop: %s running %s — log %s B, worktree %s changed file(s), %s commit(s) since the base\n' \
        "$phase" "$(fmt_elapsed "$elapsed")" "$(wc -c < "$2" 2>/dev/null || printf 0)" \
        "$(git -C "$LOOP_WORKTREE" status --porcelain 2>/dev/null | wc -l | tr -d ' ')" \
        "$(git -C "$LOOP_WORKTREE" rev-list --count "${base:-HEAD}..HEAD" 2>/dev/null || printf 0)"
    done ) >&2 &
  hb=$!
  active_hb=$hb
  # The wait ends on the **child's own completion record**, not on `kill -0` of its pid and not on `wait`: a child
  # that the shell has already reaped answers nothing, and a `wait` on a pid bash no longer tracks returns no
  # status, so a poll that believes the child is gone can leave the run blocked behind a `wait` for ever. Measured
  # 2026-10-20/21 (issue #227): fourteen runs sat inside this poll with only a `sleep` child, printing a heartbeat
  # every 30 s for up to 4 h 52 min — 606 lines, `log 0 B`, and not one `LOOP-TIMEOUT` line, although the cap was
  # 2700 s. The subshell writes its own status when it returns, which is the one event the run can wait for.
  last_second=""
  while [ ! -e "$status_file" ]; do
    # Both clocks this wait enforces — the cap and the run's deadline — are whole seconds, and so is the turn count
    # of the third bound below, so all three are read once per second and not once per look (issue #259, #686): a
    # `date`, a `jq` and a `grep` per wake-up would spend on forks what the finer
    # granularity buys back. The guard is one line on purpose — the checks below keep the indentation they had, so
    # the records that name them (`23.1`, `23.2`) still quote them (issue #264).
    if [ "$SECONDS" = "$last_second" ]; then sleep "$poll"; continue; fi
    last_second=$SECONDS
    now=$(date +%s)
    # The run's own deadline bounds this wait too (issue #227). The deadline was consulted between items, so a
    # child was the one clock the run could not enforce: measured 2026-10-20, eleven of fifteen stuck runs were
    # past their own `--minutes 90` and the longest heartbeat read 291m30s, with no `LOOP-TIMEOUT` line and no
    # stop reason — the budget was spent invisibly. The bound is read from the run's own state, which is the
    # record `report.sh` and `state.sh next` read, so the gate and the report cannot disagree about the clock.
    deadline_epoch=$(jq -r '.run.deadlineEpoch // 0' "$LOOP_STATE_FILE" 2>/dev/null)
    case "$deadline_epoch" in ''|*[!0-9]*) deadline_epoch=0 ;; esac
    if [ "$deadline_epoch" -gt 0 ] && [ "$now" -ge "$deadline_epoch" ]; then
      ended_by=deadline
      timed_out=1
      kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
      sleep 1
      kill -KILL -"$child" 2>/dev/null || true
      break
    fi
    if [ $(( now - started )) -ge "$cap" ]; then
      ended_by=cap
      timed_out=1
      kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
      sleep 1
      kill -KILL -"$child" 2>/dev/null || true
      break
    fi
    # …and the bound that is not a clock (issues #686, #784): a child that has spent its role's turns is a decision,
    # and one more turn does not make it converge. It is read from the stream the child appends to and checked in the
    # same whole second as the two clocks, so the wait costs one `grep` per second and not one per look.
    if [ "$turns_max" -gt 0 ]; then
      turns=$(child_turns "${LOOP_CHILD_EVENTS:-}")
      if [ $(( turns - turns_base )) -ge "$turns_max" ]; then
        ended_by=turns
        timed_out=1
        kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
        sleep 1
        kill -KILL -"$child" 2>/dev/null || true
        break
      fi
    fi
    # …and the bound priced in what a turn costs rather than in how many there are (issue #760), read from the same
    # stream and in the same whole second as the turn count, so the wait pays one `wc` and one `jq` over the *new*
    # lines per second. The checkpoint costs one line and decides nothing: it names the reading so a session that
    # could split at its gate knows it is worth more than the turns it saves. The recorded maximum ends the child,
    # because past it every further turn costs several times the first and that is a decision, not a slower
    # convergence. Both are off at `0`, which is why the whole block is skipped when neither key is recorded.
    if [ "$item_context_warn" -gt 0 ] || [ "$item_context_max" -gt 0 ]; then
      ctx_read=$(child_context "${LOOP_CHILD_EVENTS:-}" "$ctx_lines")
      ctx_new=${ctx_read%% *}
      ctx_lines=${ctx_read##* }
      case "$ctx_new" in ''|*[!0-9]*) ctx_new=0 ;; esac
      case "$ctx_lines" in ''|*[!0-9]*) ctx_lines=0 ;; esac
      if [ "$ctx_new" -gt "$ctx_max" ]; then ctx_max=$ctx_new; fi
      if [ "$item_context_warn" -gt 0 ] && [ "$ctx_warned" -eq 0 ] && [ "$ctx_max" -ge "$item_context_warn" ]; then
        ctx_warned=1
        printf 'LOOP-CONTEXT: the child\047s context reached %s tokens (LOOP_ITEM_CONTEXT_WARN records %s) — every further turn re-reads it, so a session split at the next gate is the cheaper half of this bound\n' \
          "$ctx_max" "$item_context_warn"
      fi
      if [ "$item_context_max" -gt 0 ] && [ "$ctx_max" -ge "$item_context_max" ]; then
        ended_by=context
        timed_out=1
        kill -TERM -"$child" 2>/dev/null || kill -TERM "$child" 2>/dev/null
        sleep 1
        kill -KILL -"$child" 2>/dev/null || true
        break
      fi
    fi
    # …and the one condition that is not a bound at all (issue #795): the record this wait ends on is written by
    # this shell's own subshell, so a `kill -0` on that pid is the only reading that tells a child still working
    # from a child whose writer was killed before it wrote the record. It is taken only while the record is absent,
    # and used only to end the wait: what #227 measured was a poll that believed a child gone — a pid the shell had
    # already reaped answers nothing, and a `wait` on it returns no status — so this reading never waits on the pid.
    # Measured 2026-11-06: the run started at 06:27:14Z was reparented to `systemd --user` when the session that
    # owned it died, sat in this poll 17 minutes into a 600-minute budget with its lock still held and its item still
    # `agent/in-progress`, and the cap would have ended the wait at 45 minutes — an item blocked for a dead child,
    # and a lock that would have refused every run after it.
    if [ ! -e "$status_file" ] && ! kill -0 "$child" 2>/dev/null; then
      ended_by=writer
      timed_out=1
      break
    fi
    sleep "$poll"
  done
  rc=0
  [ -e "$status_file" ] && rc=$(cat "$status_file" 2>/dev/null || printf 0)
  case "$rc" in ''|*[!0-9]*) rc=1 ;; esac
  # Nothing is left to a bare `wait`: a child that is still there after its own record was written, or after the
  # cap ended it, is ended here by pid, and the loop does not block on the shell's job table.
  if kill -0 "$child" 2>/dev/null; then loop_end_pid "$child"; fi
  active_child=""
  if [ "$timed_out" -eq 1 ]; then
    if [ "$ended_by" = deadline ]; then
      deadline_cut=1
      # The clock that ended this child is the run's budget, and the transcript says which one: "exceeded 58 s"
      # alone would name a cap that had 3 600 s to run, and the next run must read the deadline as the stop
      # instead of "the state being replaced never recorded a stop reason" (issue #227). The `LOOP-TIMEOUT` line
      # stays, because it is the one machine-readable line the run reads as its own timeout.
      deadline_at=$(jq -r '.run.deadlineAt // "an unrecorded time"' "$LOOP_STATE_FILE" 2>/dev/null)
      printf 'LOOP-TIMEOUT: the child exceeded %s s — the run%ss deadline at %s ended it and everything it started\n' \
        "$(( $(date +%s) - started ))" "'" "$deadline_at" >> "$2"
      printf 'LOOP-DEADLINE: the run%ss deadline passed at %s — the child running then was ended after %s s\n' \
        "'" "$deadline_at" "$(( $(date +%s) - started ))" >> "$2"
      stop_run "deadline passed at $deadline_at — the child running when it passed was ended"
    elif [ "$ended_by" = turns ]; then
      # The line the item's block is recorded from, and it says why in the item's own words: the bound, the role and the
      # key it is recorded in, and the count that reached it, so a person reading the blocked item does not have to
      # re-derive from a clock that had minutes left that the child was ended for what it spent (issue #686), nor
      # guess which phase had spent the item's pot (issue #784).
      printf 'LOOP-TIMEOUT: the child reached the recorded turn bound of %s turns (%s for the %s role) after %s turn(s) — the run ended it and everything it started\n' \
        "$turns_max" "LOOP_CHILD_TURNS_MAX_$(printf '%s' "$role" | tr '[:lower:]-' '[:upper:]_')" "$role" \
        "$(( $(child_turns "${LOOP_CHILD_EVENTS:-}") - turns_base ))" >> "$2"
    elif [ "$ended_by" = context ]; then
      # The reading the bound took, in the transcript the item is blocked on (issue #760), for the reason the turn
      # bound's line carries its count: a person reading the blocked item must not have to re-derive from an exit
      # status, on a clock with minutes left, which number ended the child. `LOOP-TIMEOUT` is the line the run reads
      # as its own timeout, so the reviewer-round and pre-gate paths that already read a cut transcript keep working.
      printf 'LOOP-TIMEOUT: the child reached the recorded context bound of %s tokens (LOOP_ITEM_CONTEXT_MAX) at %s tokens — the run ended it and everything it started\n' \
        "$item_context_max" "$ctx_max" >> "$2"
    elif [ "$ended_by" = writer ]; then
      # The event this wait ends on can only be written by a process that is running, so a writer that is gone is
      # the reason the wait ended — and the item's note must not read as a clock that fired (issue #795). The line
      # is the one the run reads as its own timeout, which is what records the item `blocked` with `bound` as its
      # mechanism instead of an escalation nobody asked for.
      printf 'LOOP-TIMEOUT: the child\047s writer (pid %s) vanished before it wrote its completion record — the run ended the wait\n' \
        "$child" >> "$2"
    else
      printf 'LOOP-TIMEOUT: the child exceeded %s s — the run ended it and everything it started\n' "$cap" >> "$2"
    fi
    # …and "everything it started" is more than the group: a descendant that gave itself its own session
    # (`setsid`) is a member of no group the signals above reach, so it kept mutating and restoring the
    # worktree for seven minutes after a run had recorded the child as ended, its last restore leaving that
    # tree dirty for the next run (measured 2026-10-22, issue #272). A survivor still names the tree it works
    # in as its working directory, which is the test `loop_pids_under` already owns (#213, #233), and each
    # process the sweep ends is named in the transcript a reader of the blocked item is handed.
    sweep=$(loop_end_pids_under "$LOOP_WORKTREE")
    if [ -n "$sweep" ]; then
      printf '%s\n' "$sweep" | sed 's/^/LOOP-ENDED: /' >> "$2"
    fi
    # …and the work it left is written down before the run moves on (issue #308): the item's own worker and the
    # rework worker of a refusing round both end here, both name the item the tree belongs to, and the tree itself is
    # still kept exactly as the child left it — the patch is a record *beside* it, never a replacement.
    keep_leftovers "$issue"
    if [ -n "$leftover_patch" ]; then
      printf 'LOOP-LEFTOVERS: %s — the work this run ended is kept there\n' "$leftover_patch" >> "$2"
      # …and this run now knows it may discard that tree to start its next item, because the record beside it is
      # one this run wrote (issue #664, the mid-run start refusal).
      own_leftovers="$own_leftovers$issue "
    fi
    rc="$LOOP_CHILD_TIMEOUT"
  fi
  : > "$stop"
  wait "$hb" 2>/dev/null
  active_hb=""
  rm -f "$stop"
  printf 'loop: %s finished in %s (exit %s, log %s B)\n' "$phase" \
    "$(fmt_elapsed $(( $(date +%s) - started )))" "$rc" "$(wc -c < "$2" 2>/dev/null || printf 0)"
  after=$(project_state)
  # What the in-time reading found, appended to the child's transcript so the item carries it: the paths as they
  # first differed, and the processes alive then with the ones of this child's own tree marked.
  outside_write_attributed=""
  outside_write_note=""
  if [ -s "$2.outside-write" ]; then
    outside_write_note=$(sed -n 's/^LOOP-OUTSIDE-WRITE-FIRST: //p' "$2.outside-write" | head -1)
    outside_write_attributed=$(sed -n 's/^LOOP-OUTSIDE-WRITE-ALIVE: .* · attributed=//p' "$2.outside-write" | head -1)
    case "$outside_write_attributed" in none|'') outside_write_attributed="" ;; esac
    cat "$2.outside-write" >> "$2"
  fi
  if [ "$before" != "$after" ]; then
    outside_write_line "$before" "$after" >> "$2"
    [ -n "$outside_write_note" ] ||
      outside_write_note="the project tree changed while this process ran: $(outside_write_clause_of "$before" "$after")"
  fi
  return "$rc"
}

# The gate is where the item's first child ends (issue #744). Measured on the archive (`python3 tools/loop-spend.py`,
# 2026-11-02): of the 274 sessions that ran a gate, 1.43 B of their 1.83 B tokens were charged **after** the first
# `LOOP-GATE` line — 13 700 turns at 104 k of context per turn, against 7 868 at 52 k before it — because everything
# the derivation had put into that session (the whole of Phase A, the plan, the focused rounds, the tool results each
# of them injected) is re-read on every turn of the archive-and-evidence tail it then performs. The tail is the same
# work whatever happened before the gate: it reads the change's `tasks.md`, the gate's line and its own failing
# output, so it is a **fresh child**, charged the project's baseline once instead of the first child's context once
# per turn. What the first child found travels as prompt values — the change, the root cause, the red test and the
# gate's line — so the tail re-derives none of it; and `tail_after_gate` returns non-zero when the report carries no
# gate line at all (a skip, a falsification, a child the run ended), because then there is no tail to run.
#
# The gate's own console is cut out of the first child's stream here, once: its tool output lives in the item's
# event file (`tool_execution_end` is what the gate printed), and its closing answer in its report. Both are bounded,
# because a child that re-read a 200-line transcript on every turn is what this split exists to stop paying for —
# `jq` and a `tail` cost the run two forks, and no turn of the tail is charged for the rest.
tail_after_gate() { # tail_after_gate <gate report> <tail prompt> <tail report> — sets tail_started, tail_exit
  local gate_report="$1" prompt="$2" report="$3" gate change_name root_cause red_test tasks output
  gate=""
  gate=$(report_field "$gate_report" LOOP-GATE)
  [ -n "$gate" ] || return 1
  change_name=$(report_field "$gate_report" LOOP-CHANGE)
  root_cause=$(report_field "$gate_report" LOOP-ROOT-CAUSE)
  red_test=$(report_field "$gate_report" LOOP-RED-TEST)
  # The tasks the change planned, as the path it sits at — the one file the tail reads to know what the gate was
  # verifying. A gate report that names no change the tree holds says so instead of handing the child a path that
  # is not there.
  tasks="none — the gate report names no change whose tasks.md this tree holds"
  case "$change_name" in
    ''|-) ;;
    *) if [ -f "$worktree/$changes_family/$change_name/tasks.md" ]; then
         tasks="$worktree/$changes_family/$change_name/tasks.md"
       fi ;;
  esac
  output="${report%.md}-gate-output.txt"
  {
    printf '# the gate child\047s tool output, where the gate it ran printed — the last 200 lines\n'
    if [ -n "${LOOP_CHILD_EVENTS:-}" ] && [ -f "$LOOP_CHILD_EVENTS" ]; then
      jq -r 'select(.type == "tool_execution_end")
             | [.result?.content[]? | select(.type == "text") | .text? // empty] | join("\n")' \
        "$LOOP_CHILD_EVENTS" 2>/dev/null | tail -n 200
    fi
    printf '\n# the gate child\047s own closing answer\n'
    tail -n 200 "$gate_report" 2>/dev/null
  } > "$output" 2>/dev/null || : > "$output"
  render_prompt "$LOOP_DIR/prompts/worker-tail.md" "$prompt" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
    "SCRATCH_DIR=${scratch_dir:-$item_dir/scratch}" \
    "BASE=$base" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" "GATE_CLAIM=$gate" "GATE_OUTPUT=$output" \
    "CHANGE_NAME=${change_name:--}" "TASKS_PATH=$tasks" "ROOT_CAUSE=$root_cause" "RED_TEST=$red_test"
  tail_started=1
  run_child "$prompt" "$report"
  tail_exit=$?
  return 0
}

render_prompt() { # render_prompt <template> <out> [KEY=VALUE]…
  local template="$1" out="$2"; shift 2
  loop_render "$template" "$@" > "$out"
}

# The one section of `tools/loop/prompts/worker.md` that changes with the run's review shape (`{{GATE_STEP}}`). It is
# a section of the worker's own prompt and not a second worker prompt, because everything else about the child — the
# route, Phases A–C, the report block — is the same in both shapes and two copies of it would be two rules.
#
#   gate-own.md       the change child runs the gate; one child from the diagnosis to the gate (the shape an item
#                     worked with `--review off` keeps)
#   gate-deferred.md  the change child's session ends at the diff, a fresh child runs the gate (issue #758)
#
# The run substitutes this **first**: the section names `{{GATE_CMD}}` and `{{EVIDENCE_DIR}}` itself, so a section
# inserted after those pairs would hand the child a path called `{{GATE_CMD}}`.
gate_step_text() { # → the `{{GATE_STEP}}` section for the shape <on|off> asks for
  if [ "${1:-off}" = on ]; then cat "$LOOP_DIR/prompts/gate-deferred.md"; else cat "$LOOP_DIR/prompts/gate-own.md"; fi
}

# The readiness verdict line for the change the run handed an item, read again when the package is rendered: the
# item's answer is the copy in the worktree, so the copy is what is judged. A project that declares no check, or a
# path that is not there, is named on the line rather than left blank — the block quotes a verdict, not a promise.
change_readiness_of() { # <change dir> → the checker's one-line verdict
  local dir="$1" gate gate_path out line
  gate=$(jq -r '.checks.changeReady // ""' "$(loop_profile)" 2>/dev/null | head -1)
  if [ -z "$gate" ]; then
    printf 'LOOP-CHANGE-READY: refused gate-not-configured — the profile declares no checks.changeReady\n'
    return 0
  fi
  case "$gate" in
    /*) gate_path="$gate" ;;
    *) gate_path="$LOOP_ROOT/$gate" ;;
  esac
  if [ ! -f "$gate_path" ]; then
    printf 'LOOP-CHANGE-READY: refused gate-not-configured — checks.changeReady names %s and no file is there\n' "$gate"
    return 0
  fi
  out=$(bash "$gate_path" "$dir" 2>&1) || true
  line=$(printf '%s\n' "$out" | grep -m1 '^LOOP-CHANGE-READY:' || true)
  printf '%s\n' "${line:-LOOP-CHANGE-READY: refused change-unreadable=$dir — the check at $gate printed no verdict line}"
}

# The round's prompt, rendered once per attempt: the same contract and the same diff, only the file it is written
# to differs, so a retried answer is the same question asked again rather than a second review (#224).
# `CORRECTION_SCOPE` and `PRE_GATE` are substituted **first**, not last: `loop_render` replaces one pair at a time on the
# template it has accumulated so far, and both sections name `{{DIFF_PATH}}` and `{{CHANGE_ROOT}}` themselves. A
# section inserted after those pairs would survive verbatim and hand the reviewer a file called `{{DIFF_PATH}}` — the
# round that exists to verify corrections pointing at a placeholder (item #245's round-1 review).
render_reviewer_prompt() { # render_reviewer_prompt <out file> [the corrections-only scope] [the pre-gate section]
  render_prompt "$LOOP_DIR/prompts/reviewer.md" "$1" \
    "CORRECTION_SCOPE=${2:-}" "PRE_GATE=${3:-}" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "DIFF_PATH=$diff_file" "DIFF_RANGE=$diff_base" "EVIDENCE_DIR=$item_dir" \
    "WORK_PACKAGE=${package:-}" \
    "REVIEW_EXCLUDE=${review_exclude_spec:-none}" "REVIEW_WALL_CLOCK=${child_max:-2700}"
}

# A review round the run's own bound ended is written on the branch as a **cut**, and the announcement of the same
# round is withdrawn in that one commit (issue #785): the branch is what a later run reads, and a round nobody
# answered beside a round the loop itself ended asks a person a question the loop already answered. Nothing is
# written for an item whose tree carries no archived change (`$change` empty) — there is no change to write against.
record_cut_round() { # <round>
  [ -n "${change:-}" ] || return 0
  bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$1" cut >&2 ||
    printf 'run: the cut of review round %s could not be recorded on the branch — a later run may read %s as a round nobody answered\n' \
      "$1" "$change" >&2
}

# The same statement for the other child the run ends: the rework worker of a refusing round (issue #788). Its
# record stays beside the refusal it reworks, because the round *was* asked and answered — withdrawing it would
# leave a branch a later pre-screen reads as a question its owner owes an answer to, and that question is the
# decision slot the run spent on itself.
record_rework_cut() { # <round>
  [ -n "${change:-}" ] || return 0
  bash "$LOOP_DIR/worktree.sh" record-review-round "$issue" "$change" "$1" rework-cut >&2 ||
    printf 'run: the cut of the rework of review round %s could not be recorded on the branch — a later run may read %s as a round nobody answered\n' \
      "$1" "$change" >&2
}

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
retry_delay_at() { # retry_delay_at <n> <delays> → the n-th delay, or the last one when there are fewer
  printf '%s\n' "$2" | awk -v n="$1" '{ v=$NF; for (i=1; i<=NF; i++) if (i==n) { v=$i; break } print v }'
}

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
    printf 'pause: %s — waiting %ss, then asking again\n' "$hold_reason" "$hold_seconds"
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
    printf 'run: the tracker could not be read for #%s — %s (recorded as unreadable, the run continues)\n' \
      "$issue" "${read_error:-the CLI gave no message}" >&2
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
    printf '%s\n' "$start_out"
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
        if own_leftovers_of "$owner_item" && discard_kept_worktree discard "$leftovers"; then
          printf 'discarding the work #%s left: this run ended that child and wrote it to %s, so #%s starts on a clean tree\n' \
            "$owner_item" "$(loop_leftover_patch_of "$owner_item")" "$issue"
          retry_start_item
        elif submodule_only_dirt "$leftovers" && place_submodule_pin; then
          # The module on another pin is not work: nothing inside it was written, so the pin the base records is placed
          # and the start is retried — the case a person met before as "the worktree is not clean" under no item.
          printf 'the tree was dirty only in the module%s gitlink — the pin the base records is placed, so #%s starts\n' \
            "'" "$issue"
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
    printf 'run: could not mark #%s in progress\n' "$issue" >&2
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
    printf 'run: could not record #%s as the item in progress\n' "$issue" >&2
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

  render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt.md" \
    "GATE_STEP=$(gate_step_text "$pre_gate")" \
    "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
    "SCRATCH_DIR=$scratch_dir" \
    "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
    "CLASS=$class" "PROPOSE_CLASS=$propose_class"

  started=$(date +%s%3N)
  run_child "$item_dir/worker-prompt.md" "$item_dir/worker-report.md"
  worker_exit=$?
  loop_state step A --name worker --status "$(if [ "$worker_exit" -eq 0 ]; then printf ok; else printf fail; fi)" \
    --note "exit $worker_exit" >/dev/null

  # The gate ends the child that ran it, and a second fresh child carries the evidence and the archive (issue
  # #744, `tail_after_gate` above). A child the run ended, or one that already answered for itself (a skip, a
  # falsification), has no tail: there is nothing to archive and nothing to carry, and the run reads the item's
  # report where it has always read it.
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
  # The child that ran the gate, and the report its result is read from: the item's change child, unless the run
  # deferred the gate to a child of its own below (issue #758).
  gate_report="$item_dir/worker-report.md"
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
  # after the item's first gate, 117 child sessions ran the gate three times or more, and a finding a reader could
  # have made from the diff alone was paid for twice: the gate it did not need, and the second full pass the rework
  # that answered it then made. The reviewer itself is cheap — 7 522 turns at 53.6 k tokens against the worker's
  # 84.5 k at 25 654 turns — so a round before the gate does not reduce how many rounds an item takes, it reduces
  # what a round costs.
  #
  # What stays where it was: the review round after the gate, because a change is judged after its verification as
  # well, and the tail that writes the archive. The round below is **not** a round of the review budget: it refuses
  # nothing, the item is never blocked by what it finds, and the round `--max-review-rounds` bounds is the one after
  # the gate. Its report stays in the item's directory and is named in the item's step, which is where a reader finds
  # what the change was asked to fix before its gate ran.
  #
  # A change child that ran the gate itself — one that ignored the prompt, or an item worked under the one-child
  # contract — leaves a gate line and no `LOOP-DIFF-READY`, and the item is read exactly as it was: a gate the run did
  # not run is not re-run, and a round before a gate that has already run has nothing to read.
  pre_gate_refusal=""
  pre_gate_refusal_verdict=""
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
  # Whether the phase that ran the gate answered nothing at all — measured in the phase itself, because a child that
  # exits 0 and writes no report is that phase's failure and no reading about the item's tree (issue #810). Empty for
  # every other shape, and kept beside `empty_tree_reading` for the same reason: it is decided before the outcome block
  # and it names a phase, not a verdict.
  phase_failure=""
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
        empty_tree_reading="the item ends at this reading, before any gate child: the change child stopped at the diff and the tree is still at the base commit, so nothing was committed"
        loop_state step A --name empty-tree --status ok --note "$empty_tree_reading" >/dev/null 2>&1 || true
        progress note "$empty_tree_reading"
      fi
      if [ "$head_now" != "$base" ]; then
        # The round's diff, its file and the pathspec are the post-gate round's own: one reviewer contract, one diff.
        review_exclude_spec=$(loop_conf_value LOOP_REVIEW_EXCLUDE_PATHSPEC)
        diff_base="$base"
        diff_file="$item_dir/review-pre-gate.diff"
        write_review_diff "$diff_base" "$diff_file"
        before=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
          { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
        pre_gate_report="$item_dir/pre-gate-review.md"
        render_reviewer_prompt "$item_dir/reviewer-pre-gate-prompt.md" "" \
          "$(cat "$LOOP_DIR/prompts/pre-gate-review.md")"
        run_child "$item_dir/reviewer-pre-gate-prompt.md" "$pre_gate_report"
        # An answer cut on its way to the file decides nothing, and it is the run's own transport rather than a
        # reviewer that stayed quiet, so the round is asked once more — the rule the rounds after the gate follow
        # (#224). A round a clock ended says so in its own report and is read as it stands.
        pre_gate_deadline=$(report_field "$pre_gate_report" LOOP-DEADLINE 2>/dev/null || true)
        if [ -z "$pre_gate_deadline" ] && [ -z "$(report_field "$pre_gate_report" LOOP-TIMEOUT)" ] &&
          ! answer_complete "$pre_gate_report" '^VERDICT: (PASS|FAIL)'; then
          printf 'run: the pre-gate answer was cut short (%s) — retrying the round once\n' \
            "$(last_line "$pre_gate_report")" >&2
          pre_gate_report="$item_dir/pre-gate-review-retry.md"
          render_reviewer_prompt "$item_dir/reviewer-pre-gate-prompt-retry.md" "" \
            "$(cat "$LOOP_DIR/prompts/pre-gate-review.md")"
          run_child "$item_dir/reviewer-pre-gate-prompt-retry.md" "$pre_gate_report"
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
          elif [ -n "$pre_gate_timeout_line" ]; then
            pre_gate_cut=1
            pre_gate_refusal_verdict="cut-short"
            # The ended child's own transcript names the bound and the figure it reached, and the reason on the item
            # quotes that line: a reader of the blocked item must not have to choose between the two bounds, or
            # re-derive from a transcript nobody handed them which number ended the round (issue #829).
            pre_gate_refusal="the run ended the pre-gate reviewer at one of its spend bounds — $pre_gate_timeout_line — the round was cut, not refused, so the change is left for the next run"
          elif ! answer_complete "$pre_gate_report" '^VERDICT: (PASS|FAIL)'; then
            pre_gate_refusal_verdict="cut-short"
            pre_gate_refusal="the pre-gate reviewer's report was cut short twice — the round's answer never arrived, so no reviewer refused this change"
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
        if [ "$pre_gate_verdict" = FAIL ] && [ -n "$blocking" ] && [ "$blocking" -gt 0 ]; then
          pre_gate_findings="$pre_gate_report"
        fi
        loop_state step A --name pre-gate-review --status ok \
          --note "the diff was read before the gate: $(if [ -n "$pre_gate_findings" ]; then printf '%s blocking finding(s) handed to the gate child' "$blocking"; else printf 'no blocking finding'; fi)" >/dev/null
        # The gate child is a phase of its own: it is handed what the change child established, and the findings when
        # there are any, and it decides nothing the change child already decided.
        gate_prompt_values=(
          "ISSUE=$issue" "CHANGE_ROOT=$worktree" "EVIDENCE_DIR=$item_dir" "BASE=$base"
          "SCRATCH_DIR=$scratch_dir"
          "GATE_CMD=$LOOP_GATE_CMD" "WORKER_SKILLS=$LOOP_WORKER_SKILLS"
          "CHANGE_NAME=${change_diff_ready:--}" "CHANGE_ANSWER=$(report_field "$gate_report" LOOP-CHANGE)"
          "ROOT_CAUSE=$(report_field "$gate_report" LOOP-ROOT-CAUSE)"
          "RED_TEST=$(report_field "$gate_report" LOOP-RED-TEST)"
          "FINDINGS_PATH=${pre_gate_findings:-none — the round read before the gate found no blocking finding}"
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
          printf 'run: the gate answer is not an answer (%s) — retrying the phase once\n' \
            "$(last_line "$gate_report")" >&2
          gate_report="$item_dir/worker-gate-report-retry.md"
          render_prompt "$LOOP_DIR/prompts/worker-gate.md" "$item_dir/worker-gate-prompt-retry.md" \
            "${gate_prompt_values[@]}"
          run_child "$item_dir/worker-gate-prompt-retry.md" "$gate_report"
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
  last_child="$gate_child"
  last_exit="$gate_exit"
  if [ "$tail_started" -eq 1 ]; then
    final_report="$tail_report"
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
      printf 'run: the %s could not start (retry %s of %s) — waiting %s s (LOOP_RETRY_MAX=%s, LOOP_RETRY_DELAYS=%s) before retrying #%s\n' \
        "$last_child" "$retry_spent_child_start" "$retry_max" "$retry_delay" "$retry_max" "$retry_delays" "$issue" >&2
      [ "$retry_delay" -eq 0 ] 2>/dev/null || sleep "$retry_delay"
      continue
    fi
    stop_run "the child could not start — the $last_child exited $last_exit with an answer of $answer_bytes B and no verdict line, leaving the tree at the base${child_words:+: $child_words}${refused_by:+ — refused by $refused_by}${retry_log_child_start:+; retried $retry_spent_child_start of $retry_max (LOOP_RETRY_MAX=$retry_max, LOOP_RETRY_DELAYS=$retry_delays) — $retry_log_child_start}"
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
  # Which of the two mechanisms stopped the item, when one did: `refused` for the last review round, `unmergeable`
  # for a base that would not take the branch. Both reach the record as `blocked`, and the record has no other
  # place to keep them apart (issue #280; measured 2026-10-23: 198.8 M of refusing rounds against 22.9 M of merge
  # refusals over the archive, distinguishable then only by reading the note). Every other block leaves it empty.
  stop_reason=""
  # The pre-gate round comes first because it stopped the item before any gate ran (issue #758): its reason names the
  # round, and an item it ended has no gate result for the arms below to read.
  if [ -n "$pre_gate_refusal" ]; then
    outcome="blocked"; blocked_reason="$pre_gate_refusal"
  elif [ "$last_exit" -ne 0 ]; then
    outcome="blocked"; blocked_reason="the $last_child process exited $last_exit"
    # A child the run itself ended says so in the item's own reason. "exited 124" alone leaves the owner unable
    # to tell a run that bounded a hung child from a child that failed by itself (issue #203).
    if [ -n "$timeout_claim" ]; then blocked_reason="$blocked_reason — $timeout_claim"; fi
    # …and the mechanism is `bound`, not a refusal (issue #786): the run ended this child at a bound it enforces, so
    # nothing about the item was measured and the next item's failure is not predicted by this one's. That one word
    # is what `state.sh next` reads to keep such a block out of the pair it stops a run on, and what the block's
    # own escalation below reads to write no decision label.
    if [ "$last_exit" -eq "$LOOP_CHILD_TIMEOUT" ] || [ -n "$timeout_claim" ]; then stop_reason="bound"; fi
  elif outside_write_decides; then
    outcome="blocked"; blocked_reason="the $last_child wrote outside the worktree — the project's own tree changed while it ran: $outside_write_note${outside_write_attributed:+ (attributed to this child: $outside_write_attributed)}"
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
    outcome="blocked"
    blocked_reason="${root_cause:+$root_cause — }the item's verdict is blocked${gate_claim:+ — the gate reported $gate_claim}"
  elif [ -n "$phase_failure" ]; then
    # A phase that answered nothing is that phase's failure (issue #810). The arms below read a report the phase never
    # wrote — its empty verdict is neither `skipped` nor `blocked`, and its empty change claim fails beside a commit
    # the change child did make — so the reading taken in the phase decides here, and the reason names the phase a
    # person has to look at instead of the item's tree.
    outcome="blocked"; blocked_reason="$phase_failure"
  elif [ "$head_now" = "$base" ]; then
    outcome="blocked"; blocked_reason="reported success but the tree is still at the base commit — nothing was committed"
  elif [ -z "$change" ]; then
    outcome="blocked"; blocked_reason="reported success but no archived change appears in the run's diff"
  elif [ -n "$LOOP_GATE_CMD" ] && [ "$gate_found" -eq 0 ]; then
    outcome="blocked"; blocked_reason="the report claims a gate result but no test-results XML exists in the tree"
  elif [ "$cited_evidence" -eq 1 ] && [ "$carried" -eq 0 ]; then
    outcome="blocked"; blocked_reason="the change cites a test result but carries no evidence in its own evidence/ directory"
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
  # The silence over a red gate is refused **here** (issue #811): the outcome is decided above, and this is the last
  # moment before the branch is merged and the item closed on the tracker. An item whose tally carries a nonzero
  # `failures=` or `errors=` and whose child recorded no waiver takes the blocked path every other refusal takes —
  # rather than the close, which would fail the write that records it after the tracker was already told the item
  # closed. `state.sh done` keeps the same refusal as its own backstop for a caller that is not this script, so the
  # clause is refused, never defaulted, in both places.
  if [ "$outcome" = "closed" ] && [ "$(gate_tally_state "$gate_field")" = red ] && [ -z "$gate_waived" ]; then
    outcome="blocked"
    stop_reason="refused"
    blocked_reason="the gate did not pass ($gate_field) and the close records no waiver — LOOP-GATE-WAIVED is the one clause the item closed over, and a close without it is a judgement nobody can read"
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
      before=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
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
        printf 'run: the round %s answer was cut short (%s) — retrying the round once\n' \
          "$round" "$(last_line "$reviewer_report")" >&2
        reviewer_report="$item_dir/reviewer-report-$round-retry.md"
        render_reviewer_prompt "$item_dir/reviewer-prompt-$round-retry.md" "$correction_scope"
        run_child "$item_dir/reviewer-prompt-$round-retry.md" "$reviewer_report"
        reviewer_deadline=$(report_field "$reviewer_report" LOOP-DEADLINE 2>/dev/null || true)
        if [ -z "$reviewer_deadline" ] && [ -z "$(report_field "$reviewer_report" LOOP-TIMEOUT)" ] &&
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
      after=$( { git -C "$worktree" status --porcelain; git -C "$worktree" diff; } |
        { if command -v sha256sum >/dev/null 2>&1; then sha256sum; else cksum | tr -d ' '; fi; } | cut -c1-16)
      if [ "$read_only" = "on" ] && [ "$before" != "$after" ]; then
        # The reviewer wrote in the tree it reads, so it cannot have been the reviewer: the round is discarded (D3).
        review_verdict="reviewer-wrote"
        outcome="blocked"; blocked_reason="the review round was discarded: the working tree changed while the reviewer ran"
        break
      fi
      # The project's own tree is read as well, by the child's own run (the in-time reading), and it is not the
      # reviewer's either — but it is not only the reviewer's to change either (#274, #276, #285, #286): a write
      # the run can tie to the reviewer's own tree, or any difference in a run that declared the tree exclusive,
      # discards the round; anything else is recorded and the round stands.
      if outside_write_decides; then
        review_verdict="reviewer-wrote"
        outcome="blocked"; blocked_reason="the review round was discarded: the project's own tree changed while the reviewer ran — $outside_write_note${outside_write_attributed:+ (attributed to this round: $outside_write_attributed)}"
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
        record_cut_round "$round"
        blocked_reason="the run's deadline passed while the reviewer of round $round was running — the round was cut, not refused, so the change is left for the next run"
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
        record_cut_round "$round"
        blocked_reason="the run ended the reviewer of round $round at one of its spend bounds — $reviewer_timeout_line — the round was cut, not refused, so the change is left for the next run"
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
      # FAIL: the same change goes back to a new worker process with the reviewer's findings. Its session runs the
      # gate itself (`gate_step_text off`): the round that refused it came *after* a gate, so there is nothing left to
      # read before one, and the round its repair buys is the next review round.
      findings=$(cat "$reviewer_report")
      render_prompt "$LOOP_DIR/prompts/worker.md" "$item_dir/worker-prompt-round2.md" \
        "GATE_STEP=$(gate_step_text off)" \
        "ISSUE=$issue" "CHANGE_ROOT=$worktree" "WORK_PACKAGE=$package" "EVIDENCE_DIR=$item_dir" \
        "SCRATCH_DIR=$scratch_dir" \
        "BASE=$base" "GATE_CMD=$LOOP_GATE_CMD" "MEASURE_CMD=$measure_cmd" "WORKER_SKILLS=$LOOP_WORKER_SKILLS" \
        "CLASS=$class" "PROPOSE_CLASS=$propose_class"
      printf '\n## The reviewer refused this change; change exactly these things\n\n%s\n' "$findings" \
        >> "$item_dir/worker-prompt-round2.md"
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
        outcome="blocked"
        blocked_reason="the round-2 worker wrote outside the worktree — the project's own tree changed while it ran: $round2_write_note"
        break
      fi
      if outside_write_decides; then
        # Named for what it is: a write outside the worktree, not a second failed review round.
        review_verdict="outside-write"
        outcome="blocked"
        blocked_reason="the round-2 tail worker wrote outside the worktree — the project's own tree changed while it ran: $outside_write_note${outside_write_attributed:+ (attributed to this child: $outside_write_attributed)}"
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
      outcome="blocked"
      stop_reason="refused"
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
  merged_sha=""
  if [ "$outcome" = "closed" ]; then
    merge_committed_branch "$issue" "fix/issue-$issue-$slug" || {
      outcome="blocked"
      stop_reason="unmergeable"
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
        ${waived_args[@]+"${waived_args[@]}"} \
        --closed "$closure" --root-cause "$root_cause" --red-test "$red_test" \
        --merged-branch "$merged_branch" --merged-commits "$merged_commits" --merged-sha "$merged_sha" \
        --events "$events_file" --note "$note_field" --review-verdict "$review_verdict" >/dev/null
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
        --events "$events_file" --note "the premise does not hold" --review-verdict "$review_verdict" >/dev/null
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
      loop_state done "$issue" --result skipped --verdict "${skip_verdict:-unspecified}" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --gate "$gate_field" \
        --root-cause "$root_cause" --red-test "$red_test" --note "skipped by the worker" \
        --events "$events_file" --decision-label "$escalated_label" --review-verdict "$review_verdict" >/dev/null
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
      # A child the run ended says in the item's own note that the attempt is the next run's to resume (issues
      # #786, #790): the tree it left was mid-phase, so nothing about the item was measured, and a reader of the
      # state — the next run's pre-screen among them — must not have to derive that from the absence of a label.
      if [ "$stop_reason" = bound ]; then
        cut_note="$cut_note · the run ended this attempt at a bound it enforces, so the item is left for the next run rather than asked about"
      fi
      if [ -n "$leftover_patch" ]; then
        cut_note="$cut_note · its work is kept at $leftover_patch"
      fi
      if [ -n "$label_failures" ]; then
        cut_note="$cut_note · the run could not write $label_failures to the tracker, so the item's state there is this run's account only"
      fi
      loop_state done "$issue" --result cut --verdict cut --change "$change_claim" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --gate "$gate_field" \
        --root-cause "$root_cause" --red-test "$red_test" --note "$cut_note" \
        --events "$events_file" --leftover "$leftover_patch" \
        --stop-reason "$stop_reason" \
        --label-failures "$label_failures" \
        --review-verdict "$review_verdict" >/dev/null
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
      loop_state done "$issue" --result blocked --verdict blocked --change "$change_claim" \
        --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
        --gate "$gate_field" \
        --root-cause "$root_cause" --red-test "$red_test" --note "$blocked_note" \
        --events "$events_file" --decision-label "$escalated_label" --leftover "$leftover_patch" \
        --label-failures "$label_failures" --stop-reason "$stop_reason" \
        --review-verdict "$review_verdict" >/dev/null
      ;;
  esac
  detail="${change:-$blocked_reason}"
  if [ "$outcome" = skipped ]; then detail="${skip_verdict:-unspecified}"; fi
  # A cut names the phase the run ended rather than the change it left: the change is the same one the item's branch
  # carries either way, and what a reader of the line has to tell apart is a cut from a block (issue #785).
  if [ "$outcome" = cut ]; then detail="${blocked_reason:-cut at a bound}"; fi
  printf 'item #%s: %s (%s) · %ss · %s review round(s)\n' "$issue" "$outcome" "$detail" "$wall" "$review_rounds"
  in_progress=""

  if [ "$push" = yes ]; then
    push_branch "fix/issue-$issue-$slug"
  fi
done

# ── the run's end ───────────────────────────────────────────────────────────────────────────────

end_run
exit "$LOOP_OK"
