#!/usr/bin/env bash
# state.sh — the run's state, its budget and its lock. The only writer of `.pi/issue-loop/run.json`.
#
# The state is what survives an interrupt: the budget, the base commit, the queue, the finished items
# and every step boundary. It lives under `.pi/` (machine-local, gitignored, never committed) and this
# script is its only writer, so a field that disagrees with the run was edited by hand — which is
# reported as a drift warning rather than silently obeyed (design D5).
#
# `init` replaces the whole state, so it keeps the state it replaces first: the file is copied under the run's
# own start time into the runs directory (`runs/` beside the state file, or `LOOP_RUNS_DIR` when the project
# records one), which is what lets `report.sh --state <file>` still describe a finished run after the next run
# has started (issue #207). A state that never recorded a stop reason is announced on stderr first: a crashed
# run is still restartable, but not silently.
#
# `next` exits 3 whenever the run may not start another item, and that exit code — not a caller's
# arithmetic — is the authority on whether an item may start: the item cap, the deadline, an empty queue, the
# recorded streak of consecutive refusals (`LOOP_REFUSAL_STREAK_STOP`, two when a project records none), or a
# remaining time below the next item's floor, which is derived from the cheapest wall time this run recorded for a
# finished item; `LOOP_ITEM_FLOOR_MINUTES` is the whole floor of a run with no such measurement (design D5,
# invariant 3). A block the run's own bound caused is not a refusal and holds no pair (issue #786): what the pair
# is read for is the prediction that the
# next item fails the same way, and a bound says nothing about the next item, which starts a fresh worker on a
# fresh tree. Exit 5 is a refusal that
# must not be retried inside the same reasoning: the lock is held, or the base has moved (design D12, D4).
#
# One condition is neither a stop nor a refusal: a hold (exit 6). The run may not start an item *yet* because
# something outside it — the provider's own quota, read through the project's `LOOP_QUOTA_CMD` — says so, and a
# wait clears it; `next` names the instant on its last line and `run.sh` pauses until then and asks again. A pause
# is not work, so it is **not** spent budget: `hold` moves the deadline with it.
#
# Exit: 0 ok, 2 usage, 3 stop, 5 refuse, 6 hold.
#
# Usage:
#   state.sh init --items N --minutes M [--review on|off] [--may-close yes|no]
#                 [--max-decisions N] [--max-review-rounds N] [--base <sha>]
#     `--base` defaults to the base branch's commit (`LOOP_BASE_REF`, `main` when unset), never the project
#     tree's checked-out commit.
#   state.sh show
#   state.sh get <jq-path>              # e.g. `get run.base`
#   state.sh set-base <commit>          # a finished attempt merged onto the base advances it
#   state.sh stop <reason>              # the condition that ended the run, as `next` names it
#   state.sh hold --seconds N [--reason TEXT]
#                         # a wait a condition outside the run imposed: the deadline moves with it, so the pause
#                         # is not spent budget, and the wait is kept in the run's record beside its stop
#   state.sh next                       # the next candidate; exit 3 when the run may not continue, 6 to wait
#   state.sh queue                      # read the queue as JSON on stdin (prescreen writes it)
#   state.sh start <issue> --change <name>
#   state.sh record-change-copy <issue> --change <name> --source <path> --files N --bytes N
#                         # the change the item names, copied into its worktree at its start (`worktree.sh`)
#   state.sh record-resume <issue> --patch <path> --outcome applied|no-apply
#                         # what the start found when it applied the patch a bound-ended child left (issue #754);
#                         # `done` folds the `no-apply` outcome into the item's note
#   state.sh step <phase> --name <name> --status running|ok|fail|skip [--note TEXT]
#   state.sh done <issue> --result done|skipped|blocked|falsified|cut [--verdict V] [--change C] [--note TEXT]
#                         [--wall SECONDS] [--pi-exit N] [--rounds N] [--gate TALLY]
#                         [--gate-waived CLAUSE] [--gate-waived-suite TEST]
#     `--gate-waived` is the one clause a close over a red tally was waived with (issue #811): a `done` whose
#     `--gate` carries a nonzero `failures=` or `errors=` is refused without it, because the judgement then exists
#     only in a child's transcript and no report can count it.
#                         [--events FILE] [--closed yes|no] [--root-cause TEXT] [--red-test TEXT]
#                         [--decision-label LABEL] [--leftover PATH] [--label-failures <transition>…]
#                         [--stop-reason refused|unmergeable|bound]
#                         [--merged-branch B] [--merged-commits LIST] [--merged-evidence TEXT] [--merged-sha SHA]
#                         [--review-verdict off|PASS|NOTES|FAIL|round2-ended|cut-short|deadline|reviewer-wrote|outside-write]
#   state.sh score-landings             # read the pipeline run each landing's own commit caused, once per landing
#   state.sh adoption-refused <issue> --reason TEXT
#                         # an admissible branch the tree, the base or the replay refused: the item's mechanism
#                         # `unmergeable` beside the pre-screen's exclusion, and no decision written for it
#   state.sh report
#   state.sh lock | unlock
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

STATE_FILE=""
STATE_DIR=""
RUNS_DIR=""
LOCK_FILE=""
FLOOR_MINUTES=45
# How many consecutive refusals a run stops on. The pair was the engine's own constant until issue #804: a refusal
# predicts the next item's failure (the same gate, the same provider, the same tree), but two is a prediction made
# from the shortest possible evidence — measured 2026-11-05/06, eight of twelve runs stopped on the pair with 55–90 %
# of their budget unused. The value is recorded (`LOOP_REFUSAL_STREAK_STOP`) rather than fixed here, so a run against
# a flaky provider can be told to try longer, and `2` remains the default for a project that records nothing.
STREAK_STOP=2

