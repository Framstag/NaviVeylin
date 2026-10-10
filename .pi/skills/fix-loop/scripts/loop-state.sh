#!/usr/bin/env bash
# loop-state.sh — budget and progress bookkeeping for the bugfix-loop skill.
#
# The loop's limits (bug cap, wall-clock deadline) and its progress have to be mechanical, not remembered:
# this script is the only writer of the run state and exits 3 whenever the run has nothing left to spend,
# so a caller stops on an exit code instead of on its own arithmetic.
#
# State lives in .pi/bugfix-loop/run.json — machine-local, gitignored (.pi/*), never committed.
# Override with LOOP_STATE for the self-test.
#
# Usage:
#   loop-state.sh init --bugs N --minutes M [--review on|off] [--allow-todo-removal yes|no]
#   loop-state.sh show               # which issue, which phase, the result of every step so far
#   loop-state.sh remaining          # exit 3 when bugs == 0 or the deadline has passed
#   loop-state.sh triage <§id> ...   # store the pre-screened survivor queue (§ optional in the argument)
#   loop-state.sh next               # print the next candidate; exit 3 when the queue or the budget is spent
#   loop-state.sh start <change> [--id §N]
#                                    # begin one iteration (phase A) on the named change and TODO id
#   loop-state.sh step <phase> --name <step> --status running|ok|fail|skip [--note TEXT]
#                                    # record one phase boundary of the current iteration
#   loop-state.sh done [<change>] [--id §N] --result done|skipped|blocked
#                     [--verdict <class>] [--note TEXT]
#                                    # a pre-change skip has no change name: use --id
#   loop-state.sh report             # per-iteration table plus every iteration's recorded steps
#   loop-state.sh verdicts           # histogram of skip verdicts (what the backlog turned out to be)
#
# <phase> is one of: prescreen A B C D E F
# --verdict class (free text, these are the ones the gate produces):
#   closed · stale · needs-diagnosis · needs-decision · unspecifiable · too-wide · needs-device · refuted · blocked
set -euo pipefail

STATE="${LOOP_STATE:-$(git rev-parse --show-toplevel)/.pi/bugfix-loop/run.json}"

need_jq() { command -v jq >/dev/null || { echo "loop-state: jq not found" >&2; exit 2; }; }
now() { date +%s; }
iso() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }
norm_id() { printf '%s' "${1#§}"; }
dash_if_empty() { [ -n "$1" ] && printf '%s' "$1" || printf '%s' "-"; }

write() { # write <json-on-stdin>
  mkdir -p "$(dirname "$STATE")"
  local tmp; tmp="$(mktemp)"
  cat > "$tmp"
  mv "$tmp" "$STATE"
}

cmd_init() {
  local bugs="" minutes="" review="on" removal="no"
  while [ $# -gt 0 ]; do
    case "$1" in
      --bugs) bugs="${2:?}"; shift 2;;
      --minutes) minutes="${2:?}"; shift 2;;
      --review) review="${2:?}"; shift 2;;
      --allow-todo-removal) removal="${2:?}"; shift 2;;
      *) echo "init: unknown arg $1" >&2; exit 2;;
    esac
  done
  [ -n "$bugs" ] && [ -n "$minutes" ] || { echo "init: --bugs and --minutes are mandatory" >&2; exit 2; }
  case "$bugs" in ''|*[!0-9]*) echo "init: --bugs must be a positive integer" >&2; exit 2;; esac
  case "$minutes" in ''|*[!0-9]*) echo "init: --minutes must be a positive integer" >&2; exit 2;; esac
  [ "$bugs" -gt 0 ] && [ "$minutes" -gt 0 ] || { echo "init: budget must be > 0" >&2; exit 2; }

  local started deadline
  started="$(now)"
  deadline=$(( started + minutes * 60 ))
  jq -n --arg iso "$(iso "$started")" --arg dl "$(iso "$deadline")" \
        --argjson started "$started" --argjson deadline "$deadline" \
        --argjson bugs "$bugs" --argjson minutes "$minutes" \
        --arg review "$review" --arg removal "$removal" \
        '{startedAt:$iso, startedEpoch:$started, deadlineAt:$dl, deadlineEpoch:$deadline,
          bugLimit:$bugs, minutesBudget:$minutes, review:$review, allowTodoRemoval:$removal,
          completed:[], skipped:[], blocked:[], queue:[], current:null, currentId:null,
          currentPhase:null, steps:[]}' | write
  echo "bugfix-loop: budget ${bugs} bugs / ${minutes} min, deadline $(iso "$deadline")"
}

