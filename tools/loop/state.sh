#!/usr/bin/env bash
# state.sh — the run's state, its budget and its lock. The only writer of `.pi/issue-loop/run.json`.
#
# The state is what survives an interrupt: the budget, the base commit, the queue, the finished items
# and every step boundary. It lives under `.pi/` (machine-local, gitignored, never committed) and this
# script is its only writer, so a field that disagrees with the run was edited by hand — which is
# reported as a drift warning rather than silently obeyed (design D5).
#
# `next` exits 3 whenever the run may not start another item, and that exit code — not a caller's
# arithmetic — is the authority on whether an item may start: the item cap, the deadline, an empty
# queue, two consecutive blocked items, or a remaining time below the next item's floor (design D5,
# invariant 3). Exit 5 is a refusal that must not be retried inside the same reasoning: the lock is
# held, or the base has moved (design D12, D4).
#
# Exit: 0 ok, 2 usage, 3 stop, 5 refuse.
#
# Usage:
#   state.sh init --items N --minutes M [--review on|off] [--may-close yes|no]
#                 [--max-decisions N] [--base <sha>]
#   state.sh show
#   state.sh get <jq-path>              # e.g. `get run.base`
#   state.sh next                       # the next candidate; exit 3 when the run may not continue
#   state.sh queue                      # read the queue as JSON on stdin (prescreen writes it)
#   state.sh start <issue> --change <name>
#   state.sh step <phase> --name <name> --status running|ok|fail|skip [--note TEXT]
#   state.sh done <issue> --result done|skipped|blocked [--verdict V] [--change C] [--note TEXT]
#                         [--wall SECONDS] [--pi-exit N] [--rounds N] [--gate TALLY]
#                         [--closed yes|no] [--root-cause TEXT] [--red-test TEXT]
#                         [--decision-label LABEL]
#   state.sh report
#   state.sh lock | unlock
set -uo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=tools/loop/lib.sh
source "$here/lib.sh"

STATE_FILE=""
STATE_DIR=""
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