# `init` and `lock` resolve the project; every other command needs the state file to already exist, and
# resolves the same way so `LOOP_STATE_FILE` from an explicit environment wins in the self-test.
state_resolve() {
  # Resolved once per script: `loop_resolve` also loads `loop.conf`, and a second call would be a
  # second read of the configuration that produced the first answer.
  [ -n "$LOOP_ROOT" ] && return 0
  loop_resolve
  loop_roles_load
  STATE_FILE="$LOOP_STATE_FILE"
  FLOOR_MINUTES=$(loop_conf_value LOOP_ITEM_FLOOR_MINUTES)
  FLOOR_MINUTES="${FLOOR_MINUTES:-45}"
  STREAK_STOP=$(loop_conf_value LOOP_REFUSAL_STREAK_STOP)
  case "$STREAK_STOP" in ''|*[!0-9]*|0) STREAK_STOP=2 ;; esac
  STATE_DIR=$(dirname "$STATE_FILE")
  LOCK_FILE="$STATE_DIR/lock"
  # Where `init` keeps the state it replaces. Empty means `runs/` beside the state file — derived from the
  # state, never a project path, because the engine knows no project (D16). A recorded value is relative to
  # the project root unless it is absolute.
  RUNS_DIR=$(loop_conf_value LOOP_RUNS_DIR)
  case "$RUNS_DIR" in
    "") RUNS_DIR="$STATE_DIR/runs" ;;
    /*) ;;
    *) RUNS_DIR="$LOOP_ROOT/$RUNS_DIR" ;;
  esac
}

need_jq() {
  command -v jq >/dev/null 2>&1 || { echo "state: jq is required" >&2; exit "$LOOP_USAGE"; }
}

now_epoch() { date +%s; }
iso_utc() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }

# What the configuration file records, read from the file and never from the shell variable (issue #815): the
# adapter exports `LOOP_TOOL_RESULT_MAX_CHARS` into every child it starts, so a run started from inside one inherits
# the parent's bound, and `loop_conf_value` — which reads the sourced variable — would report it as this run's even
# where the configuration records none. Absent, empty or non-numeric is `0`, the "no bound" every reader of these
# keys already knows. This is the read `tools/pi-child-events.sh` and `tools/gate-attribute.sh` make of the keys they
# apply: read, never sourced.
recorded_bound() { # <key> → the number the run's configuration file records
  local value
  value=$(sed -n 's/^'"$1"'="\{0,1\}\([0-9][0-9]*\)"\{0,1\}[[:space:]]*$/\1/p' "$LOOP_CONF_PATH" | tail -1)
  case "$value" in ''|*[!0-9]*) value=0 ;; esac
  printf '%s' "$value"
}

digest_of() { # digest of a file's canonical JSON, without the digest field itself
  if command -v sha256sum >/dev/null 2>&1; then
    jq -cS 'del(.stateDigest)' "$1" | sha256sum | cut -c1-16
  else
    jq -cS 'del(.stateDigest)' "$1" | cksum | tr -d ' ' | cut -c1-16
  fi
}

write_state() { # $1 = the state as JSON, without `stateDigest`
  local json digest tmp
  json=$(printf '%s' "$1" | jq -cS 'del(.stateDigest)') || { echo "state: refusing to write malformed state" >&2; exit "$LOOP_USAGE"; }
  tmp=$(mktemp) || { echo "state: cannot create a temporary file" >&2; exit "$LOOP_USAGE"; }
  # The digest is taken from the file that is about to be written, through the same `jq -cS` pipeline
  # the reader uses: a digest computed over a slightly different serialisation would report every
  # untouched state as hand-edited.
  printf '%s' "$json" > "$tmp"
  digest=$(digest_of "$tmp")
  jq -cS --arg digest "$digest" '. + {stateDigest:$digest}' "$tmp" > "$tmp.signed"
  mv "$tmp.signed" "$tmp"
  mkdir -p "$STATE_DIR"
  mv "$tmp" "$STATE_FILE"
}

patch_state() { # $1 = jq program; remaining args are jq arguments (--arg/--argjson pairs first)
  local program="$1"; shift
  local json
  json=$(jq -cS "$program" "$@" "$STATE_FILE") || { echo "state: cannot update $STATE_FILE" >&2; exit "$LOOP_USAGE"; }
  write_state "$json"
}

require_state() {
  state_resolve
  [ -f "$STATE_FILE" ] || { echo "state: no run state at $STATE_FILE — run 'init' first" >&2; exit "$LOOP_USAGE"; }
  local stored actual
  stored=$(jq -r '.stateDigest // ""' "$STATE_FILE")
  actual=$(digest_of "$STATE_FILE")
  if [ -n "$stored" ] && [ "$stored" != "$actual" ]; then
    loop_note "state: drift — $STATE_FILE no longer matches the digest state.sh wrote ($stored → $actual); a field was edited outside this script"
  fi
}

# ── commands ─────────────────────────────────────────────────────────────────────────────────────

# The state a run leaves behind is the only record of what it attempted, what it spent and why it ended, and
# `init` writes over it. So the file is copied away before it is replaced, named by the start time it carries
# itself: two runs therefore never share an archive, and the name needs nothing but the run's own record
# (issue #207). A run interrupted or crashed never wrote a stop, so its state is still kept — announced on
# stderr, never refused.
keep_replaced_state() {
  [ -f "$STATE_FILE" ] || return 0
  local started path n=1
  started=$(jq -r '.run.startedAt // ""' "$STATE_FILE" 2>/dev/null)
  [ -n "$started" ] || started="undated-$(now_epoch)"
  path="$RUNS_DIR/$started.json"
  while [ -e "$path" ]; do
    path="$RUNS_DIR/$started.$n.json"
    n=$((n + 1))
  done
  mkdir -p "$RUNS_DIR" || loop_die "init: cannot create the runs directory $RUNS_DIR"
  cp "$STATE_FILE" "$path" || loop_die "init: cannot keep the state it replaces at $path"
  # `jq -e` fails on a missing or empty reason, and on a state no longer parseable at all: either way the run
  # being replaced never said why it ended.
  jq -e '.run.stop.reason // empty' "$STATE_FILE" >/dev/null 2>&1 ||
    loop_note "state: the state being replaced never recorded a stop reason — its run is kept at $path"
}

cmd_init() {
  state_resolve
  local items="" minutes="" review="" may_close="" max_decisions="" max_review_rounds="" base="" owner_pid=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --items) need_value "$@"; items="${2:-}"; shift 2 ;;
      --minutes) need_value "$@"; minutes="${2:-}"; shift 2 ;;
      --review) need_value "$@"; review="${2:-}"; shift 2 ;;
      --may-close) need_value "$@"; may_close="${2:-}"; shift 2 ;;
      --max-decisions) need_value "$@"; max_decisions="${2:-}"; shift 2 ;;
      --max-review-rounds) need_value "$@"; max_review_rounds="${2:-}"; shift 2 ;;
      --base) need_value "$@"; base="${2:-}"; shift 2 ;;
      --owner-pid) need_value "$@"; owner_pid="${2:-}"; shift 2 ;;
      *) loop_die "init: unknown argument $1" ;;
    esac
  done
  # An omitted flag takes its recorded default from loop.conf — the budget is a file plus arguments,
  # never a dialogue (D2, D16).
  [ -n "$items" ] || items=$(loop_conf_value LOOP_ITEMS_DEFAULT)
  [ -n "$minutes" ] || minutes=$(loop_conf_value LOOP_MINUTES_DEFAULT)
  [ -n "$review" ] || review=$(loop_conf_value LOOP_REVIEW_DEFAULT)
  [ -n "$may_close" ] || may_close=$(loop_conf_value LOOP_MAY_CLOSE_DEFAULT)
  [ -n "$max_decisions" ] || max_decisions=$(loop_conf_value LOOP_MAX_DECISIONS_DEFAULT)
  [ -n "$max_review_rounds" ] || max_review_rounds=$(loop_conf_value LOOP_MAX_REVIEW_ROUNDS_DEFAULT)
  case "$items" in ''|*[!0-9]*) loop_die "init: --items must be a non-negative integer" ;; esac
  case "$minutes" in ''|*[!0-9]*) loop_die "init: --minutes must be a non-negative integer" ;; esac
  case "$max_decisions" in ''|*[!0-9]*) loop_die "init: --max-decisions must be a non-negative integer" ;; esac
  case "$max_review_rounds" in ''|*[!0-9]*|0) loop_die "init: --max-review-rounds must be a positive integer" ;; esac
  case "$review" in on|off) ;; *) loop_die "init: --review must be on or off" ;; esac
  case "$may_close" in yes|no) ;; *) loop_die "init: --may-close must be yes or no" ;; esac
  # The base every item descends from is the base *branch*'s commit, not whatever the project tree has checked
  # out (issue #301): a machine somebody works on is on a feature branch, so reading `HEAD` here made the run
  # record that branch's commit and refuse its own first item as a stale base — after the whole pre-screen had
  # been paid for. Every other reader names the base branch (`loop_base_ref`); the checkout is not one of them.
  # A base branch that does not resolve is named instead of silently replaced: the fallback is the pre-fix value,
  # and every later reader turns a missing ref into "nothing to compare" (`check_base` notes it and proceeds).
  if [ -z "$base" ]; then
    if ! base=$(git -C "$LOOP_ROOT" rev-parse --verify --quiet "$(loop_base_ref)"); then
      loop_note "init: the base branch '$(loop_base_ref)' does not resolve — the base is the checked-out commit; name this project's base branch in LOOP_BASE_REF"
      base=$(git -C "$LOOP_ROOT" rev-parse HEAD 2>/dev/null) ||
        loop_die "init: cannot read the base commit"
    fi
  fi

  # The process that writes this state is named in it, so a reader can tell a run that is going from one
  # that was killed: a run that recorded no stop and whose process is not running any more is the second,
  # and the report says so instead of calling it a run that is still going (measured 2026-10-22: the run
  # killed at 19:19 left a state that read "the run is still going" with 217 minutes of budget left). The
  # run names its own pid; without one, the lock's owner is the run that is writing the state.
  case "$owner_pid" in ''|*[!0-9]*) owner_pid=0 ;; esac
  if [ "$owner_pid" -eq 0 ] && [ -r "$LOCK_FILE/owner" ]; then
    owner_pid=$(tr -cd '0-9' < "$LOCK_FILE/owner" 2>/dev/null)
    case "$owner_pid" in ''|*[!0-9]*) owner_pid=0 ;; esac
  fi

  keep_replaced_state
  local started deadline
  # The bounds an item's numbers are read against travel with the numbers (issue #815). Every figure the report
  # prints for an item is a comparison: its `context` column against the stop that ended it, its tokens against
  # `LOOP_ITEM_TOKENS_WARN`, and its tool results against the character bound the adapter cuts them at. Those three
  # numbers used to live only in `tools/loop/loop.conf`, which is a line a person edits between runs — so a run at
  # another bound (the one experiment #791's 200 104-token cut asks for) left a state whose figures nobody could read
  # back as the figures of *that* bound. Recorded once, and `0` where the configuration records none.
  local tool_result_max context_warn context_max
  tool_result_max=$(recorded_bound LOOP_TOOL_RESULT_MAX_CHARS)
  context_warn=$(recorded_bound LOOP_ITEM_CONTEXT_WARN)
  context_max=$(recorded_bound LOOP_ITEM_CONTEXT_MAX)
  started=$(now_epoch)
  deadline=$(( started + minutes * 60 ))
  write_state "$(jq -n \
    --argjson items "$items" --argjson minutes "$minutes" --argjson maxDecisions "$max_decisions" \
    --argjson maxReviewRounds "$max_review_rounds" --argjson ownerPid "$owner_pid" \
    --argjson started "$started" --argjson deadline "$deadline" \
    --argjson toolResultMaxChars "$tool_result_max" --argjson itemContextWarn "$context_warn" \
    --argjson itemContextMax "$context_max" \
    --arg review "$review" --arg mayClose "$may_close" --arg base "$base" \
    --arg startedAt "$(iso_utc "$started")" --arg deadlineAt "$(iso_utc "$deadline")" \
    '{run:{items:$items, minutes:$minutes, review:$review, mayClose:$mayClose,
           maxDecisions:$maxDecisions, maxReviewRounds:$maxReviewRounds, base:$base,
           toolResultMaxChars:$toolResultMaxChars, itemContextWarn:$itemContextWarn,
           itemContextMax:$itemContextMax,
           ownerPid:$ownerPid,
           startedAt:$startedAt, startedEpoch:$started,
           deadlineAt:$deadlineAt, deadlineEpoch:$deadline, holds:[]},
      queue:[], excluded:[], current:null, items:[], steps:[]}')"
  printf 'state: budget %s items / %s min · review %s · may-close %s · max-decisions %s · max-review-rounds %s · base %s\n' \
    "$items" "$minutes" "$review" "$may_close" "$max_decisions" "$max_review_rounds" "${base:0:12}"
  printf 'state: bounds result %s · context warn %s · context max %s\n' \
    "$tool_result_max" "$context_warn" "$context_max"
  printf 'state: deadline %s\n' "$(iso_utc "$deadline")"
}

cmd_get() {
  require_state
  [ $# -gt 0 ] || loop_die "get: usage: state.sh get <path>"
  # A bare path is accepted (`get run.base`) and a jq path is passed through (`.run.base`), because the
  # callers are shell scripts and quoting a leading dot at every call site is a place to forget it.
  local path="$1"
  case "$path" in .*) ;; *) path=".$path" ;; esac
  jq -r "$path | if type == \"string\" then . else tojson end" "$STATE_FILE"
}

cmd_show() {
  require_state
  # A run that wrote this state and is not running any more is not a run that is going: the state names the
  # process that wrote it, and the liveness of that process is what tells the two apart.
  local gone_stop="" owner_pid
  owner_pid=$(jq -r '.run.ownerPid // 0' "$STATE_FILE" 2>/dev/null || printf 0)
  case "$owner_pid" in ''|*[!0-9]*) owner_pid=0 ;; esac
  if [ "$owner_pid" -gt 0 ] && [ -z "$(jq -r '.run.stop.reason // ""' "$STATE_FILE" 2>/dev/null)" ] \
     && ! kill -0 "$owner_pid" 2>/dev/null; then
    gone_stop="the run that wrote this state is gone — its process (pid $owner_pid) is not running and it recorded no reason"
  fi
  jq -r --arg gone "$gone_stop" '
    (.run) as $r
    | "agent-issue-loop — " + (if .current == null then "no item in progress"
                              else "#\(.current.issue) \(.current.change) · phase \(.current.phase)" end),
      "budget   \($r.items) items / \($r.minutes) min · \($r.items - (.items | length)) left · deadline \($r.deadlineAt) · \((($r.deadlineEpoch - now) / 60 | floor)) min left\(if $r.deadlineEpoch <= now then " · EXPIRED" else "" end)",
      "flags    review \($r.review) · review rounds \($r.maxReviewRounds // "?") · may-close \($r.mayClose) · max-decisions \($r.maxDecisions) · base \($r.base)",
      "bounds   result bound \($r.toolResultMaxChars // "?") · context warn \($r.itemContextWarn // "?") · context max \($r.itemContextMax // "?")",
      "stop     \($r.stop.reason // (if $gone != "" then $gone else "not stopped — the run is still going, or it ended without recording a reason" end))",
      "progress closed \(.items | map(select(.result == "done")) | length) · mis-diagnosed \(.items | map(select(.result == "falsified")) | length) · skipped \(.items | map(select(.result == "skipped")) | length) · blocked \(.items | map(select(.result == "blocked")) | length) · cut \(.items | map(select(.result == "cut")) | length) of \($r.items)",
      "queue    \(.queue | length): \([.queue[].issue] | join(" "))",
      (if (($r.holds // []) | length) > 0 then "paused   \(((($r.holds | map(.seconds) | add) / 60) | floor)) min in \(($r.holds | length)) wait(s) — a pause moves the deadline, so it is not spent budget" else empty end),
      "excluded \(.excluded | length): \([.excluded[] | "#\(.issue) \(.reason)"] | join(" · "))",
      "",
      "items",
      (if (.items | length) == 0 then "  (none yet)"
       else (.items[] | "  #\(.issue) \(.result)/\(.verdict) \(.change // "-") · \(.metrics.wallSeconds // "-")s · rounds \(.metrics.reviewRounds // "-") · gate \(.metrics.gate // "-")") end)
  ' "$STATE_FILE"
}

cmd_next() {
  require_state
  local reason
  # The streak a run stops on counts **refusals**, never cuts and never a block the run's own bound caused
  # (issues #785, #786, #790): a phase the run's own bound ended is one the next run resumes, so an item recorded
  # `cut` is no more a stop than one that closed — and the mechanism is read beside the result too, because a state
  # a tree wrote before `cut` carried a bound still says `blocked` with `stopReason: bound` for the same fact. What
  # the streak is read for is that a refusal predicts the next item's failure — the same gate, the same provider,
  # the same tree — so only a refusal may sit in it. Measured 2026-11-05: five of six runs stopped on this streak,
  # 758 of 3211 budgeted minutes used across them, and six of that day's eight blocks were bound cuts (issue #786).
  # The count is the recorded one (`LOOP_REFUSAL_STREAK_STOP`, issue #804) and not the pair this line used to
  # hardcode, and the reason names the count it read — a stop of four must not be read as one of two.
  reason=$(jq -r --argjson streak "$STREAK_STOP" '
    (.run) as $r
    | (.items | length) as $done
    | if $done >= $r.items then "item cap reached (\($done)/\($r.items))"
      elif now >= $r.deadlineEpoch then "deadline passed at \($r.deadlineAt)"
      elif (.queue | length) == 0 then "queue empty"
      elif ([.items[-$streak:][] | select(.result == "blocked" and (.stopReason // "") != "bound")] | length) == $streak
        then "\($streak) consecutive refusals ended blocked"
      else "" end
  ' "$STATE_FILE")
  if [ -z "$reason" ]; then
    # The last stop condition is a clock, not a count: do not start an item the remaining budget
    # cannot pay for. What an item costs is not a constant — the run measures it, one `done --wall` at a
    # time — so the floor follows the **cheapest** item this run has finished: its wall time times the
    # spread the measurements show (1.5), rounded up to the whole minute. The cheapest outcome is the one
    # the run has proof it can pay, and it is what admits a tail of 10-15 min skips after a history that
    # also holds a 60-minute change; a median would let that one expensive item refuse the tail (issue
    # #674). `LOOP_ITEM_FLOOR_MINUTES` is the whole floor only for a run with no measurement — the
    # constant is no longer a lower bound of a derived floor — and the refusal names the cheapest wall
    # time, the count and that configured minimum (issue #206, design D5, #674).
    local minutes_left floor measured cheapest n items_word
    minutes_left=$(jq -r '((.run.deadlineEpoch - now) / 60 | floor)' "$STATE_FILE")
    measured=$(jq -r --argjson min "$FLOOR_MINUTES" '
      [.items[] | .metrics.wallSeconds | select(type == "number" and . > 0)] as $w
      | ($w | length) as $n
      | ($w | min) as $cheapest
      | (if $cheapest == null then null else ($cheapest / 60) end) as $minutes
      | (if $minutes == null then [$min, 0, 0]
         else [($minutes * 1.5 | ceil), $n, ($minutes | round)] end)
      | @tsv' "$STATE_FILE")
    IFS=$'\t' read -r floor n cheapest <<< "$measured"
    if [ "$minutes_left" -lt "$floor" ]; then
      items_word="finished items"; [ "$n" -eq 1 ] && items_word="finished item"
      if [ "$n" -eq 0 ]; then
        reason="only ${minutes_left} min left, below the ${floor} min floor for the next item (no finished item records a wall time, configured minimum ${FLOOR_MINUTES})"
      else
        reason="only ${minutes_left} min left, below the ${floor} min floor for the next item (cheapest ${cheapest} min over ${n} ${items_word}, configured minimum ${FLOOR_MINUTES})"
      fi
    fi
  fi
  if [ -z "$reason" ]; then
    # The last condition is outside the run: the provider's own balance, read through the project's
    # `LOOP_QUOTA_CMD` (empty for a project with no such reading — the check does not exist and nothing changes).
    # The command is this project's, the question is the engine's: the run owns *when* it is asked (a boundary
    # between two items) and the reader owns how it is answered (`tools/loop-quota.sh`). Exit 3 from the reader is
    # the hold this function passes on as its own exit 6, with the reader's lines — the reason and the instant it
    # names — kept verbatim; any other non-zero status is a check that could not be made and is **not** a refusal
    # (the rule the floor above and the run's host reading already follow), so the run proceeds and the failure is
    # named on stderr rather than ending a budget.
    local quota_cmd quota_out quota_status
    quota_cmd=$(loop_conf_value LOOP_QUOTA_CMD)
    if [ -n "$quota_cmd" ]; then
      quota_status=0
      quota_out=$( cd "$LOOP_ROOT" &&
        LOOP_QUOTA_SESSION_MIN_PERCENT="$(loop_conf_value LOOP_QUOTA_SESSION_MIN_PERCENT)" \
        LOOP_QUOTA_WEEKLY_MIN_PERCENT="$(loop_conf_value LOOP_QUOTA_WEEKLY_MIN_PERCENT)" \
        LOOP_QUOTA_PACING_RESERVE_PERCENT="$(loop_conf_value LOOP_QUOTA_PACING_RESERVE_PERCENT)" \
        $quota_cmd < /dev/null 2>/dev/null ) || quota_status=$?
      case "$quota_status" in
        0)
          # A reader that exits 0 may still have something to say: it does exactly when the balance could not be
          # read, which is the one case in which the run should know it is burning quota blind.
          [ -z "$quota_out" ] || loop_note "state: $quota_out"
          ;;
        "$LOOP_HOLD_READ")
          printf '%s\n' "$quota_out"
          exit "$LOOP_HOLD"
          ;;
        *)
          loop_note "state: the quota check '$quota_cmd' exited $quota_status — a check that cannot be made is not a refusal"
          ;;
      esac
    fi
  fi
  if [ -n "$reason" ]; then
    printf '%s\n' "$reason"
    exit "$LOOP_STOP"
  fi
  jq -r '.queue[0].issue' "$STATE_FILE"
}

cmd_queue() {
  require_state
  local json
  json=$(cat)
  printf '%s' "$json" | jq -e 'type == "array"' >/dev/null 2>&1 \
    || loop_die "queue: the queue must be a JSON array"
  # The queue, the exclusions and the per-item records arrive together: the pre-screen that produced
  # them is the run's own output, and an exclusion reported only to a console would be lost (D6).
  local queue excluded kept
  # What an exclusion of this run already carries, so a second pre-screen does not erase the run's own record.
  kept=$(jq -c '[.excluded[] | select(has("mechanism")) | {issue, mechanism, adoptionRefusal}]' "$STATE_FILE" 2>/dev/null || printf '[]')
  queue=$(printf '%s' "$json" | jq -c --arg queued "$(loop_role queuedVerdict)" '[.[] | select((.verdict // $queued) == $queued) | {issue, class, category, verdict, reason, rank, gates, pinned}]')
  # The exclusion carries the one clause of evidence that produced it (the branch a previous attempt left
  # behind, or the label that states why): the run escalates the decisions among the exclusions, and it cannot
  # cite evidence it threw away here. What the run itself decided about an exclusion before a re-screen arrives
  # — the mechanism `unmergeable` and the adoption's own refusal (issue #794) — is carried over: the pre-screen
  # reads the tracker and git again, and a branch it excluded twice is the same branch, so the run must not
  # escalate on its second reading what it recorded as work on its first.
  excluded=$(printf '%s' "$json" | jq -c --arg queued "$(loop_role queuedVerdict)" --argjson kept "${kept:-[]}" '[
    .[] | select((.verdict // $queued) != $queued)
    | {issue, class, verdict, reason, evidence, alreadyEscalated: (.alreadyEscalated // false)}
    | . as $e | ($kept | map(select(.issue == $e.issue)) | .[0] // {}) as $d
    | $e + (if ($d | has("mechanism")) then {mechanism: $d.mechanism, adoptionRefusal: ($d.adoptionRefusal // "")} else {} end)]')
  # An item this run has already recorded is never queued again: the pre-screen reads the tracker, and a
  # second pass over the same tracker would hand back the item the run has just finished with.
  patch_state '
    ([.items[].issue] | unique) as $handled
    | .queue = ($q | map(select(.issue as $i | ($handled | index($i)) | not)))
    | .excluded = $e' --argjson q "$queue" --argjson e "$excluded"
  printf 'queue: %s candidate(s), %s excluded\n' \
    "$(jq -r '.queue | length' "$STATE_FILE")" "$(jq -r '.excluded | length' "$STATE_FILE")"
}

cmd_start() {
  require_state
  local issue="" change=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --change) need_value "$@"; change="${2:-}"; shift 2 ;;
      --*) loop_die "start: unknown argument $1" ;;
      *) [ -z "$issue" ] || loop_die "start: issue given twice"; issue="$1"; shift ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "start: usage: state.sh start <issue> --change <name>" ;; esac
  [ -n "$change" ] || loop_die "start: --change is required"
  patch_state '.current = {issue:$i, change:$c, phase:"A"} | .steps += [{issue:$i, phase:"A", name:"start", status:"ok", note:"", at:$at}]' \
    --argjson i "$issue" --arg c "$change" --arg at "$(iso_utc "$(now_epoch)")"
  printf 'started #%s %s — phase A\n' "$issue" "$change"
}

# What the run copied into the item's worktree, recorded when the copy is taken (`worktree.sh start-item`) so the
# item carries the source it consumed and the clean command can report it after the item landed. It is keyed by the
# item and not hung on `.current`, because it is written before the item is started — the state's `start` names the
# item afterwards, and a record the copy itself made must survive that.
cmd_record_change_copy() {
  require_state
  local issue="" change="" source="" files="" bytes=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --change) need_value "$@"; change="${2:-}"; shift 2 ;;
      --source) need_value "$@"; source="${2:-}"; shift 2 ;;
      --files) need_value "$@"; files="${2:-}"; shift 2 ;;
      --bytes) need_value "$@"; bytes="${2:-}"; shift 2 ;;
      --*) loop_die "record-change-copy: unknown argument $1" ;;
      *) [ -z "$issue" ] || loop_die "record-change-copy: issue given twice"; issue="$1"; shift ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "record-change-copy: usage: state.sh record-change-copy <issue> --change <name> --source <path> --files N --bytes N" ;; esac
  [ -n "$change" ] || loop_die "record-change-copy: --change is required"
  case "$files" in ''|*[!0-9]*) files=0 ;; esac
  case "$bytes" in ''|*[!0-9]*) bytes=0 ;; esac
  patch_state '.changeCopies = ((.changeCopies // {}) | .[($i|tostring)] = {change:$c, source:$s, files:$f, bytes:$b, at:$at})' \
    --argjson i "$issue" --arg c "$change" --arg s "$source" --argjson f "$files" --argjson b "$bytes" \
    --arg at "$(iso_utc "$(now_epoch)")"
  printf 'change %s copied into the worktree for #%s (%s file(s), %s B) — source %s\n' \
    "$change" "$issue" "$files" "$bytes" "$source"
}

# What the start found when it applied the patch a child an earlier run ended at a bound left (issue #754). The loop
# commits nothing such a child did not claim, so the patch is the next attempt's input; this records the half the item
# would otherwise never say — that it began on that work, or that the patch no longer applies to the base and the item is
# therefore worked from the base. Measured 2026-11-08 on three kept patches that neither `git apply` nor `--3way` takes
# (items/791 exit 1, items/729 exit 1, items/16 exit 128): a non-apply that leaves no sentence is a resume a reader
# cannot tell from a run that never looked, and the work it could not use is the 7728 lines of #791's patch.
#
# It is keyed by the item and not hung on `.current`, because it is written before the run's `start` names the item — the
# same reason `changeCopies` is. The note is `done`'s to write: it reads this outcome there, so every arm that records an
# item — closed, blocked, cut — carries the sentence without repeating it.
cmd_record_resume() {
  require_state
  local issue="" patch="" outcome="" detail=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --patch) need_value "$@"; patch="${2:-}"; shift 2 ;;
      --outcome) need_value "$@"; outcome="${2:-}"; shift 2 ;;
      --detail) need_value "$@"; detail="${2:-}"; shift 2 ;;
      --*) loop_die "record-resume: unknown argument $1" ;;
      *) [ -z "$issue" ] || loop_die "record-resume: issue given twice"; issue="$1"; shift ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "record-resume: usage: state.sh record-resume <issue> --patch <path> --outcome applied|no-apply" ;; esac
  [ -n "$patch" ] || loop_die "record-resume: --patch is required"
  case "$outcome" in
    applied)
      detail="${detail:-the item starts on the work a child an earlier run ended at a bound left, applied from $patch}" ;;
    no-apply)
      detail="${detail:-the work a child an earlier run ended at a bound left in $patch does not apply to this base, so the item was worked from the base}" ;;
    *) loop_die "record-resume: --outcome must be applied or no-apply" ;;
  esac
  patch_state '.resumes = ((.resumes // {}) | .[($i|tostring)] = {patch:$p, outcome:$o, detail:$d, at:$at})' \
    --argjson i "$issue" --arg p "$patch" --arg o "$outcome" --arg d "$detail" \
    --arg at "$(iso_utc "$(now_epoch)")"
  printf 'resume: #%s — the kept patch %s %s\n' "$issue" "$patch" "$outcome"
}

# The base the run recorded is the commit every item descends from. A finished attempt merged onto it (issue
# #176) advances it by that merge's own commits, so the run records the new commit rather than refusing its
# own action as a stale base the next `start-item` would see. What the commit has to be is a commit the base
# *branch* carries: the closure that follows a merge is recorded against this base, so a commit the branch does
# not have would record `status/fixed-by` against a tree that never carried the fix — which is how the absorbed
# merge of issue #302 was recorded as an advance (the run re-read the unchanged base and `set-base` took it).
cmd_set_base() {
  require_state
  [ $# -gt 0 ] || loop_die "set-base: usage: state.sh set-base <commit>"
  local sha="$1" branch
  [ -n "$sha" ] || loop_die "set-base: the commit is empty"
  git -C "$LOOP_ROOT" rev-parse --verify --quiet "$sha^{commit}" >/dev/null ||
    loop_die "set-base: $sha is not a commit"
  branch=$(loop_base_ref)
  git -C "$LOOP_ROOT" rev-parse --verify --quiet "$branch^{commit}" >/dev/null ||
    loop_refuse "set-base: there is no base branch $branch to advance — the base every closure is recorded against has to be a branch this repository has"
  git -C "$LOOP_ROOT" merge-base --is-ancestor "$sha" "$branch" 2>/dev/null ||
    loop_refuse "set-base: $sha is not on $branch — recording it would close items against a base that never carried their fix"
  patch_state '.run.base = $sha' --arg sha "$sha"
  printf 'state: base advanced to %s\n' "${sha:0:12}"
}

cmd_stop() { # the condition that ended the run, as `next` names it
  require_state
  [ $# -gt 0 ] || loop_die "stop: usage: state.sh stop <reason>"
  local reason="$1"
  [ -n "$reason" ] || loop_die "stop: a stop names the condition that ended the run"
  patch_state '.run.stop = {reason: $reason, at: (now | todate)}' --arg reason "$reason"
  printf 'state: stop recorded — %s\n' "$reason"
}

# The wait a condition outside the run imposed, which `run.sh` sleeps through before it asks `next` again. The
# deadline moves with it, because the budget is what the run may *spend* and a pause spends nothing: without this
# a run whose provider resets in six hours would end at its own deadline while asleep and never resume. The wait is
# recorded as well, so a run that took nine hours to work three items can say how much of that was a pause — which
# is the reading the item's own wall times cannot give (they start and end inside a child, never over a hold).
cmd_hold() { # <seconds> [--reason TEXT]
  require_state
  local seconds="" reason="a condition outside the run"
  while [ $# -gt 0 ]; do
    case "$1" in
      --seconds) need_value "$@"; seconds="${2:-}"; shift 2 ;;
      --reason) need_value "$@"; reason="${2:-}"; shift 2 ;;
      *) loop_die "hold: unknown argument $1" ;;
    esac
  done
  case "$seconds" in ''|*[!0-9]*|0) loop_die "hold: --seconds must be a positive integer" ;; esac
  patch_state '.run.deadlineEpoch += $seconds
               | .run.deadlineAt = (.run.deadlineEpoch | todate)
               | .run.holds = ((.run.holds // []) + [{at: (now | todate), seconds: $seconds, reason: $reason}])' \
    --argjson seconds "$seconds" --arg reason "$reason"
  printf 'state: paused %ss — the deadline is now %s\n' "$seconds" "$(jq -r '.run.deadlineAt' "$STATE_FILE")"
}

cmd_step() {
  require_state
  [ $# -gt 0 ] || loop_die "step: usage: state.sh step <phase> --name <name> --status running|ok|fail|skip [--note TEXT]"
  local phase="$1"; shift
  local name="" status="ok" note=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --name) need_value "$@"; name="${2:-}"; shift 2 ;;
      --status) need_value "$@"; status="${2:-}"; shift 2 ;;
      --note) need_value "$@"; note="${2:-}"; shift 2 ;;
      *) loop_die "step: unknown argument $1" ;;
    esac
  done
  case "$phase" in prescreen|A|B|C|D|E|F) ;; *) loop_die "step: phase must be prescreen|A|B|C|D|E|F" ;; esac
  [ -n "$name" ] || loop_die "step: --name is required"
  case "$status" in running|ok|fail|skip) ;; *) loop_die "step: --status must be running|ok|fail|skip" ;; esac
  patch_state '.current = ((.current // {}) | .phase = $p)
    | .steps += [{issue:(.current.issue // 0), phase:$p, name:$n, status:$s, note:$note, at:$at}]' \
    --arg p "$phase" --arg n "$name" --arg s "$status" --arg note "$note" --arg at "$(iso_utc "$(now_epoch)")"
  printf 'phase %s/%s: %s\n' "$phase" "$name" "$status"
}

cmd_done() {
  require_state
  local issue="" result="" verdict="" change="" note="" wall="" pi_exit="" rounds="" gate="" gate_waived="" gate_waived_suite="" closed="" root_cause="" red_test="" decision_label="" leftover="" label_failures="" merged_branch="" merged_commits="" merged_evidence="" merged_sha="" events="" stop_reason="" review_verdict=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --result) need_value "$@"; result="${2:-}"; shift 2 ;;
      --verdict) need_value "$@"; verdict="${2:-}"; shift 2 ;;
      --change) need_value "$@"; change="${2:-}"; shift 2 ;;
      --note) need_value "$@"; note="${2:-}"; shift 2 ;;
      --wall) need_value "$@"; wall="${2:-}"; shift 2 ;;
      --pi-exit) need_value "$@"; pi_exit="${2:-}"; shift 2 ;;
      --rounds) need_value "$@"; rounds="${2:-}"; shift 2 ;;
      --review-verdict) need_value "$@"; review_verdict="${2:-}"; shift 2 ;;
      --gate) need_value "$@"; gate="${2:-}"; shift 2 ;;
      --gate-waived) need_value "$@"; gate_waived="${2:-}"; shift 2 ;;
      --gate-waived-suite) need_value "$@"; gate_waived_suite="${2:-}"; shift 2 ;;
      --events) need_value "$@"; events="${2:-}"; shift 2 ;;
      --closed) need_value "$@"; closed="${2:-}"; shift 2 ;;
      --root-cause) need_value "$@"; root_cause="${2:-}"; shift 2 ;;
      --red-test) need_value "$@"; red_test="${2:-}"; shift 2 ;;
      --decision-label) need_value "$@"; decision_label="${2:-}"; shift 2 ;;
      --leftover) need_value "$@"; leftover="${2:-}"; shift 2 ;;
      --label-failures) need_value "$@"; label_failures="${2:-}"; shift 2 ;;
      --stop-reason) need_value "$@"; stop_reason="${2:-}"; shift 2 ;;
      --merged-branch) need_value "$@"; merged_branch="${2:-}"; shift 2 ;;
      --merged-commits) need_value "$@"; merged_commits="${2:-}"; shift 2 ;;
      --merged-evidence) need_value "$@"; merged_evidence="${2:-}"; shift 2 ;;
      --merged-sha) need_value "$@"; merged_sha="${2:-}"; shift 2 ;;
      --*) loop_die "done: unknown argument $1" ;;
      *) [ -z "$issue" ] || loop_die "done: issue given twice"; issue="$1"; shift ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "done: usage: state.sh done <issue> --result done|skipped|blocked|falsified" ;; esac
  # `cut` is the fifth result, and it is not a shade of `blocked` (issue #785): a round the run's own bound ended
  # — the item's wall clock, the child's role's turns, or the run's deadline — is a phase a later run resumes,
  # not a finding about the item, so it carries no decision label, is not escalated and is not counted by the
  # two-blocked streak a run stops on. Measured 2026-11-05: #780's tail wrote `LOOP-VERDICT: closed`, its gate was
  # `tests=460 failures=0`, its change was archived, and review round 1 was cut at the turn bound — the item was
  # recorded `blocked` and escalated, as #783, #767 and #763 were the same day.
  case "$result" in done|skipped|blocked|falsified|cut) ;; *) loop_die "done: --result must be done|skipped|blocked|falsified|cut" ;; esac
  # The mechanism a block stopped on is a closed vocabulary, not a second place to write prose (issue #280: a
  # refusing review round and a base that would not take the branch both reached the record as the one word
  # `blocked`, so 198.8 M of refusing rounds could only be told from one 22.9 M merge refusal by reading the note).
  # `bound` is the third value (issues #786, #790): the run ended a child of the item at a bound it enforces — the
  # child's role's turns, the child clock or the run's own deadline — which is work the next run resumes and not a
  # finding about the item. Such an item is recorded `cut`, so `bound` is the one mechanism a cut carries, and the
  # spend a mechanism is totalled by is the same field on either result.
  case "$stop_reason" in
    ""|refused|unmergeable|bound) ;;
    *) loop_die "done: --stop-reason must be refused, unmergeable or bound" ;;
  esac
  # A mechanism on anything but a block would double-count the item as two kinds of stop, so it is refused too —
  # except `bound` on a `cut`, which is the same fact one result over (issue #790).
  [ -z "$stop_reason" ] || [ "$result" = blocked ] || { [ "$result" = cut ] && [ "$stop_reason" = bound ]; } ||
    loop_die "done: --stop-reason belongs to a blocked item, or to a cut one recorded bound, and this one is $result"
  # A landing's own commit is what attaches it to the pipeline run it caused (issue #709): the field is a commit or
  # nothing, so a value that is not one is refused here rather than stored — the score would otherwise read the
  # runs of a commit nobody landed, and the report would print that as the landing's verdict. 7 is git's shortest
  # abbreviation and 40 the length of a SHA-1, and both are accepted because a state written by hand is still
  # readable; what the run passes is the full commit its merge made.
  if [ -n "$merged_sha" ]; then
    case "$merged_sha" in
      *[!0-9a-f]*) loop_die "done: --merged-sha must be a commit (7-40 lowercase hex characters)" ;;
    esac
    [ "${#merged_sha}" -ge 7 ] && [ "${#merged_sha}" -le 40 ] ||
      loop_die "done: --merged-sha must be a commit (7-40 lowercase hex characters)"
  fi
  # The verdict of the round the item's review loop ended on — the fact the round *count* cannot express (issue #247):
  # `reviewRounds` is the round **attempted** (`run.sh:1680` sets it before the reviewer runs), so an item whose
  # round 1 refused and then blocked records `1` exactly as one whose round 1 passed. `run.sh` already decides these
  # values; recording them here is what lets a report tell a pass from a refusal from a round that ended without a
  # verdict. The vocabulary is that script's, so a value this list does not hold is a programming fault, not data.
  case "$review_verdict" in
    ""|off|PASS|NOTES|FAIL|round2-ended|cut-short|deadline|reviewer-wrote|outside-write) ;;
    *) loop_die "done: --review-verdict must be off|PASS|NOTES|FAIL|round2-ended|cut-short|deadline|reviewer-wrote|outside-write" ;;
  esac
  # A close over a red gate is a judgement, and this is where it is recorded (issue #811). Measured 2026-11-06: 3 of
  # the 7 items that run closed carry `errors=1` / `failures=1` beside `verdict: closed`, each waived in prose inside
  # its closing child's transcript — "file untouched by this diff", "green in the automotive run" — so the same flake
  # was diagnosed twice in one run and counted zero times, and a waived red printed the same `closed` a green gate
  # does. The clause is the close's own fact: it is refused on a block or a cut, whose answer is their decision, and
  # beside a tally that passed, where there is nothing to waive. `unknown` — the shapes that measured no test — takes
  # a waiver and does not require one, because the tally carries no count to read red out of.
  if [ -n "$gate_waived_suite" ] && [ -z "$gate_waived" ]; then
    loop_die "done: --gate-waived-suite names the suite a waiver's clause names; give --gate-waived beside it"
  fi
  if [ -n "$gate_waived" ]; then
    [ "$result" = done ] || loop_die "done: --gate-waived belongs to an item that closed, and this one is $result"
    [ -n "$gate" ] || loop_die "done: --gate-waived is a judgement about a tally, and no --gate was given"
    [ "$(gate_tally_state "$gate")" != green ] ||
      loop_die "done: --gate-waived was given for a gate that passed ($gate) — there is nothing to waive"
  elif [ "$result" = done ] && [ "$(gate_tally_state "$gate")" = red ]; then
    loop_die "done: the gate is red ($gate) and the close records no waiver — --gate-waived names the one clause it closed over"
  fi
  [ -n "$verdict" ] || verdict=$(if [ "$result" = done ]; then printf 'closed'; elif [ "$result" = cut ]; then printf 'cut'; else printf 'unspecified'; fi)
  # The class travels from the pre-screen into the record, so the report can rank without re-reading
  # the tracker; an item with no queue entry (a resumed run) falls back to the empty string.
  # `--decision-label` is the label the run's escalation *wrote* on the item — not the verdict the worker
  # chose, which is a different fact: a skip verdict of `needs-diagnosis` and a blocked item both end as
  # the decision label. The report names the decisions an item was left with from this field (issue #177).
  # `--label-failures` is the other half of that fact: the label transitions the tracker refused, so the item is
  # recorded as one whose state there is stale rather than as one the run escalated (issue #279).
  # `--stop-reason` is the mechanism a block stopped on, recorded so a mechanism's spend is totalled from the
  # state instead of from the note's prose; it is the blocked attempt's own fact, so a second account of the same
  # item joins only its note and never rewrites it. `--leftover` is the patch a child the run ended left its work
  # in: the transcript names it, and the state keeps it too, so the report can point a caller at the work without
  # a reader having to find the item's transcript first (issue #674).
  # The files the patch a run ended its child in carries (issue #809): the path said where the work is and not how
  # much of it, so a cap-ended item's kept work sat in its directory and in no figure the report could print. The
  # count is read from the file the caller names rather than passed in beside it, because the patch itself is the only
  # account of what it holds; a path that does not resolve records nothing, since `0 files` would read as a patch that
  # was measured and was empty.
  local leftover_files=""
  if [ -n "$leftover" ] && [ -r "$leftover" ]; then
    leftover_files=$(grep -c '^+++ ' "$leftover" 2>/dev/null)
    case "$leftover_files" in ''|*[!0-9]*) leftover_files="" ;; esac
  fi
  local class usage
  # The resume the start found (issue #754): the patch a bound-ended child left, and whether it applied. `done` is where
  # it joins the item's note, because the start runs before the run knows which arm will record the item and every one of
  # those arms writes its own prose. The sentence is prepended, so a note that already names the arm keeps its own words
  # after it.
  local resume_note
  resume_note=$(jq -r --argjson i "$issue" '
    ([(.items[] | select(.issue == $i))] | length) as $n
    | (.resumes[($i|tostring)] // {})
    | if $n == 0 then (.detail // "") else "" end' "$STATE_FILE" 2>/dev/null || true)
  [ -z "${resume_note:-}" ] || note="${resume_note}${note:+ · $note}"
  class=$(jq -r --argjson i "$issue" '[.queue[], .excluded[]] | map(select(.issue == $i)) | (.[0].class // "")' "$STATE_FILE")
  # What the item spent is read from the event stream its children wrote, and recorded beside the wall time so
  # the report has one source. The sums are the item's — every child of it appended to the same file — and a
  # figure nothing reported stays null, which the report prints as `-` (issue #211).
  usage=$(child_usage_json "$events")
  patch_state '
    # One item keeps one record (#284): the run records an item a second time when it finds that item own work
    # still in the worktree, and a second entry counted one item twice in every figure the report derives from
    # `.items` — the budget left and the four tallies — and made `next` read one block as two consecutive ones.
    # A second account therefore joins the record the item already has: its note is added and the escalation
    # label it earned is written, and nothing the first record established (its result, its metrics, its change)
    # is replaced by an account that arrived later.
    .items = (
      if ([.items[] | select(.issue == $i)] | length) == 0 then
        .items + [{issue:$i, class:$c, result:$r, verdict:$v, change:$change,
                  note:$note, leftover:(if $lo == "" then null else $lo end), at:$at,
                  leftoverFiles:(if $lof == "" then null else ($lof | tonumber) end),
                  stopReason:(if $sr == "" then null else $sr end),
                  escalatedLabel:$dl, labelFailures:($lf | split(" ") | map(select(length > 0))),
                  merged:{branch:$mb, commits:$mc, evidence:$me,
                          sha:(if $ms == "" then null else $ms end)},
                  metrics:{wallSeconds:$wall, piExit:$piExit, reviewRounds:$rounds,
                           reviewVerdict:(if $rv == "" then null else $rv end),
                           gate:$gate,
                           gateWaived:(if $gw == "" then null else $gw end),
                           gateWaivedSuite:(if $gws == "" then null else $gws end),
                           closed:$closed, rootCause:$rc, redTest:$rt,
                           tools:$usage.tools, calls:$usage.calls, turns:$usage.turns,
                           reads:$usage.reads, dupReads:$usage.dupReads, dupChars:$usage.dupChars,
                            repWithheld:$usage.repWithheld, repKept:$usage.repKept,
                           tokens:$usage.tokens, cost:$usage.cost,
                           context:$usage.context,
                           phases:$usage.phases, roles:$usage.roles, roleModels:$usage.roleModels,
                           afterGate:$usage.afterGate}}]
      else
        .items | map(if .issue == $i then
          .note = (if $note == "" then .note elif .note == "" then $note else .note + " · " + $note end)
          | .leftover = (if $lo == "" then .leftover else $lo end)
          | .leftoverFiles = (if $lof == "" then .leftoverFiles else ($lof | tonumber) end)
          | .escalatedLabel = (if $dl == "" then .escalatedLabel else $dl end)
          | .labelFailures = (((.labelFailures // []) + ($lf | split(" ") | map(select(length > 0)))) | unique)
        else . end)
      end)
    | .queue = [.queue[] | select(.issue != $i)]
    | (if $mb == "" then . else .excluded = [.excluded[] | select(.issue != $i)] end)
    | (if .current != null and .current.issue == $i then .current = null else . end)
  ' --argjson i "$issue" --arg c "$class" --arg r "$result" --arg v "$verdict" --arg change "$change" \
    --arg note "$note" --arg at "$(iso_utc "$(now_epoch)")" --arg dl "$decision_label" \
    --arg lo "$leftover" --arg lf "$label_failures" --arg sr "$stop_reason" --arg lof "$leftover_files" \
    --argjson wall "$(num_or_null "$wall")" --arg piExit "$pi_exit" --argjson rounds "$(num_or_null "$rounds")" \
    --arg rv "$review_verdict" \
    --arg gate "$gate" --arg closed "$closed" --arg rc "$root_cause" --arg rt "$red_test" \
    --arg gw "$gate_waived" --arg gws "$gate_waived_suite" \
    --argjson usage "$usage" \
    --arg mb "$merged_branch" --arg mc "$merged_commits" --arg me "$merged_evidence" \
    --arg ms "$merged_sha"
  printf 'recorded %s (#%s %s)\n' "$result" "$issue" "$verdict"
}

# What a landing's own commit caused is the one fact the state cannot derive and the report may not read from the
# tracker (issue #709): `gh run list --commit <sha>` answers the runs whose head is that commit, and the *push* run
# among them is the pipeline the landing triggered. Measured 2026-10-30 on this project's own landings
# (`openspec/changes/archive/2026-10-30-fix-landing-commit-is-scored/evidence/probe-gh-run-list.log`): a landing
# commit answers with its one `push` run, and one headSha carries a scheduled run beside it — so the newest run of a
# commit is not always the landing's, and the event filter is what makes the answer the landing's own.
#
# Three rules keep a stored number honest, the same three `report.sh` keeps:
#   * **one read per landing**, not per report and not per pass: an item that carries a score is not read again;
#   * **no run is not a green run**: no run yet, an unfinished run, or a tracker that cannot be read leaves the item
#     unscored and prints the reason, because a `false` written from an absent answer reads as a measurement;
#   * **the read cannot fail the item**: this runs on an item the run has already landed, so a network fault is
#     recorded, never raised, and the command's own status is 0 whether it read a run or not.
cmd_score_landings() {
  require_state
  local issue sha runs verdict reddened
  while IFS=$'\t' read -r issue sha; do
    [ -n "$issue" ] || continue
    if ! command -v gh >/dev/null 2>&1; then
      printf 'landing #%s %s: unscored — there is no gh on PATH to read the pipeline it caused\n' "$issue" "$sha"
      continue
    fi
    if ! runs=$(gh run list --commit "$sha" --limit 20 \
        --json databaseId,headSha,event,status,conclusion 2>/dev/null); then
      printf 'landing #%s %s: unscored — the pipeline runs of this commit could not be read\n' "$issue" "$sha"
      continue
    fi
    verdict=$(printf '%s' "$runs" | jq -r --arg sha "$sha" '
      [ .[] | select((.headSha // "") == $sha) | select((.event // "push") == "push") ] as $push
      | if ($push | length) == 0 then "none"
        elif ([$push[] | select((.status // "") == "completed")] | length) == 0 then "running"
        else (([$push[] | select((.status // "") == "completed")] | .[0].conclusion) // "")
        end' 2>/dev/null) || verdict="unreadable"
    case "$verdict" in
      none)
        printf 'landing #%s %s: unscored — no push run names this commit yet\n' "$issue" "$sha"
        continue ;;
      running)
        printf 'landing #%s %s: unscored — the push run of this commit has not finished\n' "$issue" "$sha"
        continue ;;
      unreadable|"")
        printf 'landing #%s %s: unscored — the pipeline runs of this commit could not be read\n' "$issue" "$sha"
        continue ;;
    esac
    # A run that ended in one of these three did not break the base; every other conclusion is a red pipeline (the
    # vocabulary `broken-main` files on: failure, cancelled, timed_out, startup_failure, error).
    case "$verdict" in
      success|skipped|neutral) reddened=false ;;
      *) reddened=true ;;
    esac
    patch_state '.items = [ .items[]
        | if .issue == $i and ((.merged.sha // "") != "") and .landedReddenedMain == null
          then .landedReddenedMain = $v else . end ]' \
      --argjson i "$issue" --argjson v "$reddened"
    if [ "$reddened" = true ]; then
      printf 'landing #%s %s: reddened main — its push run ended %s\n' "$issue" "$sha" "$verdict"
    else
      printf 'landing #%s %s: did not redden main — its push run ended %s\n' "$issue" "$sha" "$verdict"
    fi
  done < <(jq -r '.items[]
    | select((.merged.sha // "") != "") | select(.landedReddenedMain == null)
    | [(.issue | tostring), .merged.sha] | @tsv' "$STATE_FILE")
}

num_or_null() {
  case "${1:-}" in ''|*[!0-9.]*) printf 'null' ;; *) printf '%s' "$1" ;; esac
}

# What one item's children spent, read from the event stream they appended to (issue #211). The stream is the
# agent's: `tool_execution_end` names the tool that ran, `message_update.usage` carries the latest cumulative
# usage of one response, `turn_end` closes that response, and a compaction carries `tokensBefore`. Three rules
# make the numbers honest:
#
#   * **a turn's spend is its last usage record**, not the sum of its updates — `usage` is cumulative within a
#     response, so summing the updates would count the same tokens once per streamed chunk;
#   * **the largest context is a maximum, not a sum**: the biggest `input + cacheRead` any turn reported, or a
#     compaction's `tokensBefore` when one happened, because that is the size the window had to hold;
#   * **nothing reported is null, never zero.** A provider that streams no usage leaves zeros, and a zero
#     printed as a figure reads as a measurement nobody made; the report prints `-` for a null.
#
# The shape a session spent its budget in is two more counts (issue #757): the `tool_execution_end` lines are
# the calls and the `turn_end` lines are the turns, and `report.sh` reads their ratio. It is recorded as the two
# counts rather than as the ratio, because a ratio cannot say whether an item was slow or merely empty — and
# because an item's calls and turns are each summed over its children, the way its tokens are. A turn is every
# `turn_end`, not only the ones that reported a usage record: a child that reports no usage still pays a turn,
# and a ratio read over the reporting turns alone would flatter exactly the providers that measure least.
#
# Where and as whom the spend happened are three more figures (issue #759), and they are read from the same lines: a
# run that paid two thirds of its clock inside the gate command and a run that paid it reading printed the same
# shapes in the report, so the number was printed beside nothing it could be judged against (measured over 227 items,
# 2026-11-03: 89.6 h, model 30.1 h, the project's gate 35.1 h, the loop's own self-tests 11.6 h; 1 581 of 2 591 Mtok
# spent after the item's first gate, reviewer 402 Mtok against the worker's 2 168).
#
#   * **`phases`** — the model time is the sum of the turns' own `durationMs`, the gate the sum of the
#     `tool_execution_end` durations of the calls whose command runs the gate, and every other tool's durations are
#     the third bucket. Which command is the gate is the project's declaration (`LOOP_GATE_CMD`), read as its
#     program's name — never a name this engine carries (`run#engineNamesNoProjectFact`). The three are disjoint: a
#     turn's duration is the model response, the tool's duration the execution that follows it, so they add up to
#     the clock the item's children spent working;
#   * **`roles`** — each `session` record opens one child's stream and its first `user` message is the prompt file
#     the run handed it, which is the only thing in the stream that names the role. The path does: `worker-tail` is
#     the tail, `reviewer` the reviewer, everything else a worker. Read from the prompt's *words* (the rule
#     `tools/loop-spend.py` uses) the tail folds into the worker, because its prompt is `worker-tail-prompt.md`;
#   * **`afterGate`** — over the sessions that ran a gate, the tokens of the turns from the first gate turn on and
#     those sessions' own tokens, so a share has both halves in the record.
#
# Each is null where nothing was measured, never zero: a stream with no `session` record has no role split, one with
# no `durationMs` no phase figure, and one that ran no gate no position to split at. A stream that holds none of the
# records above — a plain-text command, or another agent's format — yields a null for every figure, so the engine
# reads a child's dialect without depending on it.
child_usage_json() { # child_usage_json <events-file> → {tools,calls,turns,tokens,cost,context,phases,roles,roleModels,afterGate,reads,dupReads,dupChars,repWithheld,repKept}
  local events="${1:-}" blob="" gate_cmd gate_prog="" word rep_record rep_withheld="null" rep_kept="null"
  # The gate is the project's declaration, not a name this engine may carry: its program is the first word of
  # `LOOP_GATE_CMD` that names a path, or its first word when none does — `tools/loop/gate.sh`,
  # `bash tools/loop/gate.sh` and a bare command all reduce to the program a child's tool call names.
  gate_cmd=$(loop_conf_value LOOP_GATE_CMD)
  for word in $gate_cmd; do
    case "$word" in -*) continue ;; esac
    case "$word" in */*) gate_prog="${word##*/}"; break ;; esac
    [ -n "$gate_prog" ] || gate_prog="$word"
  done
  if [ -n "$events" ] && [ -f "$events" ]; then
    # What the repeat rule withheld is recorded beside the stream (issue #831): every replacement `repeatResult`
    # makes appends a `LOOP-TOOL-RESULT-REPEAT` line to the item's trim record naming the characters the child
    # asked for and the one line it was handed instead. The event stream cannot answer that — it records the result
    # the tool returned either way — so the figure is read from the record, and a stream whose item kept no record
    # leaves both null rather than a zero that would read as "the rule withheld nothing".
    rep_record="$(dirname "$events")/tool-result-trims.log"
    if [ -f "$rep_record" ]; then
      rep_withheld=$(sed -n 's/^LOOP-TOOL-RESULT-REPEAT: .*chars=\([0-9][0-9]*\) kept=\([0-9][0-9]*\)$/\1/p' "$rep_record" \
        | awk '{s += $1} END {print s + 0}')
      rep_kept=$(sed -n 's/^LOOP-TOOL-RESULT-REPEAT: .*chars=\([0-9][0-9]*\) kept=\([0-9][0-9]*\)$/\2/p' "$rep_record" \
        | awk '{s += $1} END {print s + 0}')
    fi
    blob=$(jq -s -c --arg gate "$gate_prog" --argjson repw "$rep_withheld" --argjson repk "$rep_kept" '
      def turns:
        reduce .[] as $e ({cur:null, done:[]};
          if $e.type == "message_update" and (($e.usage? // null) | type) == "object" then .cur = $e.usage
          elif $e.type == "turn_end" then ((if .cur != null then .done = (.done + [.cur]) else . end) | .cur = null)
          else . end)
        | if .cur != null then (.done + [.cur]) else .done end;
      # The session is the phase, and a turn is where its spend and its clock are: the usage it ended on (the rule
      # `turns` keeps), its own `durationMs`, and the calls it made — the last is what a tool duration is attributed
      # by, because `tool_execution_end` names its call and carries no command of its own.
      def tok: if . == null then 0
               else ((.input // 0) + (.output // 0) + (.cacheRead // 0) + (.cacheWrite // 0)) end;
      def is_gate: (($gate | length) > 0)
                   and ([((. // "") | tostring) | splits("[ \\t]+")]
                        | any(.[]; . == $gate or endswith("/" + $gate)));
      def calls_of: [((. // [])[] | select(.type == "toolCall")
                      | {id: (.id // ""), gate: (((.arguments?.command? // "") | tostring) | is_gate)})];
      def sessions:
        reduce .[] as $e ({cur:null, done:[]};
          if $e.type == "session" then
            (if .cur != null then .done = (.done + [.cur]) else . end)
            | .cur = {prompt:null, usage:null, turns:[]}
          elif .cur == null then .
          elif $e.type == "message_start" and (($e.message.role? // "") == "user") and (.cur.prompt == null) then
            .cur.prompt = (($e.message.content // "")
              | if type == "array" then (map(.text? // "") | join("")) else tostring end)
          elif $e.type == "message_update" and (($e.usage? // null) | type) == "object" then
            .cur.usage = $e.usage
          elif $e.type == "turn_end" then
            (($e.message // {}) as $m
             | .cur.turns = (.cur.turns + [{usage: (.cur.usage // ($m.usage? // null)),
                                            ms: ($m.durationMs? // 0),
                                            # The model the turn answered with (issue #823): the same message that
                                            # carries the usage carries `model`, and `responseModel` when the
                                            # provider reported a concrete one, so the reader takes the concrete one
                                            # and falls back to the requested one rather than choosing between them.
                                            model: (($m.responseModel? // $m.model? // null)
                                                    | if type == "string" and length > 0 then . else null end),
                                            calls: ($m.content | calls_of)}])
             | .cur.usage = null)
          else . end)
        | (if .cur != null then (.done + [.cur]) else .done end);
      . as $all
      | ($all | turns) as $t
      | ($all | sessions) as $ses
      | ([$all[] | select(.type == "turn_end") | (.message.durationMs? // 0)] | add // 0) as $modelMs
      | (reduce ($all[] | select(.type == "turn_end") | (.message.content // [])[]
                 | select(.type == "toolCall" and ((((.arguments?.command? // "") | tostring) | is_gate)))
                 | (.id // "")) as $i ({}; .[$i] = true)) as $gateIds
      | ([$all[] | select(.type == "tool_execution_end")]) as $ends
      | ([$ends[] | select($gateIds[(.toolCallId // "")] // false) | (.durationMs? // 0)] | add // 0) as $gateMs
      | ([$ends[] | select(($gateIds[(.toolCallId // "")] // false) | not) | (.durationMs? // 0)] | add // 0) as $otherMs
      | (reduce ($ses[] | {role: ((.prompt // "") | ascii_downcase
                                  | if test("worker-tail") then "tail"
                                    elif test("reviewer") then "reviewer"
                                    else "worker" end),
                           tok: ([.turns[] | .usage | tok] | add // 0)}) as $s
           ({worker:null, reviewer:null, tail:null};
            .[$s.role] = (if $s.tok > 0 then (((.[$s.role]) // 0) + $s.tok) else .[$s.role] end))) as $roles
      # …and the model each of those turns named, beside the role total (issue #823). The model is read off the same
      # `turn_end` message the tokens are, so a role that answered on two models is two tallies rather than one row
      # priced at whichever model came first, and two sessions of one role sum their models instead of overwriting. A
      # role whose turns named no model stays null, never an empty object — the rule every figure of this function
      # keeps.
      | (reduce ($ses[] | {role: ((.prompt // "") | ascii_downcase
                                  | if test("worker-tail") then "tail"
                                    elif test("reviewer") then "reviewer"
                                    else "worker" end),
                           models: ([.turns[]
                                     | select((.model // null) != null)
                                     | {m: .model, t: (.usage | tok)}]
                                    | group_by(.m) | map({key: .[0].m, value: ([.[].t] | add)}) | from_entries)}) as $s
           ({worker:null, reviewer:null, tail:null};
            .[$s.role] = (if ($s.models | length) > 0
                          then (reduce ($s.models | to_entries[]) as $e (.[$s.role] // {};
                                  .[$e.key] = ((.[$e.key] // 0) + $e.value)))
                          else .[$s.role] end))) as $roleModels
      | ([$ses[] | ([.turns[] | any(.calls[]?; .gate)] | index(true)) as $g
           | select($g != null)
           | {after: ([.turns[$g:][] | .usage | tok] | add // 0),
              total: ([.turns[] | .usage | tok] | add // 0)}]) as $gated
      | ([$all[] | select(.type == "tool_execution_end") | (.toolName? // empty) | strings]
         | group_by(.) | map({key: .[0], value: length}) | from_entries) as $tools
      | ([$all[] | select(.type == "tool_execution_end")] | length) as $calls
      | ([$all[] | select(.type == "turn_end")] | length) as $turns
      # The reads a session repeated (issue #814): the one figure of the worker prompt rule (#246) that nothing
      # counted, so a child that follows it and one that ignores it read the same in every report. A read is a call of
      # the two read tools the offline reader counts (`tools/loop-spend.py`), its path what the call named — the
      # `args` of its `tool_execution_start`, or the `arguments` of its `toolCall` block for a harness that reports the
      # call only there — and its size the characters of the `tool_execution_end` result. The path is collected over the
      # whole stream before the walk below, because a real stream closes the call before it closes the turn
      # (`tool_execution_end` precedes the `turn_end` that names it). A repeat is a path the **session** had already
      # read, because a reviewer reading what the worker read is a new conversation and not a second read of a fact the
      # item holds. `reads` is recorded beside the two because a count of repeats with no base says nothing; all three
      # are null when the stream measured no read at all, never a zero.
      | ([[$all[] | select(.type == "tool_execution_start")
                   | select(((.toolName? // "") == "ctx_read") or ((.toolName? // "") == "read"))
                   | {id: (.toolCallId? // ""),
                      p: (((.args? // {}) | (.path? // .file_path? // "")) | tostring)}],
           [$all[] | select(.type == "turn_end") | ((.message.content? // [])[])
                   | select(.type == "toolCall")
                   | select(((.name? // "") == "ctx_read") or ((.name? // "") == "read"))
                   | {id: (.id? // ""),
                      p: (((.arguments? // {}) | (.path? // .file_path? // "")) | tostring)}]]
          | add) as $named
      | (reduce $named[] as $x ({}; if $x.p != "" then .[$x.id] = $x.p else . end)) as $paths
      | (reduce $all[] as $e ({s: 0, reads: []};
           if $e.type == "session" then .s += 1
           elif $e.type == "tool_execution_end" then
             (($e.toolName? // "")) as $n
             | ($paths[($e.toolCallId? // "")] // "") as $p
             | if (($n == "ctx_read" or $n == "read") and $p != "") then
                 .reads += [{s: .s, p: $p,
                             chars: (($e.result?.content? // [])
                                     | if type == "array"
                                       then ([.[] | (.text? // "") | tostring | length] | add // 0)
                                       else 0 end)}]
               else . end
           else . end)) as $r
      | (reduce $r.reads[] as $x ({seen: {}, dup: 0, chars: 0};
           (($x.s | tostring) + "|" + $x.p) as $k
           | if (.seen[$k] // 0) > 0 then .dup += 1 | .chars += $x.chars | .seen[$k] += 1
             else .seen[$k] = 1 end)) as $dup
      | ($r.reads | length) as $reads
      | ([$all[] | select(.type == "compaction_end") | (.result?.tokensBefore? // empty) | numbers] | max) as $compacted
      | ([$t[] | ((.input // 0) + (.cacheRead // 0))] | max) as $turnMax
      | ([$turnMax // 0, $compacted // 0] | max) as $context
      | {tools: (if ($tools | length) > 0 then $tools else null end),
         calls: (if $turns > 0 then $calls else null end),
         turns: (if $turns > 0 then $turns else null end),
         tokens:
           (if ($t | length) == 0 then null
            else ([$t[] | ((.input // 0) + (.output // 0) + (.cacheRead // 0) + (.cacheWrite // 0))] | add) as $sum
                 | if $sum == 0 then null
                   else {input: ([$t[] | .input // 0] | add),
                         output: ([$t[] | .output // 0] | add),
                         cacheRead: ([$t[] | .cacheRead // 0] | add),
                         cacheWrite: ([$t[] | .cacheWrite // 0] | add)}
                   end
            end),
         cost:
           (if ($t | length) == 0 then null
            else ([$t[] | (.cost?.total? // 0)] | add) as $c
                 | if $c > 0 then $c else null end
            end),
         context: (if $context > 0 then $context else null end),
         reads: (if $reads > 0 then $reads else null end),
         dupReads: (if $reads > 0 then $dup.dup else null end),
         dupChars: (if $reads > 0 then $dup.chars else null end),
         repWithheld: $repw,
         repKept: $repk,
         phases: (if ($modelMs + $gateMs + $otherMs) == 0 then null
                  else {modelMs: $modelMs, gateMs: $gateMs, otherMs: $otherMs} end),
         roles: (if ([$roles[] | select(. != null)] | length) == 0 then null else $roles end),
         roleModels: (if ([$roleModels[] | select(. != null)] | length) == 0 then null else $roleModels end),
         afterGate: (if ($gated | length) == 0 then null
                     else {tokens: ([$gated[].after] | add // 0),
                           total: ([$gated[].total] | add // 0),
                           sessions: ($gated | length)} end)}' "$events" 2>/dev/null) || blob=""
  fi
  case "$blob" in
    '{'*) printf '%s' "$blob" ;;
    *) printf '%s' '{"tools":null,"calls":null,"turns":null,"tokens":null,"cost":null,"context":null,"phases":null,"roles":null,"roleModels":null,"afterGate":null,"reads":null,"dupReads":null,"dupChars":null,"repWithheld":null,"repKept":null}' ;;
  esac
}

# The adoption of a previous attempt's branch is a command the run takes with its own inputs — the project tree
# it lands in, the base it replays onto, the replay itself — and its refusal is not the owner's question (issue
# #794): the branch's own evidence was admissible, so what refused it is a tree a later run finds clean once the
# dirt is gone, and the item is the next run's work. The refusal is recorded beside the pre-screen's exclusion,
# which keeps its verdict, its reason and the branch as evidence; the predicates that read an exclusion as a
# question the owner owes skip an entry that carries the mechanism, so no decision slot is spent and nothing is
# escalated. A mechanism belongs to one record (the rule `done --stop-reason` already keeps), so an item the run
# has worked is refused here rather than recording the same stop twice.
cmd_adoption_refused() {
  require_state
  local issue="${1:-}" reason=""
  shift || true
  while [ $# -gt 0 ]; do
    case "$1" in
      --reason) need_value "$@"; reason="${2:-}"; shift 2 ;;
      --*) loop_die "adoption-refused: unknown argument $1" ;;
      *) loop_die "adoption-refused: usage: state.sh adoption-refused <issue> --reason TEXT" ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "adoption-refused: usage: state.sh adoption-refused <issue> --reason TEXT" ;; esac
  [ -n "$reason" ] || loop_die "adoption-refused: --reason is required — the refusal's own text is what the record quotes"
  jq -e --argjson i "$issue" '[(.excluded // [])[] | select(.issue == $i)] | length == 1' "$STATE_FILE" >/dev/null ||
    loop_refuse "adoption-refused: #$issue is not one exclusion of this run — the adoption's refusal is recorded on the pre-screen's own entry for it"
  jq -e --argjson i "$issue" '[(.items // [])[] | select(.issue == $i)] | length == 0' "$STATE_FILE" >/dev/null ||
    loop_refuse "adoption-refused: #$issue already has the item record this run wrote for it — a mechanism belongs to one record, and that one is the block's"
  patch_state '(.excluded = [.excluded[] | if .issue == $i then . + {mechanism:"unmergeable", adoptionRefusal:$r} else . end])' \
    --argjson i "$issue" --arg r "$reason"
  printf 'adoption refused: #%s recorded as unmergeable — the item stays work, and no decision is written for it\n' "$issue"
}

# The table answers the run's questions on its own (#208, #211): which items were handled and how each ended —
# `outcome` plus the item's own recorded reason when that outcome is not a closure — what each one cost in
# clock time (the wall time `done --wall` records) and in spend (the tools it called, the tokens it used and
# what they cost, the largest context it reached, all read from the child's own event stream), and, after it,
# what the caller must do now. The columns are appended rather than interleaved so a reader that quoted the
# earlier shape keeps its columns in order.
#
# A figure nothing reported prints `-`, never `0`: a provider that streams no usage leaves zeros behind, and a
# zero in a column of numbers reads as a measurement that was made. The block's mechanism is the last column,
# because a column that arrives is appended: the spend beside it is totalled by mechanism from the table itself,
# `-` where no block stopped on one (issue #280).
cmd_report() {
  require_state
  # The band an item's spend is read against (issue #662), beside the clocks it belongs with. `0` — or a
  # configuration that records no key, which is what a ported project's is — flags nothing, so the column prints
  # `-` for every row exactly as a row that reported no spend does; report.sh's run-level line says which of the
  # two it is, and nothing here decides an item's outcome from it.
  local tokens_warn
  tokens_warn=$(loop_conf_value LOOP_ITEM_TOKENS_WARN)
  case "$tokens_warn" in ''|*[!0-9]*) tokens_warn=0 ;; esac
  jq -r --argjson over "$tokens_warn" '
    "closed \(.items | map(select(.result == "done")) | length) · mis-diagnosed \(.items | map(select(.result == "falsified")) | length) · skipped \(.items | map(select(.result == "skipped")) | length) · blocked \(.items | map(select(.result == "blocked")) | length) · cut \(.items | map(select(.result == "cut")) | length) of \(.run.items) items · budget \(.run.minutes) min",
    "",
    "| issue | class | verdict | change | root cause | red test | gate | review | wall | tools | tokens | context | outcome | reason | stop | over | calls | dup reads |",
    "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    (if (.items | length) == 0 then "| — | — | — | — | — | — | — | — | — | — | — | — | nothing processed | — | — | — | — | — |"
     else (.items[]
       | ((.metrics.wallSeconds // null) as $w
          | if $w == null then "-"
            elif $w < 60 then "\($w | floor)s"
            else "\(($w / 60) | floor)m\(($w % 60) | floor)s" end) as $wall
       | ((.note // "") | gsub("[\\t\\n\\r]+"; " ") | gsub("\\|"; "/")) as $note
       | ((.metrics.rootCause // "") | gsub("[\\t\\n\\r]+"; " ") | gsub("\\|"; "/")) as $cause
       | (if .result == "done" then "-" elif $note != "" then $note elif $cause != "" then $cause else "-" end) as $reason
       | ((.metrics.tools // null) as $tools
          | if $tools == null then "-"
            else ($tools | to_entries | sort_by(.key) | map("\(.key)=\(.value)") | join(" ")) end) as $toolcol
       | ((.metrics.tokens // null) as $t
          | if $t == null then "-"
            else "in=\($t.input) out=\($t.output) cache=\(($t.cacheRead // 0) + ($t.cacheWrite // 0))"
                 + ((.metrics.cost // null) as $c
                    | if $c == null then "" else " cost=$\(($c * 10000 | round) / 10000)" end)
            end) as $tokencol
       | ((.metrics.context // null) as $n | if $n == null then "-" else "\($n)" end) as $contextcol
       # A record written before the field existed has no `stopReason`, one written since carries `null` when no
       # mechanism is given, and only a mechanism block carries a word: all three print `-`, and `//` alone does
       # not, because it yields the empty string for an empty one (#280, review round 1).
       | ((.stopReason // "") | if . == "" then "-" else . end) as $stopcol
       # The band column (issue #662): how far above the recorded bound the spend is, and `-` otherwise — below it,
       # no band recorded, or no spend reported at all, which is the same answer the token cell gives. Appended
       # after the mechanism, so every column the report already had keeps its name and its order.
       | ((.metrics.tokens // null) as $tk
          | (if $tk == null then null
             else (($tk.input // 0) + ($tk.output // 0) + ($tk.cacheRead // 0) + ($tk.cacheWrite // 0)) end) as $tot
          | if $over > 0 and $tot != null and $tot > $over
            then "\(((($tot / $over) * 10) | round) / 10)x" else "-" end) as $overcol
       # The shape the item spent its budget in (issue #757): its tool calls per turn, appended after the band for
       # the reason the band was appended — every column a reader already quoted keeps its index. The two counts are
       # recorded (`tools/loop/state.sh`) and their ratio is derived here; an item that recorded no turn prints
       # `-`, never a zero, exactly as every other unreported figure does. Unlike the band column this one is the
       # figure of the item itself and not a multiple of a bound, so it prints whether or not a floor is recorded —
       # the floor is the comparison the report trend makes, and `0` means no comparison rather than no measurement.
       | ((.metrics.calls // null) as $calls
          | (.metrics.turns // null) as $turns
          | if $calls == null or $turns == null or $turns == 0 then "-"
            else "\(((($calls / $turns) * 100) | round) / 100)" end) as $callscol
        # The reads the item repeated (issue #814), appended for the reason every column since the band was: every
        # index a reader already quotes keeps its number. The cell is `repeat/total` — a count of repeats with no base
        # is a figure nobody can read — and an item whose stream measured no read prints `-`, never a zero.
        | ((.metrics.dupReads // null) as $dup
           | (.metrics.reads // null) as $reads
           | if $dup == null or $reads == null or $reads == 0 then "-" else "\($dup)/\($reads)" end) as $dupcol
       | "| #\(.issue) | \(.class // "-") | \(.verdict) | \(.change // "-") | \(.metrics.rootCause // "-") | \(.metrics.redTest // "-") | \(.metrics.gate // "-") | \(.metrics.reviewRounds // "-") | \($wall) | \($toolcol) | \($tokencol) | \($contextcol) | \(.result) | \($reason) | \($stopcol) | \($overcol) | \($callscol) | \($dupcol) |") end),
    "",
    "merged finished attempts: " + (
      [.items[] | select((.merged.branch // "") != "")
        | "#\(.issue) \(.merged.branch) · commits \(.merged.commits) · evidence \(.merged.evidence)"]
      | if length == 0 then "none" else join(" | ") end),
    (if (.excluded | length) == 0 then "no exclusions" else "excluded: \([.excluded[] | "#\(.issue) \(.verdict)\(if (.mechanism // "") == "" then "" else " (\(.mechanism))" end)"] | join(" · "))" end)
  ' "$STATE_FILE"
}

# ── the lock: one run per repository (D12) ───────────────────────────────────────────────────────

# The owner is the caller's own identity, because the lock is taken and released by two different
# `state.sh` processes (the run takes it and its EXIT trap releases it): a lock keyed to this script's
# pid would never be released. The self-test passes an explicit owner for the same reason.
cmd_lock() {
  state_resolve
  local owner="${PPID:-unknown}"
  while [ $# -gt 0 ]; do
    case "$1" in
      --owner) need_value "$@"; owner="${2:-}"; shift 2 ;;
      *) loop_die "lock: unknown argument $1" ;;
    esac
  done
  mkdir -p "$STATE_DIR"
  if ! mkdir "$LOCK_FILE" 2>/dev/null; then
    # The lock is a lease, not a tombstone. A run that was killed with a signal no trap can catch leaves its
    # lock behind, and a lock whose owner is not running any more must not refuse every run that follows it:
    # the run takes it over and says so (measured 2026-10-22: the run killed at 19:19 left the lock, and the
    # run that started at 19:34 was refused by it two minutes into a 240-minute budget — after having replaced
    # the live run's state and merged its item). A live owner is the refusal it has always been.
    local holder holder_pid
    holder=$(cat "$LOCK_FILE/owner" 2>/dev/null || printf 'an unknown process')
    holder_pid=$(printf '%s' "$holder" | tr -cd '0-9')
    if [ -n "$holder_pid" ] && kill -0 "$holder_pid" 2>/dev/null; then
      loop_note "another run holds $LOCK_FILE (owner $holder) — one run per repository"
      exit "$LOOP_REFUSE"
    fi
    rm -rf "$LOCK_FILE" 2>/dev/null || true
    mkdir "$LOCK_FILE" 2>/dev/null || { loop_note "another run holds $LOCK_FILE — one run per repository"; exit "$LOOP_REFUSE"; }
    printf 'took over the lock %s: its owner %s is not running any more\n' "$LOCK_FILE" "$holder" >&2
  fi
  printf '%s\n' "$owner" > "$LOCK_FILE/owner"
  printf 'lock held: %s (owner %s)\n' "$LOCK_FILE" "$owner"
}

cmd_unlock() {
  state_resolve
  local owner="${PPID:-unknown}"
  while [ $# -gt 0 ]; do
    case "$1" in
      --owner) need_value "$@"; owner="${2:-}"; shift 2 ;;
      *) loop_die "unlock: unknown argument $1" ;;
    esac
  done
  [ -d "$LOCK_FILE" ] || return 0
  # Only the holder releases it, so a lock left behind by a killed process is reported by the next
  # run's attempt rather than being silently stolen.
  local holder
  holder=$(cat "$LOCK_FILE/owner" 2>/dev/null || printf '')
  if [ -n "$holder" ] && [ "$holder" != "$owner" ]; then
    loop_note "not releasing $LOCK_FILE — it is held by $holder"
    return 0
  fi
  rm -rf "$LOCK_FILE"
}

need_jq
state_resolve
cmd="${1:-}"; shift || true
case "$cmd" in
  init) cmd_init "$@" ;;
  show) cmd_show "$@" ;;
  get) cmd_get "$@" ;;
  next) cmd_next "$@" ;;
  queue) cmd_queue "$@" ;;
  start) cmd_start "$@" ;;
  record-change-copy) cmd_record_change_copy "$@" ;;
  record-resume) cmd_record_resume "$@" ;;
  step) cmd_step "$@" ;;
  done) cmd_done "$@" ;;
  adoption-refused) cmd_adoption_refused "$@" ;;
  score-landings) cmd_score_landings ;;
  set-base) cmd_set_base "$@" ;;
  stop) cmd_stop "$@" ;;
  hold) cmd_hold "$@" ;;
  report) cmd_report "$@" ;;
  lock) cmd_lock "$@" ;;
  unlock) cmd_unlock "$@" ;;
  ""|-h|--help) sed -n '2,32p' "$0" ;;
  *) loop_die "unknown command '$cmd'" ;;
esac