require_state() { [ -f "$STATE" ] || { echo "loop-state: no run state at $STATE — run 'init' first" >&2; exit 2; }; }

cmd_show() { require_state; jq -r '
  ((.deadlineEpoch - now) / 60 | floor) as $minLeft
  | "bugfix-loop — " + (if .current == null
                      then "no iteration in progress"
                           + ((.completed[-1] // .blocked[-1]) as $l
                              | if $l then " · last: §\($l.id) \($l.change) (\($l.verdict))" else "" end)
                      else "§\(.currentId // "?") \(.current) · phase \(.currentPhase)" end),
  "budget   \(.bugLimit) bugs / \(.minutesBudget) min · \(.bugLimit - (.completed | length)) left · deadline \(.deadlineAt) · \((if .deadlineEpoch > now then "" else "EXPIRED · " end))\($minLeft) min left",
  "review   \(.review) · todo-removal \(.allowTodoRemoval)",
  "progress closed \(.completed | length) · skipped \(.skipped | length) · blocked \(.blocked | length) of \(.bugLimit)",
  "queue    \(.queue | length): \([.queue[]?.id] | join(" "))",
  "",
  "steps",
  ( [ .steps[] | select(.change == ($c // "") and .id == ($i // "")) ]
    | if length == 0 then "  (none yet)"
      else .[] | "  \(.phase)  \(.name) · \(.status) · \(if .note == "" then "-" else .note end)" end )
' --arg c "$(jq -r 'if .current != null then .current else (.completed[-1].change // .blocked[-1].change // "") end' "$STATE")" \
  --arg i "$(jq -r 'if .current != null then (.currentId // "") else (.completed[-1].id // .blocked[-1].id // "") end' "$STATE")" "$STATE"; }

cmd_remaining() {
  require_state
  local left deadline
  left=$(jq -r '.bugLimit - (.completed | length)' "$STATE")
  deadline=$(jq -r '.deadlineEpoch' "$STATE")
  if [ "$left" -le 0 ]; then echo "0 bugs left (cap reached)"; exit 3; fi
  if [ "$(now)" -ge "$deadline" ]; then echo "deadline passed at $(iso "$deadline")"; exit 3; fi
  echo "$left bugs left, $(( (deadline - $(now)) / 60 )) min left (deadline $(iso "$deadline"))"
}

cmd_triage() { require_state; [ $# -gt 0 ] || { echo "triage: need at least one id" >&2; exit 2; }
  local json='[]'
  for id in "$@"; do json=$(jq --arg id "$(norm_id "$id")" '. + [{id:$id}]' <<<"$json"); done
  jq --argjson q "$json" '.queue = $q' "$STATE" | write
  echo "queue: $(jq -r '[.queue[].id] | join(" ")' "$STATE")"
}

cmd_next() {
  require_state
  local left; left=$(jq -r '.bugLimit - (.completed | length)' "$STATE")
  [ "$left" -gt 0 ] || { echo "cap reached"; exit 3; }
  [ "$(now)" -lt "$(jq -r '.deadlineEpoch' "$STATE")" ] || { echo "deadline passed"; exit 3; }
  local id; id=$(jq -r '.queue[0].id // empty' "$STATE")
  [ -n "$id" ] || { echo "queue empty"; exit 3; }
  echo "$id"
}

cmd_start() {
  require_state
  local c="" id=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --id) id="$(norm_id "${2:?}")"; shift 2;;
      --*) echo "start: unknown flag $1" >&2; exit 2;;
      *) [ -z "$c" ] || { echo "start: change given twice" >&2; exit 2; }; c="$1"; shift;;
    esac
  done
  [ -n "$c" ] || { echo "start: usage: start <change> [--id §N]" >&2; exit 2; }
  jq --arg c "$c" --arg i "$id" --arg at "$(iso "$(now)")" '
    .current = $c | .currentId = ($i | select(. != "") // null) | .currentPhase = "A"
    | .steps += [ {id:$i, change:$c, phase:"A", name:"start", status:"ok", note:"", at:$at} ]
  ' "$STATE" | write
  echo "started §${id:-?} $c — phase A"
}

cmd_step() {
  require_state
  [ $# -gt 0 ] || { echo "step: usage: step <phase> --name <step> --status running|ok|fail|skip [--note TEXT]" >&2; exit 2; }
  local phase="$1"; shift
  local name="" status="ok" note=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --name) name="${2:?}"; shift 2;;
      --status) status="${2:?}"; shift 2;;
      --note) note="${2:?}"; shift 2;;
      *) echo "step: unknown arg $1" >&2; exit 2;;
    esac
  done
  case "$phase" in prescreen|A|B|C|D|E|F) ;; *) echo "step: phase must be prescreen|A|B|C|D|E|F" >&2; exit 2;; esac
  [ -n "$name" ] || { echo "step: --name is mandatory" >&2; exit 2; }
  case "$status" in running|ok|fail|skip) ;; *) echo "step: --status must be running|ok|fail|skip" >&2; exit 2;; esac
  jq --arg p "$phase" --arg n "$name" --arg s "$status" --arg note "$note" --arg at "$(iso "$(now)")" '
    .currentPhase = $p
    | .steps += [ {id:(.currentId // ""), change:(.current // ""), phase:$p, name:$n,
                    status:$s, note:$note, at:$at} ]
  ' "$STATE" | write
  echo "§$(dash_if_empty "$(jq -r '.currentId // ""' "$STATE")") $phase/$name: $status — $(dash_if_empty "$note")"
}