cmd_init() {
  state_resolve
  local items="" minutes="" review="" may_close="" max_decisions="" base=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --items) items="${2:-}"; shift 2 ;;
      --minutes) minutes="${2:-}"; shift 2 ;;
      --review) review="${2:-}"; shift 2 ;;
      --may-close) may_close="${2:-}"; shift 2 ;;
      --max-decisions) max_decisions="${2:-}"; shift 2 ;;
      --base) base="${2:-}"; shift 2 ;;
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
  case "$items" in ''|*[!0-9]*) loop_die "init: --items must be a non-negative integer" ;; esac
  case "$minutes" in ''|*[!0-9]*) loop_die "init: --minutes must be a non-negative integer" ;; esac
  case "$max_decisions" in ''|*[!0-9]*) loop_die "init: --max-decisions must be a non-negative integer" ;; esac
  case "$review" in on|off) ;; *) loop_die "init: --review must be on or off" ;; esac
  case "$may_close" in yes|no) ;; *) loop_die "init: --may-close must be yes or no" ;; esac
  [ -n "$base" ] || base=$(git -C "$LOOP_ROOT" rev-parse HEAD 2>/dev/null) || loop_die "init: cannot read the current commit"

  local started deadline
  started=$(now_epoch)
  deadline=$(( started + minutes * 60 ))
  write_state "$(jq -n \
    --argjson items "$items" --argjson minutes "$minutes" --argjson maxDecisions "$max_decisions" \
    --argjson started "$started" --argjson deadline "$deadline" \
    --arg review "$review" --arg mayClose "$may_close" --arg base "$base" \
    --arg startedAt "$(iso_utc "$started")" --arg deadlineAt "$(iso_utc "$deadline")" \
    '{run:{items:$items, minutes:$minutes, review:$review, mayClose:$mayClose,
           maxDecisions:$maxDecisions, base:$base, startedAt:$startedAt, startedEpoch:$started,
           deadlineAt:$deadlineAt, deadlineEpoch:$deadline},
      queue:[], excluded:[], current:null, items:[], steps:[]}')"
  printf 'state: budget %s items / %s min · review %s · may-close %s · max-decisions %s · base %s\n' \
    "$items" "$minutes" "$review" "$may_close" "$max_decisions" "${base:0:12}"
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
  jq -r '
    (.run) as $r
    | "agent-issue-loop — " + (if .current == null then "no item in progress"
                              else "#\(.current.issue) \(.current.change) · phase \(.current.phase)" end),
      "budget   \($r.items) items / \($r.minutes) min · \($r.items - (.items | length)) left · deadline \($r.deadlineAt) · \((($r.deadlineEpoch - now) / 60 | floor)) min left\(if $r.deadlineEpoch <= now then " · EXPIRED" else "" end)",
      "flags    review \($r.review) · may-close \($r.mayClose) · max-decisions \($r.maxDecisions) · base \($r.base)",
      "progress closed \(.items | map(select(.result == "done")) | length) · skipped \(.items | map(select(.result == "skipped")) | length) · blocked \(.items | map(select(.result == "blocked")) | length) of \($r.items)",
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
    # cannot pay for (measured: 40-65 min for a change, 10-15 min for a skip — REFERENCE.md).
    local minutes_left
    minutes_left=$(jq -r '((.run.deadlineEpoch - now) / 60 | floor)' "$STATE_FILE")
    if [ "$minutes_left" -lt "$FLOOR_MINUTES" ]; then
      reason="only ${minutes_left} min left, below the ${FLOOR_MINUTES} min floor for the next item"
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
  # The exclusion carries the one clause of evidence that produced it (`stale`'s path, or the branch a
  # previous attempt left behind): the run escalates the decisions among the exclusions, and it cannot cite
  # evidence it threw away here.
  excluded=$(printf '%s' "$json" | jq -c --arg queued "$(loop_role queuedVerdict)" '[.[] | select((.verdict // $queued) != $queued) | {issue, class, verdict, reason, evidence}]')
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
      --change) change="${2:-}"; shift 2 ;;
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

cmd_step() {
  require_state
  [ $# -gt 0 ] || loop_die "step: usage: state.sh step <phase> --name <name> --status running|ok|fail|skip [--note TEXT]"
  local phase="$1"; shift
  local name="" status="ok" note=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --name) name="${2:-}"; shift 2 ;;
      --status) status="${2:-}"; shift 2 ;;
      --note) note="${2:-}"; shift 2 ;;
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
  local issue="" result="" verdict="" change="" note="" wall="" pi_exit="" rounds="" gate="" closed="" root_cause="" red_test="" decision_label=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --result) result="${2:-}"; shift 2 ;;
      --verdict) verdict="${2:-}"; shift 2 ;;
      --change) change="${2:-}"; shift 2 ;;
      --note) note="${2:-}"; shift 2 ;;
      --wall) wall="${2:-}"; shift 2 ;;
      --pi-exit) pi_exit="${2:-}"; shift 2 ;;
      --rounds) rounds="${2:-}"; shift 2 ;;
      --gate) gate="${2:-}"; shift 2 ;;
      --closed) closed="${2:-}"; shift 2 ;;
      --root-cause) root_cause="${2:-}"; shift 2 ;;
      --red-test) red_test="${2:-}"; shift 2 ;;
      --decision-label) decision_label="${2:-}"; shift 2 ;;
      --*) loop_die "done: unknown argument $1" ;;
      *) [ -z "$issue" ] || loop_die "done: issue given twice"; issue="$1"; shift ;;
    esac
  done
  case "$issue" in ''|*[!0-9]*) loop_die "done: usage: state.sh done <issue> --result done|skipped|blocked" ;; esac
  case "$result" in done|skipped|blocked) ;; *) loop_die "done: --result must be done|skipped|blocked" ;; esac
  [ -n "$verdict" ] || verdict=$(if [ "$result" = done ]; then printf 'closed'; else printf 'unspecified'; fi)
  # The class travels from the pre-screen into the record, so the report can rank without re-reading
  # the tracker; an item with no queue entry (a resumed run) falls back to the empty string.
  # `--decision-label` is the label the run's escalation *wrote* on the item — not the verdict the worker
  # chose, which is a different fact: a skip verdict of `needs-diagnosis` and a blocked item both end as
  # the decision label. The report names the decisions an item was left with from this field (issue #177).
  local class
  class=$(jq -r --argjson i "$issue" '[.queue[], .excluded[]] | map(select(.issue == $i)) | (.[0].class // "")' "$STATE_FILE")
  patch_state '
    .items += [{issue:$i, class:$c, result:$r, verdict:$v, change:$change, note:$note, at:$at,
                escalatedLabel:$dl,
                metrics:{wallSeconds:$wall, piExit:$piExit, reviewRounds:$rounds, gate:$gate,
                         closed:$closed, rootCause:$rc, redTest:$rt}}]
    | .queue = [.queue[] | select(.issue != $i)]
    | (if .current != null and .current.issue == $i then .current = null else . end)
  ' --argjson i "$issue" --arg c "$class" --arg r "$result" --arg v "$verdict" --arg change "$change" \
    --arg note "$note" --arg at "$(iso_utc "$(now_epoch)")" --arg dl "$decision_label" \
    --argjson wall "$(num_or_null "$wall")" --arg piExit "$pi_exit" --argjson rounds "$(num_or_null "$rounds")" \
    --arg gate "$gate" --arg closed "$closed" --arg rc "$root_cause" --arg rt "$red_test"
  printf 'recorded %s (#%s %s)\n' "$result" "$issue" "$verdict"
}

