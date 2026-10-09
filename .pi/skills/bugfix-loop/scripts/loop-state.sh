#!/usr/bin/env bash
# loop-state.sh — budget bookkeeping for the bugfix-loop skill.
#
# The loop's limits (bug cap, wall-clock deadline) and its progress have to be
# mechanical, not remembered: this script is the only writer of the run state and
# exits 3 whenever the run has nothing left to spend, so a caller stops on an exit
# code instead of on its own arithmetic.
#
# State lives in .pi/bugfix-loop/run.json — machine-local, gitignored (.pi/*),
# never committed. Override with LOOP_STATE for the self-test.
#
# Usage:
#   loop-state.sh init --bugs N --minutes M [--review on|off] [--allow-todo-removal yes|no]
#   loop-state.sh show
#   loop-state.sh remaining          # exit 3 when bugs == 0 or deadline passed
#   loop-state.sh start <change>
#   loop-state.sh done <change> --result done|skipped|blocked [--todo §N] [--note TEXT]
#   loop-state.sh triage <candidate...>   # store the ranked candidate queue
#   loop-state.sh next               # exit 3 when the queue is empty or the limit is spent
set -euo pipefail

STATE="${LOOP_STATE:-$(git rev-parse --show-toplevel)/.pi/bugfix-loop/run.json}"

need_jq() { command -v jq >/dev/null || { echo "loop-state: jq not found" >&2; exit 2; }; }
now() { date +%s; }
iso() { date -u -d "@$1" +%Y-%m-%dT%H:%M:%SZ; }

write() { # write <json-on-stdin>
  mkdir -p "$(dirname "$STATE")"
  local tmp; tmp="$(mktemp)"
  cat > "$tmp"
  mv "$tmp" "$STATE"
}

read_state() { jq . "$STATE"; }

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
          completed:[], skipped:[], blocked:[], queue:[], current:null}' | write
  echo "bugfix-loop: budget ${bugs} bugs / ${minutes} min, deadline $(iso "$deadline")"
}

require_state() { [ -f "$STATE" ] || { echo "loop-state: no run state at $STATE — run 'init' first" >&2; exit 2; }; }

cmd_show() { require_state; jq -r '
  "budget   \(.bugLimit) bugs / \(.minutesBudget) min · deadline \(.deadlineAt) \((if (.deadlineEpoch > now) then "ok" else "EXPIRED" end))",
  "review   \(.review) · todo-removal \(.allowTodoRemoval)",
  "done     \(.completed | length) · skipped \(.skipped | length) · blocked \(.blocked | length)",
  "current  \(.current // "-")",
  "queue    \((.queue | length)): \([.queue[]?.id] | join(" "))"
' "$STATE"
}

cmd_remaining() {
  require_state
  local left deadline
  left=$(jq -r '.bugLimit - (.completed | length)' "$STATE")
  deadline=$(jq -r '.deadlineEpoch' "$STATE")
  if [ "$left" -le 0 ]; then echo "0 bugs left (cap reached)"; exit 3; fi
  if [ "$(now)" -ge "$deadline" ]; then echo "deadline passed at $(iso "$deadline")"; exit 3; fi
  echo "$left bugs left, $(( (deadline - $(now)) / 60 )) min left (deadline $(iso "$deadline"))"
}

cmd_start() { require_state; local c="${1:?usage: start <change>}"
  jq --arg c "$c" '.current = $c' "$STATE" | write; echo "current: $c"; }

cmd_done() { require_state; local c="${1:?usage: done <change> --result ...}"; shift
  local result="" todo="" note=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --result) result="${2:?}"; shift 2;;
      --todo) todo="${2:?}"; shift 2;;
      --note) note="${2:?}"; shift 2;;
      *) echo "done: unknown arg $1" >&2; exit 2;;
    esac
  done
  case "$result" in done|skipped|blocked) ;; *) echo "done: --result must be done|skipped|blocked" >&2; exit 2;; esac
  local entry
  entry=$(jq -n --arg c "$c" --arg t "$todo" --arg n "$note" --arg at "$(iso "$(now)")" \
                '{change:$c, todo:$t, note:$n, at:$at}')
  jq --argjson e "$entry" --arg r "$result" --arg c "$c" '
    if .current == $c then .current = null else . end
    | if $r == "done" then .completed += [$e] elif $r == "skipped" then .skipped += [$e] else .blocked += [$e] end
    | .queue = [ .queue[] | select(.id != ($e.todo | sub("^§";""))) ]
  ' "$STATE" | write
  echo "recorded $result: $c"
  # Do not call cmd_remaining here: its `exit 3` would exit this shell, not return a status.
  echo "budget: $(jq -r '.bugLimit - (.completed | length)' "$STATE") bugs left"
}

cmd_triage() { require_state; [ $# -gt 0 ] || { echo "triage: need at least one id" >&2; exit 2; }
  local json='[]'
  for id in "$@"; do json=$(jq --arg id "${id#§}" '. + [{id:$id}]' <<<"$json"); done
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

need_jq
cmd="${1:-}"; shift || true
case "$cmd" in
  init|show|remaining|start|done|triage|next) "cmd_$cmd" "$@";;
  ""|-h|--help|help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//';; 
  *) echo "loop-state: unknown command '$cmd'" >&2; exit 2;;
esac