cmd_done() {
  require_state
  local change="" id="" result="" verdict="" note=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --id) id="$(norm_id "${2:?}")"; shift 2;;
      --result) result="${2:?}"; shift 2;;
      --verdict) verdict="${2:?}"; shift 2;;
      --note) note="${2:?}"; shift 2;;
      --*) echo "done: unknown flag $1" >&2; exit 2;;
      *) [ -z "$change" ] || { echo "done: change given twice" >&2; exit 2; }; change="$1"; shift;;
    esac
  done
  [ -n "$change$id" ] || { echo "done: need a change name or --id §N" >&2; exit 2; }
  case "$result" in done|skipped|blocked) ;; *) echo "done: --result must be done|skipped|blocked" >&2; exit 2;; esac
  if [ -z "$verdict" ]; then
    case "$result" in done) verdict="closed";; *) verdict="unspecified";; esac
  fi
  local entry
  entry=$(jq -n --arg c "$change" --arg i "$id" --arg v "$verdict" --arg n "$note" --arg at "$(iso "$(now)")" \
                '{change:$c, id:$i, verdict:$v, note:$n, at:$at}')
  jq --argjson e "$entry" --arg r "$result" --arg c "$change" --arg i "$id" '
    (if $c != "" and .current == $c
       then .current = null | .currentId = null | .currentPhase = null
       else . end)
    | (if $r == "done" then .completed += [$e] elif $r == "skipped" then .skipped += [$e] else .blocked += [$e] end)
    | (if $e.id != "" then .queue = [ .queue[] | select(.id != $e.id) ] else . end)
  ' "$STATE" | write
  echo "recorded $result ($verdict): ${change:-§$id}"
  # Do not call cmd_remaining here: its `exit 3` would exit this shell, not return a status.
  echo "budget: $(jq -r '.bugLimit - (.completed | length)' "$STATE") bugs left"
}

cmd_report() {
  require_state
  jq -r '
    "closed \(.completed | length) · skipped \(.skipped | length) · blocked \(.blocked | length) of \(.bugLimit) bugs · budget \(.minutesBudget) min · deadline \(.deadlineAt)",
    "",
    ( .completed[] as $e
      | ("closed   §\($e.id)\t\($e.change)\t\($e.at)"),
        (.steps | map(select(.change == $e.change)) | .[]?
          | "    \(.phase)/\(.name) · \(.status) · \(if .note == "" then "-" else .note end)") ),
    ( .skipped[]   | "skipped  §\(.id)\t\(.verdict)\t\(.note)\t\(.at)" ),
    ( .blocked[]   | "blocked  §\(.id)\t\(.change)\t\(.verdict)\t\(.note)\t\(.at)" )
  ' "$STATE"
}

cmd_verdicts() {
  require_state
  jq -r '[.skipped[]?.verdict, .blocked[]?.verdict] | map(select(. != "" and . != null)) | group_by(.) | map("\(length)\t\(.[0])") | .[]' "$STATE" | sort -rn
}

need_jq
cmd="${1:-}"; shift || true
case "$cmd" in
  init|show|status|remaining|start|step|done|triage|next|report|verdicts) "cmd_${cmd/status/show}" "$@";;
  ""|-h|--help|help) awk 'NR>1 && /^set -euo/ {exit} NR>1 {sub(/^# ?/,""); print}' "$0";;
  *) echo "loop-state: unknown command '$cmd'" >&2; exit 2;;
esac
