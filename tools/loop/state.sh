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
# arithmetic — is the authority on whether an item may start: the item cap, the deadline, an empty
# queue, two consecutive blocked items, or a remaining time below the next item's floor, which is
# derived from the wall times this run records for its finished items and bounded below by
# `LOOP_ITEM_FLOOR_MINUTES` (design D5, invariant 3). Exit 5 is a refusal that must not be retried inside
# the same reasoning: the lock is held, or the base has moved (design D12, D4).
#
# Exit: 0 ok, 2 usage, 3 stop, 5 refuse.
#
# Usage:
#   state.sh init --items N --minutes M [--review on|off] [--may-close yes|no]
#                 [--max-decisions N] [--max-review-rounds N] [--base <sha>]
#     `--base` defaults to the base branch's commit (`LOOP_BASE_REF`, `main` when unset), never the project
#     tree's checked-out commit.
#   state.sh show
#   state.sh get <jq-path>              # e.g. `get run.base`
#   state.sh set-base <commit>          # a finished attempt merged onto the base advances it
#   state.sh stop <reason>              # the condition that ended the run, as `next` names it#   state.sh next                       # the next candidate; exit 3 when the run may not continue
#   state.sh queue                      # read the queue as JSON on stdin (prescreen writes it)
#   state.sh start <issue> --change <name>
#   state.sh step <phase> --name <name> --status running|ok|fail|skip [--note TEXT]
#   state.sh done <issue> --result done|skipped|blocked|falsified [--verdict V] [--change C] [--note TEXT]
#                         [--wall SECONDS] [--pi-exit N] [--rounds N] [--gate TALLY]
#                         [--events FILE] [--closed yes|no] [--root-cause TEXT] [--red-test TEXT]
#                         [--decision-label LABEL] [--label-failures <transition>…]
#                         [--stop-reason refused|unmergeable]
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
  started=$(now_epoch)
  deadline=$(( started + minutes * 60 ))
  write_state "$(jq -n \
    --argjson items "$items" --argjson minutes "$minutes" --argjson maxDecisions "$max_decisions" \
    --argjson maxReviewRounds "$max_review_rounds" --argjson ownerPid "$owner_pid" \
    --argjson started "$started" --argjson deadline "$deadline" \
    --arg review "$review" --arg mayClose "$may_close" --arg base "$base" \
    --arg startedAt "$(iso_utc "$started")" --arg deadlineAt "$(iso_utc "$deadline")" \
    '{run:{items:$items, minutes:$minutes, review:$review, mayClose:$mayClose,
           maxDecisions:$maxDecisions, maxReviewRounds:$maxReviewRounds, base:$base,
           ownerPid:$ownerPid,
           startedAt:$startedAt, startedEpoch:$started,
           deadlineAt:$deadlineAt, deadlineEpoch:$deadline},
      queue:[], excluded:[], current:null, items:[], steps:[]}')"
  printf 'state: budget %s items / %s min · review %s · may-close %s · max-decisions %s · max-review-rounds %s · base %s\n' \
    "$items" "$minutes" "$review" "$may_close" "$max_decisions" "$max_review_rounds" "${base:0:12}"
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
      "stop     \($r.stop.reason // (if $gone != "" then $gone else "not stopped — the run is still going, or it ended without recording a reason" end))",
      "progress closed \(.items | map(select(.result == "done")) | length) · mis-diagnosed \(.items | map(select(.result == "falsified")) | length) · skipped \(.items | map(select(.result == "skipped")) | length) · blocked \(.items | map(select(.result == "blocked")) | length) of \($r.items)",
      "queue    \(.queue | length): \([.queue[].issue] | join(" "))",
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
  reason=$(jq -r '
    (.run) as $r
    | (.items | length) as $done
    | if $done >= $r.items then "item cap reached (\($done)/\($r.items))"
      elif now >= $r.deadlineEpoch then "deadline passed at \($r.deadlineAt)"
      elif (.queue | length) == 0 then "queue empty"
      elif ([.items[-2:][] | select(.result == "blocked")] | length) == 2 then "two consecutive items ended blocked"
      else "" end
  ' "$STATE_FILE")
  if [ -z "$reason" ]; then
    # The last stop condition is a clock, not a count: do not start an item the remaining budget
    # cannot pay for. What an item costs is not a constant — the run measures it, one `done --wall` at a
    # time — so the floor is derived from the items this run has finished: their median wall time times
    # the spread the measurements show (1.5), rounded up to the whole minute. The median is the central
    # value because it is the one a single expensive item does not move; the margin is what turns a
    # typical item into a floor for the one still to come. `LOOP_ITEM_FLOOR_MINUTES` remains the lower
    # bound, so a run with no history — or a cheap one, which a run of skips is — keeps the documented
    # floor, and the refusal names both numbers it compared (issue #206, design D5).
    local minutes_left floor measured median n items_word
    minutes_left=$(jq -r '((.run.deadlineEpoch - now) / 60 | floor)' "$STATE_FILE")
    measured=$(jq -r --argjson min "$FLOOR_MINUTES" '
      [.items[] | .metrics.wallSeconds | select(type == "number" and . > 0)] as $w
      | ($w | length) as $n
      | ($w | sort) as $s
      | (if $n == 0 then null
         elif ($n % 2) == 1 then ($s[($n / 2) | floor] / 60)
         else ((($s[$n / 2 - 1] + $s[$n / 2]) / 2) / 60) end) as $median
      | (if $median == null then [$min, 0, 0]
         else [([($median * 1.5 | ceil), $min] | max), $n, ($median | round)] end)
      | @tsv' "$STATE_FILE")
    IFS=$'\t' read -r floor n median <<< "$measured"
    if [ "$minutes_left" -lt "$floor" ]; then
      items_word="finished items"; [ "$n" -eq 1 ] && items_word="finished item"
      if [ "$n" -eq 0 ]; then
        reason="only ${minutes_left} min left, below the ${floor} min floor for the next item (no finished item records a wall time, configured minimum ${FLOOR_MINUTES})"
      else
        reason="only ${minutes_left} min left, below the ${floor} min floor for the next item (median ${median} min over ${n} ${items_word}, configured minimum ${FLOOR_MINUTES})"
      fi
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
  local queue excluded
  queue=$(printf '%s' "$json" | jq -c --arg queued "$(loop_role queuedVerdict)" '[.[] | select((.verdict // $queued) == $queued) | {issue, class, category, verdict, reason, rank, gates}]')
  # The exclusion carries the one clause of evidence that produced it (the branch a previous attempt left
  # behind, or the label that states why): the run escalates the decisions among the exclusions, and it cannot
  # cite evidence it threw away here.
  excluded=$(printf '%s' "$json" | jq -c --arg queued "$(loop_role queuedVerdict)" '[.[] | select((.verdict // $queued) != $queued) | {issue, class, verdict, reason, evidence, alreadyEscalated: (.alreadyEscalated // false)}]')
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
  local issue="" result="" verdict="" change="" note="" wall="" pi_exit="" rounds="" gate="" closed="" root_cause="" red_test="" decision_label="" label_failures="" merged_branch="" merged_commits="" merged_evidence="" events="" stop_reason=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --result) need_value "$@"; result="${2:-}"; shift 2 ;;
      --verdict) need_value "$@"; verdict="${2:-}"; shift 2 ;;
      --change) need_value "$@"; change="${2:-}"; shift 2 ;;
      --note) need_value "$@"; note="${2:-}"; shift 2 ;;
      --wall) need_value "$@"; wall="${2:-}"; shift 2 ;;
      --pi-exit) need_value "$@"; pi_exit="${2:-}"; shift 2 ;;
      --rounds) need_value "$@"; rounds="${2:-}"; shift 2 ;;
      --gate) need_value "$@"; gate="${2:-}"; shift 2 ;;
      --events) need_value "$@"; events="${2:-}"; shift 2 ;;
      --closed) need_value "$@"; closed="${2:-}"; shift 2 ;;
      --root-cause) need_value "$@"; root_cause="${2:-}"; shift 2 ;;
      --red-test) need_value "$@"; red_test="${2:-}"; shift 2 ;;
      --decision-label) need_value "$@"; decision_label="${2:-}"; shift 2 ;;
      --label-failures) need_value "$@"; label_failures="${2:-}"; shift 2 ;;
      --stop-reason) need_value "$@"; stop_reason="${2:-}"; shift 2 ;;
      --merged-branch) need_value "$@"; merged_branch="${2:-}"; shift 2 ;;
      --merged-commits) need_value "$@"; merged_commits="${2:-}"; shift 2 ;;
      --merged-evidence) need_value "$@"; merged_evidence="${2:-}"; shift 2 ;;
      --*) loop_die "done: unknown argument $1" ;;
      *) [ -z "$issue" ] || loop_die "done: issue given twice"; issue="$1"; shift ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "done: usage: state.sh done <issue> --result done|skipped|blocked|falsified" ;; esac
  case "$result" in done|skipped|blocked|falsified) ;; *) loop_die "done: --result must be done|skipped|blocked|falsified" ;; esac
  # The mechanism a block stopped on is a closed vocabulary, not a second place to write prose (issue #280: a
  # refusing review round and a base that would not take the branch both reached the record as the one word
  # `blocked`, so 198.8 M of refusing rounds could only be told from one 22.9 M merge refusal by reading the note).
  # A mechanism on anything but a block would double-count the item as two kinds of stop, so it is refused too.
  case "$stop_reason" in
    ""|refused|unmergeable) ;;
    *) loop_die "done: --stop-reason must be refused or unmergeable" ;;
  esac
  [ -z "$stop_reason" ] || [ "$result" = blocked ] ||
    loop_die "done: --stop-reason belongs to a blocked item, and this one is recorded $result"
  [ -n "$verdict" ] || verdict=$(if [ "$result" = done ]; then printf 'closed'; else printf 'unspecified'; fi)
  # The class travels from the pre-screen into the record, so the report can rank without re-reading
  # the tracker; an item with no queue entry (a resumed run) falls back to the empty string.
  # `--decision-label` is the label the run's escalation *wrote* on the item — not the verdict the worker
  # chose, which is a different fact: a skip verdict of `needs-diagnosis` and a blocked item both end as
  # the decision label. The report names the decisions an item was left with from this field (issue #177).
  # `--label-failures` is the other half of that fact: the label transitions the tracker refused, so the item is
  # recorded as one whose state there is stale rather than as one the run escalated (issue #279).
  # `--stop-reason` is the mechanism a block stopped on, recorded so a mechanism's spend is totalled from the
  # state instead of from the note's prose; it is the blocked attempt's own fact, so a second account of the same
  # item joins only its note and never rewrites it.
  local class usage
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
        .items + [{issue:$i, class:$c, result:$r, verdict:$v, change:$change, note:$note, at:$at,
                  stopReason:(if $sr == "" then null else $sr end),
                  escalatedLabel:$dl, labelFailures:($lf | split(" ") | map(select(length > 0))),
                  merged:{branch:$mb, commits:$mc, evidence:$me},
                  metrics:{wallSeconds:$wall, piExit:$piExit, reviewRounds:$rounds, gate:$gate,
                           closed:$closed, rootCause:$rc, redTest:$rt,
                           tools:$usage.tools, tokens:$usage.tokens, cost:$usage.cost,
                           context:$usage.context}}]
      else
        .items | map(if .issue == $i then
          .note = (if $note == "" then .note elif .note == "" then $note else .note + " · " + $note end)
          | .escalatedLabel = (if $dl == "" then .escalatedLabel else $dl end)
          | .labelFailures = (((.labelFailures // []) + ($lf | split(" ") | map(select(length > 0)))) | unique)
        else . end)
      end)
    | .queue = [.queue[] | select(.issue != $i)]
    | (if $mb == "" then . else .excluded = [.excluded[] | select(.issue != $i)] end)
    | (if .current != null and .current.issue == $i then .current = null else . end)
  ' --argjson i "$issue" --arg c "$class" --arg r "$result" --arg v "$verdict" --arg change "$change" \
    --arg note "$note" --arg at "$(iso_utc "$(now_epoch)")" --arg dl "$decision_label" \
    --arg lf "$label_failures" --arg sr "$stop_reason" \
    --argjson wall "$(num_or_null "$wall")" --arg piExit "$pi_exit" --argjson rounds "$(num_or_null "$rounds")" \
    --arg gate "$gate" --arg closed "$closed" --arg rc "$root_cause" --arg rt "$red_test" \
    --argjson usage "$usage" \
    --arg mb "$merged_branch" --arg mc "$merged_commits" --arg me "$merged_evidence"
  printf 'recorded %s (#%s %s)\n' "$result" "$issue" "$verdict"
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
# A stream that holds none of these records — a plain-text command, or another agent's format — yields four
# nulls, so the engine reads a child's dialect without depending on it.
child_usage_json() { # child_usage_json <events-file> → {tools,tokens,cost,context}
  local events="${1:-}" blob=""
  if [ -n "$events" ] && [ -f "$events" ]; then
    blob=$(jq -s -c '
      def turns:
        reduce .[] as $e ({cur:null, done:[]};
          if $e.type == "message_update" and (($e.usage? // null) | type) == "object" then .cur = $e.usage
          elif $e.type == "turn_end" then ((if .cur != null then .done = (.done + [.cur]) else . end) | .cur = null)
          else . end)
        | if .cur != null then (.done + [.cur]) else .done end;
      . as $all
      | ($all | turns) as $t
      | ([$all[] | select(.type == "tool_execution_end") | (.toolName? // empty) | strings]
         | group_by(.) | map({key: .[0], value: length}) | from_entries) as $tools
      | ([$all[] | select(.type == "compaction_end") | (.result?.tokensBefore? // empty) | numbers] | max) as $compacted
      | ([$t[] | ((.input // 0) + (.cacheRead // 0))] | max) as $turnMax
      | ([$turnMax // 0, $compacted // 0] | max) as $context
      | {tools: (if ($tools | length) > 0 then $tools else null end),
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
         context: (if $context > 0 then $context else null end)}' "$events" 2>/dev/null) || blob=""
  fi
  case "$blob" in
    '{'*) printf '%s' "$blob" ;;
    *) printf '%s' '{"tools":null,"tokens":null,"cost":null,"context":null}' ;;
  esac
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
  jq -r '
    "closed \(.items | map(select(.result == "done")) | length) · mis-diagnosed \(.items | map(select(.result == "falsified")) | length) · skipped \(.items | map(select(.result == "skipped")) | length) · blocked \(.items | map(select(.result == "blocked")) | length) of \(.run.items) items · budget \(.run.minutes) min",
    "",
    "| issue | class | verdict | change | root cause | red test | gate | review | wall | tools | tokens | context | outcome | reason | stop |",
    "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|",
    (if (.items | length) == 0 then "| — | — | — | — | — | — | — | — | — | — | — | — | nothing processed | — | — |"
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
       | "| #\(.issue) | \(.class // "-") | \(.verdict) | \(.change // "-") | \(.metrics.rootCause // "-") | \(.metrics.redTest // "-") | \(.metrics.gate // "-") | \(.metrics.reviewRounds // "-") | \($wall) | \($toolcol) | \($tokencol) | \($contextcol) | \(.result) | \($reason) | \($stopcol) |") end),
    "",
    "merged finished attempts: " + (
      [.items[] | select((.merged.branch // "") != "")
        | "#\(.issue) \(.merged.branch) · commits \(.merged.commits) · evidence \(.merged.evidence)"]
      | if length == 0 then "none" else join(" | ") end),
    (if (.excluded | length) == 0 then "no exclusions" else "excluded: \([.excluded[] | "#\(.issue) \(.verdict)"] | join(" · "))" end)
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
  step) cmd_step "$@" ;;
  done) cmd_done "$@" ;;
  set-base) cmd_set_base "$@" ;;
  stop) cmd_stop "$@" ;;
  report) cmd_report "$@" ;;
  lock) cmd_lock "$@" ;;
  unlock) cmd_unlock "$@" ;;
  ""|-h|--help) sed -n '2,32p' "$0" ;;
  *) loop_die "unknown command '$cmd'" ;;
esac