num_or_null() {
  case "${1:-}" in ''|*[!0-9.]*) printf 'null' ;; *) printf '%s' "$1" ;; esac
}

cmd_report() {
  require_state
  jq -r '
    "closed \(.items | map(select(.result == "done")) | length) · skipped \(.items | map(select(.result == "skipped")) | length) · blocked \(.items | map(select(.result == "blocked")) | length) of \(.run.items) items · budget \(.run.minutes) min",
    "",
    "| issue | class | verdict | change | root cause | red test | gate | review | outcome |",
    "|---|---|---|---|---|---|---|---|---|",
    (if (.items | length) == 0 then "| — | — | — | — | — | — | — | — | nothing processed |"
     else (.items[] | "| #\(.issue) | \(.class // "-") | \(.verdict) | \(.change // "-") | \(.metrics.rootCause // "-") | \(.metrics.redTest // "-") | \(.metrics.gate // "-") | \(.metrics.reviewRounds // "-") | \(.result) |") end),
    "",
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
      --owner) owner="${2:-}"; shift 2 ;;
      *) loop_die "lock: unknown argument $1" ;;
    esac
  done
  mkdir -p "$STATE_DIR"
  if ! mkdir "$LOCK_FILE" 2>/dev/null; then
    local holder
    holder=$(cat "$LOCK_FILE/owner" 2>/dev/null || printf 'an unknown process')
    loop_note "another run holds $LOCK_FILE (owner $holder) — one run per repository"
    exit "$LOOP_REFUSE"
  fi
  printf '%s\n' "$owner" > "$LOCK_FILE/owner"
  printf 'lock held: %s (owner %s)\n' "$LOCK_FILE" "$owner"
}

cmd_unlock() {
  state_resolve
  local owner="${PPID:-unknown}"
  while [ $# -gt 0 ]; do
    case "$1" in
      --owner) owner="${2:-}"; shift 2 ;;
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
  report) cmd_report "$@" ;;
  lock) cmd_lock "$@" ;;
  unlock) cmd_unlock "$@" ;;
  ""|-h|--help) sed -n '2,29p' "$0" ;;
  *) loop_die "unknown command '$cmd'" ;;
esac
